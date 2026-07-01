package com.diabetes.plan.service;

import com.diabetes.common.client.UserMessageClientHelper;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.dify.DifyJsonSchema;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.plan.dify.DifyPlanLlmOutputAssembler;
import com.diabetes.plan.dify.DifyPlanWorkflowContract;
import com.diabetes.plan.entity.HealthPlan;
import com.diabetes.plan.mapper.HealthPlanMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 健康方案生成服务：本地热量计算 + Dify 工作流生成四大维度方案。
 *
 * @see PlanPromptBuilder Dify 入参/出参 JSON 契约
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private static final int MAX_DIFY_RETRIES = 2;

    private final HealthPlanMapper healthPlanMapper;
    private final PlanPersistenceService planPersistenceService;
    private final UserProfileService userProfileService;
    private final CalorieCalculator calorieCalculator;
    private final PlanPromptBuilder planPromptBuilder;
    private final DifyClient difyClient;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;
    private final String difyApiKey;
    private final String difyBaseUrl;
    private final String difyResponseMode;
    private final String difyInternalKey;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PlanService(HealthPlanMapper healthPlanMapper,
                       PlanPersistenceService planPersistenceService,
                       UserProfileService userProfileService,
                       CalorieCalculator calorieCalculator,
                       PlanPromptBuilder planPromptBuilder,
                       DifyClient difyClient,
                       UserServiceClient userServiceClient,
                       ObjectMapper objectMapper,
                       @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
                       @Value("${dify.workflows.plan-generation.api-key:}") String difyApiKey,
                       @Value("${dify.workflows.plan-generation.response-mode:blocking}") String difyResponseMode,
                       @Value("${dify-internal.key:}") String difyInternalKey) {
        this.healthPlanMapper = healthPlanMapper;
        this.planPersistenceService = planPersistenceService;
        this.userProfileService = userProfileService;
        this.calorieCalculator = calorieCalculator;
        this.planPromptBuilder = planPromptBuilder;
        this.difyClient = difyClient;
        this.userServiceClient = userServiceClient;
        this.objectMapper = objectMapper;
        this.difyBaseUrl = difyBaseUrl;
        this.difyApiKey = difyApiKey;
        this.difyResponseMode = difyResponseMode == null ? "blocking" : difyResponseMode.trim();
        this.difyInternalKey = difyInternalKey;
    }

    public SseEmitter generatePlanStream(String userId, Map<String, Object> profile) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.warn("SSE 连接异常 userId={}: {}", userId, ex.getMessage()));
        executor.execute(() -> {
            try {
                int dailyCalories = calorieCalculator.calculateDailyCalories(profile);
                emitter.send(SseEmitter.event().name("stage_calorie")
                        .data(Map.of("stage", "calorie", "dailyCalories", dailyCalories)));

                Map<String, Object> planContent = generatePlanContent(userId, profile, dailyCalories);

                emitter.send(SseEmitter.event().name("stage_diet")
                        .data(Map.of("stage", "diet", "content", planContent.getOrDefault("dietPlan", Map.of()))));
                emitter.send(SseEmitter.event().name("stage_exercise")
                        .data(Map.of("stage", "exercise", "content", planContent.getOrDefault("exercisePlan", Map.of()))));
                emitter.send(SseEmitter.event().name("stage_rest")
                        .data(Map.of("stage", "rest", "content", planContent.getOrDefault("restPlan", Map.of()))));
                emitter.send(SseEmitter.event().name("stage_medication")
                        .data(Map.of("stage", "medication",
                                "content", planContent.getOrDefault("medicationNote", ""))));

                HealthPlan plan = planPersistenceService.savePlan(userId, profile, dailyCalories, planContent);
                UserMessageClientHelper.notifyPlanCompleted(userServiceClient, difyInternalKey,
                        userId, plan.getPlanId());
                emitter.send(SseEmitter.event().name("complete")
                        .data(Map.of("planId", plan.getPlanId(), "version", plan.getVersion())));
                emitter.complete();
            } catch (Exception e) {
                log.error("方案生成失败 userId={}: {}", userId, e.getMessage(), e);
                UserMessageClientHelper.notifyPlanFailed(userServiceClient, difyInternalKey, userId,
                        e.getMessage() == null ? "方案生成失败" : e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", e.getMessage() == null ? "方案生成失败" : e.getMessage())));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private Map<String, Object> generatePlanContent(String userId, Map<String, Object> profile, int dailyCalories) {
        Map<String, Object> defaultPlan = defaultPlanContent(dailyCalories);
        if (difyApiKey == null || difyApiKey.isBlank()) {
            log.warn("未配置 DIFY_PLAN_API_KEY，使用本地默认方案模板");
            return defaultPlan;
        }

        Map<String, Object> payload = planPromptBuilder.buildDifyPayload(userId, profile, dailyCalories);
        Exception lastError = null;
        for (int attempt = 0; attempt <= MAX_DIFY_RETRIES; attempt++) {
            try {
                JsonNode response = callDifyWorkflow(userId, payload);
                assertWorkflowSucceeded(response);
                Map<String, Object> parsed = parseDifyPlanResponse(response, defaultPlan);
                if (parsed != null && hasRequiredPlanSections(parsed)) {
                    parsed.put("aiRaw", response.toString());
                    return parsed;
                }
                String preview = response.toString();
                if (preview.length() > 800) {
                    preview = preview.substring(0, 800) + "...";
                }
                lastError = new BusinessException(502,
                        "Dify 工作流响应缺少 " + DifyPlanWorkflowContract.LLM_OUTPUT_KEY
                                + "（或兼容的 health_plan）或必填字段，请检查工作流结束节点。原始响应: "
                                + preview);
            } catch (Exception e) {
                lastError = e;
                log.warn("Dify 方案生成第 {} 次失败: {}", attempt + 1, e.getMessage());
            }
        }

        if (lastError instanceof BusinessException be) {
            throw be;
        }
        throw new BusinessException(502, "Dify 方案生成失败: " + lastError.getMessage());
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyPlanWorkflowContract.workflowSpec(
                difyBaseUrl, difyApiKey, difyResponseMode);
    }

    private void assertWorkflowSucceeded(JsonNode response) {
        String status = response.path("data").path("status").asText(null);
        if (status == null || status.isBlank()) {
            status = response.path("status").asText(null);
        }
        if (status != null && !status.isBlank() && !"succeeded".equalsIgnoreCase(status)) {
            String error = response.path("data").path("error").asText(
                    response.path("error").asText("工作流执行失败"));
            throw new BusinessException(502, "Dify 工作流状态=" + status + ": " + error);
        }
    }

    private boolean hasRequiredPlanSections(Map<String, Object> parsed) {
        return parsed.containsKey("dietPlan") && parsed.containsKey("exercisePlan") && parsed.containsKey("restPlan");
    }

    private JsonNode callDifyWorkflow(String userId, Map<String, Object> payload) {
        Map<String, Object> inputs = buildWorkflowInputs(payload);
        log.info("发起 Dify 方案生成工作流 userId={}", userId);
        return difyClient.runWorkflowBlocking(difyApiKey, userId, inputs, difyResponseMode);
    }

    /**
     * 构建 Dify Workflow API 的 inputs（7 个开始节点变量平铺传入）。
     * 请求体：{@code { "inputs": { query, user_id, daily_calories, user_profile, ... } }}
     */
    private Map<String, Object> buildWorkflowInputs(Map<String, Object> payload) {
        return DifyJsonSchema.flatWorkflowInputs(payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDifyPlanResponse(JsonNode response, Map<String, Object> fallback) {
        JsonNode llmNode = extractLlmOutput(response);
        if (!llmNode.isMissingNode() && DifyPlanLlmOutputAssembler.isShallowLlmOutput(llmNode)) {
            Map<String, Object> assembled = DifyPlanLlmOutputAssembler.assembleToPlanContent(llmNode);
            if (DifyPlanLlmOutputAssembler.hasRequiredSections(assembled)) {
                return assembled;
            }
            return null;
        }

        JsonNode planNode = extractDeepHealthPlan(response);
        if (planNode.isMissingNode()) {
            return null;
        }

        Map<String, Object> result = new HashMap<>(fallback);
        if (planNode.has("diet_plan")) {
            result.put("dietPlan", objectMapper.convertValue(planNode.get("diet_plan"), Map.class));
        } else if (planNode.has("dietPlan")) {
            result.put("dietPlan", objectMapper.convertValue(planNode.get("dietPlan"), Map.class));
        }
        if (planNode.has("exercise_plan")) {
            result.put("exercisePlan", objectMapper.convertValue(planNode.get("exercise_plan"), Map.class));
        } else if (planNode.has("exercisePlan")) {
            result.put("exercisePlan", objectMapper.convertValue(planNode.get("exercisePlan"), Map.class));
        }
        if (planNode.has("rest_plan")) {
            result.put("restPlan", objectMapper.convertValue(planNode.get("rest_plan"), Map.class));
        } else if (planNode.has("restPlan")) {
            result.put("restPlan", objectMapper.convertValue(planNode.get("restPlan"), Map.class));
        }
        if (planNode.has("medication_note")) {
            result.put("medicationNote", planNode.get("medication_note").asText());
        } else if (planNode.has("medicationNote")) {
            result.put("medicationNote", planNode.get("medicationNote").asText());
        }
        if (planNode.has("summary")) {
            result.put("summary", planNode.get("summary").asText());
        }
        return result;
    }

    /** 提取浅层 LLM Structured Output（plan_llm_output 或 outputs 根上的浅层字段） */
    private JsonNode extractLlmOutput(JsonNode difyResponse) {
        JsonNode node = difyResponse.path(DifyPlanWorkflowContract.LLM_OUTPUT_KEY);
        if (!node.isMissingNode()) {
            return node;
        }

        node = difyResponse.path("data").path("outputs").path(DifyPlanWorkflowContract.LLM_OUTPUT_KEY);
        if (!node.isMissingNode()) {
            return node;
        }

        node = difyResponse.path("outputs").path(DifyPlanWorkflowContract.LLM_OUTPUT_KEY);
        if (!node.isMissingNode()) {
            return node;
        }

        JsonNode outputs = difyResponse.path("data").path("outputs");
        if (outputs.isMissingNode()) {
            outputs = difyResponse.path("outputs");
        }
        if (DifyPlanLlmOutputAssembler.isShallowLlmOutput(outputs)) {
            return outputs;
        }

        JsonNode legacy = extractDeepHealthPlan(difyResponse);
        if (!legacy.isMissingNode() && DifyPlanLlmOutputAssembler.isShallowLlmOutput(legacy)) {
            return legacy;
        }

        return objectMapper.missingNode();
    }

    /** 兼容旧工作流嵌套 health_plan 输出 */
    private JsonNode extractDeepHealthPlan(JsonNode difyResponse) {
        JsonNode plan = difyResponse.path(DifyPlanWorkflowContract.OUTPUT_KEY);
        if (!plan.isMissingNode()) return plan;

        plan = difyResponse.path("data").path("outputs").path(DifyPlanWorkflowContract.OUTPUT_KEY);
        if (!plan.isMissingNode()) return plan;

        plan = difyResponse.path("outputs").path(DifyPlanWorkflowContract.OUTPUT_KEY);
        if (!plan.isMissingNode()) return plan;

        JsonNode outputs = difyResponse.path("data").path("outputs");
        if (outputs.isMissingNode()) outputs = difyResponse.path("outputs");
        if (!outputs.isMissingNode() && (outputs.has("diet_plan") || outputs.has("dietPlan"))) {
            return outputs;
        }

        JsonNode text = difyResponse.path("data").path("outputs").path("text");
        if (text.isMissingNode()) text = difyResponse.path("outputs").path("text");
        if (text.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(text.asText());
                JsonNode fromText = parsed.path(DifyPlanWorkflowContract.LLM_OUTPUT_KEY);
                if (!fromText.isMissingNode()) return fromText;
                fromText = parsed.path(DifyPlanWorkflowContract.OUTPUT_KEY);
                if (!fromText.isMissingNode()) return fromText;
                if (parsed.has("diet_plan") || parsed.has("dietPlan")) return parsed;
                if (DifyPlanLlmOutputAssembler.isShallowLlmOutput(parsed)) return parsed;
            } catch (Exception e) {
                log.debug("无法从 Dify text 输出解析方案: {}", e.getMessage());
            }
        }
        return objectMapper.missingNode();
    }

    @Deprecated
    private JsonNode extractHealthPlan(JsonNode difyResponse) {
        return extractDeepHealthPlan(difyResponse);
    }

    public Map<String, Object> getLatest(String userId) {
        HealthPlan plan = healthPlanMapper.findLatestByUserId(userId);
        if (plan == null) {
            return null;
        }
        return toDetail(plan);
    }

    public Map<String, Object> getHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<HealthPlan> plans = healthPlanMapper.findHistory(userId, offset, size);
        int total = healthPlanMapper.countByUserId(userId);
        List<Map<String, Object>> list = plans.stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("planId", p.getPlanId());
                    m.put("version", p.getVersion());
                    m.put("dailyCalories", p.getDailyCalories());
                    m.put("generatedAt", p.getGeneratedAt());
                    m.put("isFavorite", p.getIsFavorite());
                    return m;
                })
                .toList();
        return Map.of("plans", list, "total", total);
    }

    public Map<String, Object> getDetail(String planId) {
        HealthPlan plan = healthPlanMapper.findById(planId);
        if (plan == null) {
            return null;
        }
        return toDetail(plan);
    }

    public Map<String, Object> toggleFavorite(String userId, String planId) {
        HealthPlan plan = healthPlanMapper.findById(planId);
        if (plan == null) {
            throw new BusinessException(404, "方案不存在");
        }
        if (plan.getUserId() != null && !plan.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作此方案");
        }
        int next = plan.getIsFavorite() != null && plan.getIsFavorite() == 1 ? 0 : 1;
        healthPlanMapper.updateFavorite(planId, next);
        return Map.of("favorited", next == 1);
    }

    private Map<String, Object> toDetail(HealthPlan plan) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("planId", plan.getPlanId());
        detail.put("title", plan.getTitle());
        detail.put("summary", plan.getSummary());
        detail.put("dailyCalories", plan.getDailyCalories());
        detail.put("medicationNote", plan.getMedicationNote());
        detail.put("version", plan.getVersion());
        detail.put("isFavorite", plan.getIsFavorite());
        detail.put("generatedAt", plan.getGeneratedAt());
        try {
            if (plan.getAiRawResponse() != null) {
                Map<String, Object> raw = objectMapper.readValue(plan.getAiRawResponse(), Map.class);
                detail.put("dietPlan", raw.getOrDefault("dietPlan", Map.of()));
                detail.put("exercisePlan", raw.getOrDefault("exercisePlan", Map.of()));
                detail.put("restPlan", raw.getOrDefault("restPlan", Map.of()));
                if (raw.containsKey("summary")) {
                    detail.put("summary", raw.get("summary"));
                }
            }
        } catch (IOException ignored) {
            detail.put("dietPlan", Map.of());
            detail.put("exercisePlan", Map.of());
            detail.put("restPlan", Map.of());
        }
        return detail;
    }

    private Map<String, Object> defaultPlanContent(int dailyCalories) {
        Map<String, Object> breakfast = Map.of(
                "time", "07:00-08:00",
                "foods", List.of(
                        Map.of("name", "全麦面包", "amount", "2片", "calories", 160, "gi_level", "low"),
                        Map.of("name", "水煮蛋", "amount", "1个", "calories", 70, "gi_level", "low"),
                        Map.of("name", "无糖豆浆", "amount", "250ml", "calories", 80, "gi_level", "low")
                ),
                "total_calories", 310
        );
        Map<String, Object> lunch = Map.of(
                "time", "12:00-13:00",
                "foods", List.of(
                        Map.of("name", "糙米饭", "amount", "150g", "calories", 210, "gi_level", "medium"),
                        Map.of("name", "清蒸鱼", "amount", "100g", "calories", 120, "gi_level", "low"),
                        Map.of("name", "炒青菜", "amount", "200g", "calories", 60, "gi_level", "low")
                ),
                "total_calories", 390
        );
        Map<String, Object> dinner = Map.of(
                "time", "18:00-19:00",
                "foods", List.of(
                        Map.of("name", "杂粮饭", "amount", "100g", "calories", 140, "gi_level", "medium"),
                        Map.of("name", "鸡胸肉", "amount", "80g", "calories", 100, "gi_level", "low"),
                        Map.of("name", "凉拌黄瓜", "amount", "150g", "calories", 30, "gi_level", "low")
                ),
                "total_calories", 270
        );
        Map<String, Object> snack = Map.of(
                "time", "15:00-16:00",
                "foods", List.of(
                        Map.of("name", "坚果", "amount", "20g", "calories", 100, "gi_level", "low"),
                        Map.of("name", "无糖酸奶", "amount", "100g", "calories", 60, "gi_level", "low")
                ),
                "total_calories", 160
        );

        Map<String, Object> diet = new LinkedHashMap<>();
        diet.put("meal_plan", Map.of("breakfast", breakfast, "lunch", lunch, "dinner", dinner, "snack", snack));
        diet.put("diet_principles", List.of(
                "控制总热量摄入，每日约" + dailyCalories + "kcal",
                "选择低GI食物，避免精制糖和高升糖指数食物",
                "三餐定时定量，可适当加餐",
                "请在医生指导下执行"
        ));
        diet.put("foods_to_avoid", List.of("含糖饮料", "糕点甜点", "油炸食品", "白米饭/白面包"));
        diet.put("foods_to_recommend", List.of("全谷物", "绿叶蔬菜", "优质蛋白(鱼/鸡/豆)", "低糖水果"));

        Map<String, Object> exercise = new LinkedHashMap<>();
        exercise.put("weekly_goal", "每周至少150分钟中等强度有氧运动");
        exercise.put("items", List.of(
                Map.of("type", "快走", "duration", "30分钟", "frequency", "每日", "intensity", "中等", "calories_burned", 150, "caution", "餐后1小时进行"),
                Map.of("type", "力量训练", "duration", "20分钟", "frequency", "每周3次", "intensity", "轻度", "calories_burned", 100, "caution", "避免空腹运动")
        ));

        Map<String, Object> rest = new LinkedHashMap<>();
        rest.put("wake_up", "06:30");
        rest.put("sleep", "22:30");
        rest.put("nap", "午休20-30分钟");
        rest.put("glucose_monitor_times", List.of("空腹", "早餐后2h", "晚餐前", "睡前"));
        rest.put("routine_tips", List.of("固定作息时间，避免熬夜", "请在医生指导下执行"));

        Map<String, Object> result = new HashMap<>();
        result.put("dietPlan", diet);
        result.put("exercisePlan", exercise);
        result.put("restPlan", rest);
        result.put("medicationNote", "请遵医嘱按时用药，注意监测低血糖症状。请在医生指导下执行。");
        result.put("summary", "基于本地规则生成的通用健康管理方案（Dify 未配置或调用失败时的降级结果）");
        return result;
    }
}
