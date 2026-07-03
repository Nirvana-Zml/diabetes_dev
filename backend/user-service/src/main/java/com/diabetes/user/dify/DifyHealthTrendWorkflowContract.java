package com.diabetes.user.dify;

import com.diabetes.common.dify.DifyJsonSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康趋势分析 Dify 工作流 JSON 契约（Workflow #7）。
 */
public final class DifyHealthTrendWorkflowContract {

    public static final String INPUT_LAYOUT = "flat";
    public static final List<String> INPUT_FIELD_NAMES = List.of("query", "health_history", "user_baseline");
    public static final String OUTPUT_KEY = "trend_analysis";
    public static final String DEFAULT_QUERY = "请分析用户近期的健康指标变化趋势";

    private DifyHealthTrendWorkflowContract() {
    }

    public static Map<String, Object> buildInputs(ObjectMapper objectMapper,
                                                  List<Map<String, Object>> history,
                                                  Map<String, Object> baseline) throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", DEFAULT_QUERY);
        inputs.put("health_history", objectMapper.writeValueAsString(history == null ? List.of() : history));
        inputs.put("user_baseline", objectMapper.writeValueAsString(baseline == null ? Map.of() : baseline));
        return inputs;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", DifyJsonSchema.stringType());
        properties.put("health_history", DifyJsonSchema.stringType());
        properties.put("user_baseline", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> pointProps = new LinkedHashMap<>();
        pointProps.put("date", DifyJsonSchema.stringType());
        pointProps.put("value", DifyJsonSchema.numberType());

        Map<String, Object> bpPointProps = new LinkedHashMap<>();
        bpPointProps.put("date", DifyJsonSchema.stringType());
        bpPointProps.put("systolic", DifyJsonSchema.numberType());
        bpPointProps.put("diastolic", DifyJsonSchema.numberType());

        Map<String, Object> trendProps = new LinkedHashMap<>();
        trendProps.put("direction", DifyJsonSchema.stringType());
        trendProps.put("avg_value", DifyJsonSchema.numberType());
        trendProps.put("change_rate", DifyJsonSchema.numberType());
        trendProps.put("data_points", DifyJsonSchema.array(DifyJsonSchema.object(pointProps)));

        Map<String, Object> bpTrendProps = new LinkedHashMap<>();
        bpTrendProps.put("direction", DifyJsonSchema.stringType());
        bpTrendProps.put("avg_systolic", DifyJsonSchema.numberType());
        bpTrendProps.put("avg_diastolic", DifyJsonSchema.numberType());
        bpTrendProps.put("data_points", DifyJsonSchema.array(DifyJsonSchema.object(bpPointProps)));

        Map<String, Object> anomalyProps = new LinkedHashMap<>();
        anomalyProps.put("type", DifyJsonSchema.stringType());
        anomalyProps.put("date", DifyJsonSchema.stringType());
        anomalyProps.put("value", DifyJsonSchema.numberType());
        anomalyProps.put("severity", DifyJsonSchema.stringType());
        anomalyProps.put("description", DifyJsonSchema.stringType());
        anomalyProps.put("suggestion", DifyJsonSchema.stringType());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", DifyJsonSchema.stringType());
        properties.put("risk_level", DifyJsonSchema.stringType());
        properties.put("bmi_trend", DifyJsonSchema.object(trendProps));
        properties.put("glucose_trend", DifyJsonSchema.object(trendProps));
        properties.put("bp_trend", DifyJsonSchema.object(bpTrendProps));
        properties.put("anomalies", DifyJsonSchema.array(DifyJsonSchema.object(anomalyProps)));
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
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }
}
