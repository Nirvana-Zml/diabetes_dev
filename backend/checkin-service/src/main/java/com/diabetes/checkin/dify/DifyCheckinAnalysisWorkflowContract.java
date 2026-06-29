package com.diabetes.checkin.dify;

import com.diabetes.common.dify.DifyJsonSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打卡 AI 行为分析 Dify 工作流 JSON 契约。
 *
 * <p>入参：开始节点 7 个独立变量，后端平铺写入 API {@code inputs}。</p>
 * <p>专家角色与输出约束写在 Dify LLM 节点系统提示词中，不作为开始节点入参。</p>
 */
public final class DifyCheckinAnalysisWorkflowContract {

    /** @deprecated 使用 {@link #INPUT_LAYOUT}={@code flat}，不再使用单变量包裹 */
    @Deprecated
    public static final String INPUT_VARIABLE_NAME = "inputs";

    public static final String INPUT_LAYOUT = "flat";
    public static final List<String> INPUT_FIELD_NAMES = List.of(
            "query", "user_id", "start_date", "end_date",
            "checkin_stats", "trend_data", "user_profile"
    );
    public static final String OUTPUT_KEY = "behavior_analysis";
    public static final String DEFAULT_QUERY = "请分析用户的打卡行为数据，生成行为分析总结和改善建议";

    private DifyCheckinAnalysisWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String userId,
                                                       Map<String, Object> checkinStats,
                                                       Map<String, Object> trendData,
                                                       Map<String, Object> userProfile,
                                                       String startDate,
                                                       String endDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", DEFAULT_QUERY);
        payload.put("user_id", DifyJsonSchema.asString(userId));
        payload.put("start_date", DifyJsonSchema.asString(startDate));
        payload.put("end_date", DifyJsonSchema.asString(endDate));
        payload.put("checkin_stats", toSchemaCompliantStats(checkinStats));
        payload.put("trend_data", toSchemaCompliantTrends(trendData));
        payload.put("user_profile", toSchemaCompliantProfile(userProfile));
        return payload;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", DifyJsonSchema.stringType());
        properties.put("user_id", DifyJsonSchema.stringType());
        properties.put("start_date", DifyJsonSchema.stringType());
        properties.put("end_date", DifyJsonSchema.stringType());
        properties.put("checkin_stats", checkinStatsSchema());
        properties.put("trend_data", trendDataSchema());
        properties.put("user_profile", userProfileSchema());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> patternItemProps = new LinkedHashMap<>();
        patternItemProps.put("type", DifyJsonSchema.stringType());
        patternItemProps.put("pattern", DifyJsonSchema.stringType());
        patternItemProps.put("completion_rate", DifyJsonSchema.numberType());
        patternItemProps.put("description", DifyJsonSchema.stringType());
        patternItemProps.put("suggestion", DifyJsonSchema.stringType());

        Map<String, Object> anomalyItemProps = new LinkedHashMap<>();
        anomalyItemProps.put("date", DifyJsonSchema.stringType());
        anomalyItemProps.put("type", DifyJsonSchema.stringType());
        anomalyItemProps.put("value", DifyJsonSchema.numberType());
        anomalyItemProps.put("description", DifyJsonSchema.stringType());
        anomalyItemProps.put("possible_reason", DifyJsonSchema.stringType());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", DifyJsonSchema.stringType());
        properties.put("behavior_patterns", DifyJsonSchema.array(DifyJsonSchema.object(patternItemProps)));
        properties.put("anomalies", DifyJsonSchema.array(DifyJsonSchema.object(anomalyItemProps)));
        properties.put("improvements", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> workflowSpec(String baseUrl, String apiKey, String responseMode) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", responseMode);
        spec.put("inputLayout", INPUT_LAYOUT);
        spec.put("inputFieldNames", INPUT_FIELD_NAMES);
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("outputJsonSchema", outputJsonSchema());
        spec.put("inputExample", buildInputObject("usr_xxx", Map.of(), Map.of(), Map.of(), "2024-06-01", "2024-06-30"));
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }

    private static Map<String, Object> checkinStatsSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("totalCheckins", DifyJsonSchema.integerType());
        props.put("completionRate", DifyJsonSchema.numberType());
        props.put("totalPoints", DifyJsonSchema.integerType());
        props.put("streakDays", DifyJsonSchema.integerType());
        props.put("calendarData", DifyJsonSchema.object(Map.of()));
        return DifyJsonSchema.object(props);
    }

    private static Map<String, Object> trendDataSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("dietTrend", trendArraySchema());
        props.put("exerciseTrend", trendArraySchema());
        props.put("medicationTrend", trendArraySchema());
        props.put("glucoseTrend", trendArraySchema());
        return DifyJsonSchema.object(props);
    }

    private static Map<String, Object> userProfileSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("height", DifyJsonSchema.numberType());
        props.put("weight", DifyJsonSchema.numberType());
        props.put("bmi", DifyJsonSchema.numberType());
        props.put("fastingGlucose", DifyJsonSchema.numberType());
        return DifyJsonSchema.object(props);
    }

    private static Map<String, Object> trendArraySchema() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("date", DifyJsonSchema.stringType());
        itemProps.put("count", DifyJsonSchema.integerType());
        return DifyJsonSchema.array(DifyJsonSchema.object(itemProps));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toSchemaCompliantStats(Map<String, Object> stats) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (stats == null || stats.isEmpty()) {
            result.put("totalCheckins", 0);
            result.put("completionRate", 0.0);
            result.put("totalPoints", 0);
            result.put("streakDays", 0);
            result.put("calendarData", Map.of());
            return result;
        }
        result.put("totalCheckins", DifyJsonSchema.asInteger(stats.get("totalCheckins")));
        result.put("completionRate", DifyJsonSchema.asNumber(stats.get("completionRate")));
        result.put("totalPoints", DifyJsonSchema.asInteger(stats.get("totalPoints")));
        result.put("streakDays", DifyJsonSchema.asInteger(stats.get("streakDays")));
        Object calendar = stats.get("calendarData");
        result.put("calendarData", calendar instanceof Map ? calendar : Map.of());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toSchemaCompliantTrends(Map<String, Object> trends) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dietTrend", toTrendPoints(trends == null ? null : trends.get("dietTrend")));
        result.put("exerciseTrend", toTrendPoints(trends == null ? null : trends.get("exerciseTrend")));
        result.put("medicationTrend", toTrendPoints(trends == null ? null : trends.get("medicationTrend")));
        result.put("glucoseTrend", toTrendPoints(trends == null ? null : trends.get("glucoseTrend")));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toTrendPoints(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> points = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", DifyJsonSchema.asString(map.get("date")));
            point.put("count", DifyJsonSchema.asInteger(map.get("count")));
            points.add(point);
        }
        return points;
    }

    private static Map<String, Object> toSchemaCompliantProfile(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        putNumberIfPresent(result, profile, "height");
        putNumberIfPresent(result, profile, "weight");
        putNumberIfPresent(result, profile, "bmi");
        putNumberIfPresent(result, profile, "fastingGlucose", "fasting_glucose");
        return result;
    }

    private static void putNumberIfPresent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        if (keys == null || keys.length == 0) return;
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) {
                target.put(keys[0], DifyJsonSchema.asNumber(source.get(key)));
                return;
            }
        }
    }
}
