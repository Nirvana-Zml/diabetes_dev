package com.diabetes.health.service;

import com.diabetes.common.client.UserMessageClientHelper;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.IdGenerator;
import com.diabetes.common.dify.DifyJsonSchema;
import com.diabetes.health.dify.DifyRiskAssessmentWorkflowContract;
import com.diabetes.health.dto.RiskAssessRequest;
import com.diabetes.health.entity.*;
import com.diabetes.health.mapper.RiskAssessmentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/**
 * Dify 风险评估工作流期望的响应体（Webhook / Workflow 输出节点）：
 * 
 * <pre>
 * {
 *   "risk_assessment": {
 *     "risk_score": 62,
 *     "risk_level": "low|medium|high",
 *     "confidence": "low|medium|high",
 *     "analysis": "报告摘要文本",
 *     "glucose_level": "normal|prediabetes|diabetes",
 *     "risk_factors": [
 *       {"factor_code":"bmi","name":"BMI超标","weight":20,"factor_level":2,"description":"..."}
 *     ],
 *     "suggestions": [
 *       {"category":1,"priority":1,"content":"..."},
 *       "也可为纯字符串，将使用默认 category=3 priority=2"
 *     ]
 *   }
 * }
 * </pre>
 * 
 * 也支持嵌套在 data.outputs.risk_assessment 下（标准 Workflow Run 响应）。
 */
@Service
public class RiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final HealthRecordService healthRecordService;
    private final RiskAssessmentMapper riskAssessmentMapper;
    private final MedicalCalculator medicalCalculator;
    private final DifyClient difyClient;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;
    private final String difyApiKey;
    private final String difyBaseUrl;
    private final String difyWebhookPath;
    private final String difyResponseMode;
    private final String difyTriggerMode;
    private final String difyWebhookPayloadMode;
    private final String difyInputVarName;
    private final String difyInputFormat;
    private final String difyInternalKey;

    public RiskAssessmentService(HealthRecordService healthRecordService,
            RiskAssessmentMapper riskAssessmentMapper,
            MedicalCalculator medicalCalculator,
            DifyClient difyClient,
            UserServiceClient userServiceClient,
            ObjectMapper objectMapper,
            @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
            @Value("${dify.workflows.risk-assessment.api-key:}") String difyApiKey,
            @Value("${dify.workflows.risk-assessment.webhook-path:}") String difyWebhookPath,
            @Value("${dify.workflows.risk-assessment.response-mode:blocking}") String difyResponseMode,
            @Value("${dify.workflows.risk-assessment.trigger-mode:api}") String difyTriggerMode,
            @Value("${dify.workflows.risk-assessment.webhook-payload-mode:auto}") String difyWebhookPayloadMode,
            @Value("${dify.workflows.risk-assessment.input-var-name:inputs}") String difyInputVarName,
            @Value("${dify.workflows.risk-assessment.input-format:object}") String difyInputFormat,
            @Value("${dify-internal.key:}") String difyInternalKey) {
        this.healthRecordService = healthRecordService;
        this.riskAssessmentMapper = riskAssessmentMapper;
        this.medicalCalculator = medicalCalculator;
        this.difyClient = difyClient;
        this.userServiceClient = userServiceClient;
        this.objectMapper = objectMapper;
        this.difyApiKey = difyApiKey;
        this.difyBaseUrl = difyBaseUrl;
        this.difyWebhookPath = difyWebhookPath;
        this.difyResponseMode = difyResponseMode;
        this.difyTriggerMode = difyTriggerMode == null ? "api" : difyTriggerMode.trim().toLowerCase();
        this.difyWebhookPayloadMode = difyWebhookPayloadMode == null ? "auto"
                : difyWebhookPayloadMode.trim().toLowerCase();
        this.difyInputVarName = (difyInputVarName == null || difyInputVarName.isBlank())
                ? DifyRiskAssessmentWorkflowContract.INPUT_VARIABLE_NAME
                : difyInputVarName.trim();
        this.difyInputFormat = difyInputFormat == null ? "object" : difyInputFormat.trim().toLowerCase();
        this.difyInternalKey = difyInternalKey;
    }

    @Transactional
    public Map<String, Object> assess(String userId, RiskAssessRequest request) {
        Map<String, Object> userProfile = userServiceClient.getUserProfile(userId, difyInternalKey);
        int age = resolveAge(userProfile);
        String gender = resolveGender(userProfile);

        MedicalCalculator.BmiResult bmiResult = medicalCalculator.calculateBmi(request.getHeight(),
                request.getWeight());
        MedicalCalculator.GlucoseResult glucoseResult = medicalCalculator.evaluateGlucose(request.getFastingGlucose());
        MedicalCalculator.BaseRiskResult baseRisk = medicalCalculator.calculateBaseRisk(request, bmiResult,
                glucoseResult, age);

        String recordId = healthRecordService.saveFromQuestionnaire(userId, request, bmiResult.bmi());

        int riskScore = baseRisk.baseRiskScore();
        int riskLevel = medicalCalculator.mapRiskLevel(riskScore);
        int glucoseLevel = glucoseResult.glucoseLevel();
        String confidence = "medium";
        List<Map<String, Object>> factors = new ArrayList<>(baseRisk.riskFactors());
        List<SuggestionItem> suggestions = defaultSuggestionItems();
        String difyRunId = null;
        String aiRaw = null;
        String analysis = "基于医学计算器的基础风险评估完成。";

        JsonNode difyResponse = callDify(userId, request, userProfile, age, gender, bmiResult, glucoseResult, baseRisk);
        aiRaw = difyResponse.toString();
        difyRunId = difyResponse.path("id").asText(null);
        if (difyRunId == null || difyRunId.isBlank()) {
            difyRunId = difyResponse.path("workflow_run_id").asText(null);
        }

        JsonNode assessmentNode = extractRiskAssessment(difyResponse);
        if (assessmentNode.isMissingNode()) {
            String preview = aiRaw.length() > 800 ? aiRaw.substring(0, 800) + "..." : aiRaw;
            throw new BusinessException(502, "Dify 工作流响应缺少 risk_assessment 字段，请检查工作流输出节点。原始响应: " + preview);
        }

        riskScore = assessmentNode.path("risk_score").asInt(riskScore);
        riskLevel = mapDifyRiskLevel(assessmentNode.path("risk_level").asText("medium"));
        confidence = assessmentNode.path("confidence").asText(confidence);
        analysis = assessmentNode.path("analysis").asText(analysis);
        glucoseLevel = mapGlucoseLevelName(
                assessmentNode.path("glucose_level").asText(glucoseResult.glucoseLevelName()));
        if (assessmentNode.has("risk_factors")) {
            factors = objectMapper.convertValue(assessmentNode.get("risk_factors"), List.class);
        }
        if (assessmentNode.has("suggestions")) {
            suggestions = parseSuggestions(assessmentNode.get("suggestions"));
        }

        String assessmentId = IdGenerator.nextId("asmt_");
        RiskAssessment assessment = new RiskAssessment();
        assessment.setAssessmentId(assessmentId);
        assessment.setUserId(userId);
        assessment.setHealthRecordId(recordId);
        assessment.setDifyWorkflowId(difyRunId);
        assessment.setRiskLevel(riskLevel);
        assessment.setRiskScore(riskScore);
        assessment.setBmiSnapshot(bmiResult.bmi());
        assessment.setGlucoseLevel(glucoseLevel);
        assessment.setConfidence(mapConfidence(confidence));
        assessment.setGenerationStatus(2);
        assessment.setReportSummary(analysis.length() > 500 ? analysis.substring(0, 500) : analysis);
        assessment.setAiRawResponse(aiRaw);
        riskAssessmentMapper.insert(assessment);

        int sort = 0;
        for (Map<String, Object> factor : factors) {
            RiskAssessmentFactor entity = new RiskAssessmentFactor();
            entity.setFactorId(IdGenerator.nextId("rf_"));
            entity.setAssessmentId(assessmentId);
            entity.setFactorCode(factor.get("factor_code") != null ? String.valueOf(factor.get("factor_code")) : null);
            entity.setFactorName(String.valueOf(factor.getOrDefault("name", "风险因素")));
            Object weight = factor.get("weight");
            entity.setWeight(weight == null ? null : new BigDecimal(weight.toString()));
            Object factorLevel = factor.get("factor_level");
            entity.setFactorLevel(factorLevel == null ? null : Integer.parseInt(factorLevel.toString()));
            entity.setDescription(String.valueOf(factor.getOrDefault("description", "")));
            entity.setSortOrder(sort++);
            riskAssessmentMapper.insertFactor(entity);
        }

        sort = 0;
        for (SuggestionItem suggestion : suggestions) {
            RiskAssessmentSuggestion entity = new RiskAssessmentSuggestion();
            entity.setSuggestionId(IdGenerator.nextId("rs_"));
            entity.setAssessmentId(assessmentId);
            entity.setCategory(suggestion.category());
            entity.setPriority(suggestion.priority());
            entity.setContent(suggestion.content());
            entity.setSortOrder(sort++);
            riskAssessmentMapper.insertSuggestion(entity);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assessmentId", assessmentId);
        result.put("riskLevel", medicalCalculator.riskLevelName(riskLevel));
        result.put("riskScore", riskScore);
        result.put("bmi", bmiResult.bmi());
        result.put("bmiLevel", bmiResult.bmiLevel());
        result.put("glucoseLevel", glucoseLevelName(glucoseLevel));
        result.put("factors", factors);
        result.put("suggestions", suggestions.stream().map(SuggestionItem::content).toList());
        result.put("confidence", confidence);
        result.put("reportSummary", analysis);
        result.put("userAge", age);
        result.put("userGender", gender);

        UserMessageClientHelper.notifyRiskCompleted(userServiceClient, difyInternalKey, userId,
                assessmentId, medicalCalculator.riskLevelName(riskLevel), riskScore);
        return result;
    }

    public void notifyAssessmentFailed(String userId, String errorMessage) {
        UserMessageClientHelper.notifyRiskFailed(userServiceClient, difyInternalKey, userId, errorMessage);
    }

    private JsonNode callDify(String userId, RiskAssessRequest request, Map<String, Object> userProfile,
            int age, String gender,
            MedicalCalculator.BmiResult bmiResult,
            MedicalCalculator.GlucoseResult glucoseResult,
            MedicalCalculator.BaseRiskResult baseRisk) {
        Map<String, Object> payload = buildDifyPayload(userId, request, userProfile, age, gender, bmiResult,
                glucoseResult, baseRisk);
        if (payload.isEmpty()) {
            throw new BusinessException(500, "Dify 请求体构建失败，无法发起工作流调用");
        }

        if ("webhook".equals(difyTriggerMode) || "webhook-first".equals(difyTriggerMode)) {
            try {
                return invokeWebhook(payload);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                if ("webhook".equals(difyTriggerMode)) {
                    throw new BusinessException(502, "Dify Webhook 调用失败: " + e.getMessage());
                }
                log.warn("Dify Webhook 调用失败，尝试 Workflow API: {}", e.getMessage());
            }
        }

        return invokeWorkflowApi(userId, payload);
    }

    private JsonNode invokeWebhook(Map<String, Object> payload) {
        if (difyWebhookPath == null || difyWebhookPath.isBlank()) {
            throw new BusinessException(500, "未配置 dify.workflows.risk-assessment.webhook-path，无法调用 Dify Webhook");
        }
        log.info("发起 Dify Webhook 风险评估, path={}", difyWebhookPath);
        return difyClient.triggerWebhook(difyWebhookPath, payload);
    }

    private JsonNode invokeWorkflowApi(String userId, Map<String, Object> payload) {
        if (difyApiKey == null || difyApiKey.isBlank()) {
            throw new BusinessException(500, "未配置 dify.workflows.risk-assessment.api-key，无法调用 Dify 工作流");
        }
        Map<String, Object> inputs = buildWorkflowInputs(payload);
        log.info("发起 Dify Workflow API 风险评估, userId={}", userId);
        return difyClient.runWorkflowBlocking(difyApiKey, userId, inputs, difyResponseMode);
    }

    /**
     * 构建 Dify Workflow API 的 inputs（开始节点各变量平铺传入）。
     * 请求体：{@code { "inputs": { user_id, user_profile, questionnaire, medical_calc_results, risk_factors } }}
     */
    private Map<String, Object> buildWorkflowInputs(Map<String, Object> payload) {
        Map<String, Object> normalized = DifyRiskAssessmentWorkflowContract.ensureStringEncodedInputs(payload,
                objectMapper);
        if (difyInputVarName == null || difyInputVarName.isBlank() || "flat".equalsIgnoreCase(difyInputVarName)) {
            return DifyJsonSchema.flatWorkflowInputs(normalized);
        }
        return DifyJsonSchema.wrapWorkflowInputs(difyInputVarName, normalized, difyInputFormat, objectMapper);
    }

    private Map<String, Object> buildDifyPayload(String userId, RiskAssessRequest request,
            Map<String, Object> userProfile, int age, String gender,
            MedicalCalculator.BmiResult bmiResult,
            MedicalCalculator.GlucoseResult glucoseResult,
            MedicalCalculator.BaseRiskResult baseRisk) {
        try {
            Map<String, Object> questionnaire = objectMapper.convertValue(request, Map.class);
            Map<String, Object> profile = new LinkedHashMap<>(userProfile);
            profile.put("age", age);
            profile.put("gender", gender);

            Map<String, Object> medicalCalc = Map.of(
                    "bmi", bmiResult.bmi(),
                    "bmiLevel", bmiResult.bmiLevel(),
                    "glucoseLevel", glucoseResult.glucoseLevelName(),
                    "baseRiskScore", baseRisk.baseRiskScore());
            return DifyRiskAssessmentWorkflowContract.buildInputObject(
                    userId, profile, questionnaire, medicalCalc, baseRisk.riskFactors(), objectMapper);
        } catch (Exception e) {
            return Map.of();
        }
    }

    JsonNode extractRiskAssessment(JsonNode difyResponse) {
        JsonNode assessment = difyResponse.path("risk_assessment");
        if (!assessment.isMissingNode())
            return assessment;
        assessment = difyResponse.path("data").path("outputs").path("risk_assessment");
        if (!assessment.isMissingNode())
            return assessment;
        assessment = difyResponse.path("outputs").path("risk_assessment");
        if (!assessment.isMissingNode())
            return assessment;

        // 部分工作流将 JSON 放在 text 输出中
        JsonNode text = difyResponse.path("data").path("outputs").path("text");
        if (text.isMissingNode()) {
            text = difyResponse.path("outputs").path("text");
        }
        if (text.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(text.asText());
                JsonNode fromText = parsed.path("risk_assessment");
                if (!fromText.isMissingNode())
                    return fromText;
                if (parsed.has("risk_score"))
                    return parsed;
            } catch (Exception e) {
                log.debug("无法从 Dify text 输出解析 risk_assessment: {}", e.getMessage());
            }
        }
        return objectMapper.missingNode();
    }

    int resolveAge(Map<String, Object> profile) {
        Object birthDate = profile.get("birth_date");
        if (birthDate == null)
            return 0;
        try {
            return Period.between(LocalDate.parse(birthDate.toString()), LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }

    String resolveGender(Map<String, Object> profile) {
        Object gender = profile.get("gender");
        if (gender == null)
            return "unknown";
        return switch (Integer.parseInt(gender.toString())) {
            case 1 -> "male";
            case 2 -> "female";
            default -> "unknown";
        };
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyRiskAssessmentWorkflowContract.workflowSpec(
                difyBaseUrl, difyApiKey, difyInputFormat, difyResponseMode, objectMapper);
    }

    public Map<String, Object> getHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<RiskAssessment> records = riskAssessmentMapper.findByUserId(userId, offset, size);
        int total = riskAssessmentMapper.countByUserId(userId);
        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assessmentId", r.getAssessmentId());
            m.put("riskLevel", medicalCalculator.riskLevelName(r.getRiskLevel()));
            m.put("riskScore", r.getRiskScore());
            m.put("assessedAt", r.getAssessedAt());
            m.put("createdAt", r.getAssessedAt());
            return m;
        }).toList();
        return Map.of("records", list, "total", total);
    }

    public Map<String, Object> getDetail(String assessmentId) {
        RiskAssessment assessment = riskAssessmentMapper.findById(assessmentId);
        if (assessment == null) {
            return null;
        }
        List<RiskAssessmentFactor> factors = riskAssessmentMapper.findFactors(assessmentId);
        List<RiskAssessmentSuggestion> suggestions = riskAssessmentMapper.findSuggestions(assessmentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assessmentId", assessment.getAssessmentId());
        result.put("riskLevel", medicalCalculator.riskLevelName(assessment.getRiskLevel()));
        result.put("riskScore", assessment.getRiskScore());
        result.put("bmi", assessment.getBmiSnapshot());
        result.put("glucoseLevel", glucoseLevelName(assessment.getGlucoseLevel()));
        result.put("reportSummary", assessment.getReportSummary());
        result.put("factors", factors.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getFactorName());
            m.put("weight", f.getWeight());
            m.put("description", f.getDescription() == null ? "" : f.getDescription());
            return m;
        }).toList());
        result.put("suggestions", suggestions.stream().map(RiskAssessmentSuggestion::getContent).toList());
        result.put("confidence", confidenceName(assessment.getConfidence()));
        result.put("chartData", buildChartData(assessment));
        result.put("assessedAt", assessment.getAssessedAt());
        result.put("createdAt", assessment.getAssessedAt());
        return result;
    }

    private Map<String, Object> buildChartData(RiskAssessment assessment) {
        int score = assessment.getRiskScore();
        return Map.of(
                "gauge", Map.of(
                        "min", 0, "max", 100, "value", score,
                        "thresholds", List.of(
                                Map.of("level", "low", "max", 30, "color", "#43A047"),
                                Map.of("level", "medium", "max", 60, "color", "#F57C00"),
                                Map.of("level", "high", "max", 100, "color", "#E53935"))));
    }

    private List<SuggestionItem> parseSuggestions(JsonNode node) {
        List<SuggestionItem> list = new ArrayList<>();
        if (!node.isArray())
            return list;
        for (JsonNode item : node) {
            if (item.isTextual()) {
                list.add(new SuggestionItem(3, 2, item.asText()));
            } else {
                list.add(new SuggestionItem(
                        item.path("category").asInt(3),
                        item.path("priority").asInt(2),
                        item.path("content").asText("")));
            }
        }
        return list.isEmpty() ? defaultSuggestionItems() : list;
    }

    private List<SuggestionItem> defaultSuggestionItems() {
        return List.of(
                new SuggestionItem(1, 1, "控制饮食，减少高糖高脂食物摄入"),
                new SuggestionItem(2, 2, "每周至少运动5次，每次30分钟以上"),
                new SuggestionItem(4, 1, "定期监测空腹及餐后血糖"));
    }

    private record SuggestionItem(int category, int priority, String content) {
    }

    private int mapDifyRiskLevel(String level) {
        return switch (level) {
            case "low" -> 1;
            case "high" -> 3;
            default -> 2;
        };
    }

    private int mapConfidence(String confidence) {
        return switch (confidence) {
            case "low" -> 1;
            case "high" -> 3;
            default -> 2;
        };
    }

    private String confidenceName(Integer confidence) {
        if (confidence == null)
            return "medium";
        return switch (confidence) {
            case 1 -> "low";
            case 3 -> "high";
            default -> "medium";
        };
    }

    private int mapGlucoseLevelName(String name) {
        return switch (name) {
            case "prediabetes" -> 2;
            case "diabetes" -> 3;
            default -> 1;
        };
    }

    private String glucoseLevelName(Integer level) {
        if (level == null)
            return "normal";
        return switch (level) {
            case 2 -> "prediabetes";
            case 3 -> "diabetes";
            default -> "normal";
        };
    }
}
