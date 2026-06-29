package com.diabetes.plan.dify;

import com.diabetes.common.dify.DifyJsonSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康方案生成 Dify 工作流 JSON 契约。
 *
 * <p>入参：开始节点 7 个独立变量，后端平铺写入 API {@code inputs}。</p>
 * <p>出参：工作流返回浅层 LLM Structured Output（{@link #LLM_OUTPUT_KEY}），
 * 后端 {@link DifyPlanLlmOutputAssembler} 组装为完整方案。</p>
 */
public final class DifyPlanWorkflowContract {

    /** @deprecated 方案工作流使用 {@link #INPUT_LAYOUT}={@code flat}，不再使用单变量包裹 */
    @Deprecated
    public static final String INPUT_VARIABLE_NAME = "inputs";

    public static final String INPUT_LAYOUT = "flat";
    public static final List<String> INPUT_FIELD_NAMES = List.of(
            "query", "user_id", "daily_calories",
            "user_profile", "health_profile", "risk_data", "checkin_data"
    );

    /** Dify 结束节点应输出的浅层 LLM 结果（深度 ≤4，避免 Dify 深度限制） */
    public static final String LLM_OUTPUT_KEY = "plan_llm_output";

    /** @deprecated 兼容旧工作流直接输出嵌套 health_plan；新工作流请使用 {@link #LLM_OUTPUT_KEY} */
    @Deprecated
    public static final String OUTPUT_KEY = "health_plan";

    public static final String DEFAULT_QUERY = "请根据用户健康画像生成个性化健康管理方案";

    private DifyPlanWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String userId,
                                                       Map<String, Object> profile,
                                                       int dailyCalories) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", DEFAULT_QUERY);
        payload.put("user_id", DifyJsonSchema.asString(userId));
        payload.put("daily_calories", dailyCalories);
        payload.put("user_profile", DifyJsonSchema.asObject(profile == null ? null : profile.get("user_profile")));
        payload.put("health_profile", DifyJsonSchema.asObject(profile == null ? null : profile.get("health_profile")));
        payload.put("checkin_data", DifyJsonSchema.asObject(profile == null ? null : profile.get("checkin_data")));

        Object riskData = profile == null ? null : profile.get("risk_data");
        if (riskData instanceof Map<?, ?> riskMap && !riskMap.isEmpty()) {
            payload.put("risk_data", riskData);
        }
        return payload;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", DifyJsonSchema.stringType());
        properties.put("user_id", DifyJsonSchema.stringType());
        properties.put("daily_calories", DifyJsonSchema.integerType());
        properties.put("user_profile", DifyJsonSchema.object(Map.of(
                "age", DifyJsonSchema.integerType(),
                "gender", DifyJsonSchema.stringType(),
                "nickname", DifyJsonSchema.stringType()
        )));
        properties.put("health_profile", DifyJsonSchema.object(Map.of(
                "height", DifyJsonSchema.numberType(),
                "weight", DifyJsonSchema.numberType(),
                "bmi", DifyJsonSchema.numberType(),
                "fastingGlucose", DifyJsonSchema.numberType(),
                "exerciseFreq", DifyJsonSchema.integerType(),
                "dietType", DifyJsonSchema.stringType()
        )));
        properties.put("risk_data", DifyJsonSchema.object(Map.of(
                "riskLevel", DifyJsonSchema.stringType(),
                "riskScore", DifyJsonSchema.integerType()
        )));
        properties.put("checkin_data", DifyJsonSchema.object(Map.of(
                "recent_days", DifyJsonSchema.integerType(),
                "total_recent", DifyJsonSchema.integerType(),
                "records", DifyJsonSchema.array(DifyJsonSchema.object(Map.of()))
        )));
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> llmOutputJsonSchema() {
        Map<String, Object> foodItem = Map.of(
                "name", DifyJsonSchema.stringType(),
                "amount", DifyJsonSchema.stringType(),
                "calories", DifyJsonSchema.integerType(),
                "gi_level", DifyJsonSchema.stringType()
        );
        Map<String, Object> exerciseItem = Map.of(
                "type", DifyJsonSchema.stringType(),
                "duration", DifyJsonSchema.stringType(),
                "frequency", DifyJsonSchema.stringType(),
                "intensity", DifyJsonSchema.stringType(),
                "calories_burned", DifyJsonSchema.integerType(),
                "caution", DifyJsonSchema.stringType()
        );

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", DifyJsonSchema.stringType());
        properties.put("diet_principles", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        properties.put("foods_to_avoid", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        properties.put("foods_to_recommend", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        properties.put("breakfast_time", DifyJsonSchema.stringType());
        properties.put("breakfast_foods", DifyJsonSchema.array(DifyJsonSchema.object(foodItem)));
        properties.put("breakfast_total_calories", DifyJsonSchema.integerType());
        properties.put("lunch_time", DifyJsonSchema.stringType());
        properties.put("lunch_foods", DifyJsonSchema.array(DifyJsonSchema.object(foodItem)));
        properties.put("lunch_total_calories", DifyJsonSchema.integerType());
        properties.put("dinner_time", DifyJsonSchema.stringType());
        properties.put("dinner_foods", DifyJsonSchema.array(DifyJsonSchema.object(foodItem)));
        properties.put("dinner_total_calories", DifyJsonSchema.integerType());
        properties.put("snack_time", DifyJsonSchema.stringType());
        properties.put("snack_foods", DifyJsonSchema.array(DifyJsonSchema.object(foodItem)));
        properties.put("snack_total_calories", DifyJsonSchema.integerType());
        properties.put("exercise_weekly_goal", DifyJsonSchema.stringType());
        properties.put("exercise_items", DifyJsonSchema.array(DifyJsonSchema.object(exerciseItem)));
        properties.put("rest_wake_up", DifyJsonSchema.stringType());
        properties.put("rest_sleep", DifyJsonSchema.stringType());
        properties.put("rest_nap", DifyJsonSchema.stringType());
        properties.put("rest_glucose_monitor_times", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        properties.put("rest_routine_tips", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        properties.put("medication_note", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    /** @deprecated 使用 {@link #llmOutputJsonSchema()} 作为 Dify 工作流出参；完整 health_plan 由后端组装 */
    @Deprecated
    public static Map<String, Object> outputJsonSchema() {
        return llmOutputJsonSchema();
    }

    public static Map<String, Object> workflowSpec(String baseUrl,
                                                   String apiKey,
                                                   String responseMode) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> sampleProfile = Map.of(
                "user_profile", Map.of("age", 35, "gender", "male", "nickname", "示例用户"),
                "health_profile", Map.of("height", 170, "weight", 72.5, "bmi", 25.1),
                "risk_data", Map.of("riskLevel", "medium", "riskScore", 45),
                "checkin_data", Map.of("recent_days", 14, "total_recent", 5, "records", List.of())
        );
        Map<String, Object> llmOutputExample = new LinkedHashMap<>();
        llmOutputExample.put("summary", "基于用户画像生成的方案概述");
        llmOutputExample.put("breakfast_time", "07:30");
        llmOutputExample.put("breakfast_foods", List.of(Map.of(
                "name", "燕麦片", "amount", "40g", "calories", 150, "gi_level", "low")));
        llmOutputExample.put("breakfast_total_calories", 370);
        llmOutputExample.put("lunch_time", "12:00");
        llmOutputExample.put("lunch_foods", List.of());
        llmOutputExample.put("lunch_total_calories", 500);
        llmOutputExample.put("dinner_time", "18:30");
        llmOutputExample.put("dinner_foods", List.of());
        llmOutputExample.put("dinner_total_calories", 500);
        llmOutputExample.put("snack_time", "15:00");
        llmOutputExample.put("snack_foods", List.of());
        llmOutputExample.put("snack_total_calories", 100);
        llmOutputExample.put("diet_principles", List.of("控制总热量"));
        llmOutputExample.put("foods_to_avoid", List.of("含糖饮料"));
        llmOutputExample.put("foods_to_recommend", List.of("全谷物"));
        llmOutputExample.put("exercise_weekly_goal", "每周150分钟中等强度有氧运动");
        llmOutputExample.put("exercise_items", List.of(Map.of(
                "type", "快走", "duration", "30分钟", "frequency", "每日",
                "intensity", "中等", "calories_burned", 150, "caution", "餐后1小时")));
        llmOutputExample.put("rest_wake_up", "06:30");
        llmOutputExample.put("rest_sleep", "22:30");
        llmOutputExample.put("rest_nap", "午休20-30分钟");
        llmOutputExample.put("rest_glucose_monitor_times", List.of("空腹", "睡前"));
        llmOutputExample.put("rest_routine_tips", List.of("固定作息"));
        llmOutputExample.put("medication_note", "请遵医嘱用药");

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", responseMode);
        spec.put("inputLayout", INPUT_LAYOUT);
        spec.put("inputFieldNames", INPUT_FIELD_NAMES);
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("llmOutputJsonSchema", llmOutputJsonSchema());
        spec.put("outputJsonSchema", llmOutputJsonSchema());
        spec.put("outputKey", LLM_OUTPUT_KEY);
        spec.put("outputAssembly", "backend");
        spec.put("inputExample", buildInputObject("usr_xxx", sampleProfile, 1800));
        spec.put("outputExample", llmOutputExample);
        return spec;
    }
}
