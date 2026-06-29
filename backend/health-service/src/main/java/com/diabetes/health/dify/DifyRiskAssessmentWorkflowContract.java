package com.diabetes.health.dify;

import com.diabetes.common.dify.DifyJsonSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 糖尿病风险评估 Dify 工作流 JSON 契约。
 *
 * <p>除 {@code user_id} 外，结构化数据均以 JSON 文本字符串传入 Dify
 *（与工作流开始节点 String / Paragraph 类型一致）。</p>
 */
public final class DifyRiskAssessmentWorkflowContract {

    public static final String INPUT_VARIABLE_NAME = "inputs";
    public static final String OUTPUT_KEY = "risk_assessment";

    private DifyRiskAssessmentWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String userId,
                                                       Map<String, Object> userProfile,
                                                       Map<String, Object> questionnaire,
                                                       Map<String, Object> medicalCalcResults,
                                                       List<Map<String, Object>> riskFactors,
                                                       ObjectMapper mapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", DifyJsonSchema.asString(userId));
        payload.put("user_profile", DifyJsonSchema.asJsonString(toSchemaCompliantProfile(userProfile), mapper));
        payload.put("questionnaire", DifyJsonSchema.asJsonString(DifyJsonSchema.asObject(questionnaire), mapper));
        payload.put("medical_calc_results", DifyJsonSchema.asJsonString(toSchemaCompliantMedicalCalc(medicalCalcResults), mapper));
        payload.put("risk_factors", DifyJsonSchema.asJsonArrayString(riskFactors == null ? List.of() : riskFactors, mapper));
        return ensureStringEncodedInputs(payload, mapper);
    }

    /**
     * 调用 Dify 前兜底：结构化字段必须为 JSON 文本字符串（Paragraph / Text-input 类型）。
     */
    public static Map<String, Object> ensureStringEncodedInputs(Map<String, Object> payload, ObjectMapper mapper) {
        if (payload == null || payload.isEmpty()) {
            return payload == null ? Map.of() : new LinkedHashMap<>(payload);
        }
        Map<String, Object> out = new LinkedHashMap<>(payload);
        stringifyInputField(out, "user_profile", mapper, false);
        stringifyInputField(out, "questionnaire", mapper, false);
        stringifyInputField(out, "medical_calc_results", mapper, false);
        stringifyInputField(out, "risk_factors", mapper, true);
        return out;
    }

    private static void stringifyInputField(Map<String, Object> map,
                                            String key,
                                            ObjectMapper mapper,
                                            boolean array) {
        Object value = map.get(key);
        if (value instanceof String) {
            if (((String) value).isBlank()) {
                map.put(key, array ? "[]" : "{}");
            }
            return;
        }
        if (value == null) {
            map.put(key, array ? "[]" : "{}");
            return;
        }
        map.put(key, array
                ? DifyJsonSchema.asJsonArrayString(value, mapper)
                : DifyJsonSchema.asJsonString(value, mapper));
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("user_id", DifyJsonSchema.stringType());
        properties.put("user_profile", DifyJsonSchema.stringType());
        properties.put("questionnaire", DifyJsonSchema.stringType());
        properties.put("medical_calc_results", DifyJsonSchema.stringType());
        properties.put("risk_factors", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> factorItemProps = new LinkedHashMap<>();
        factorItemProps.put("factor_code", DifyJsonSchema.stringType());
        factorItemProps.put("name", DifyJsonSchema.stringType());
        factorItemProps.put("weight", DifyJsonSchema.numberType());
        factorItemProps.put("factor_level", DifyJsonSchema.integerType());
        factorItemProps.put("description", DifyJsonSchema.stringType());

        Map<String, Object> suggestionItemProps = new LinkedHashMap<>();
        suggestionItemProps.put("category", DifyJsonSchema.integerType());
        suggestionItemProps.put("priority", DifyJsonSchema.integerType());
        suggestionItemProps.put("content", DifyJsonSchema.stringType());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("risk_score", DifyJsonSchema.integerType());
        properties.put("risk_level", DifyJsonSchema.stringType());
        properties.put("confidence", DifyJsonSchema.stringType());
        properties.put("analysis", DifyJsonSchema.stringType());
        properties.put("glucose_level", DifyJsonSchema.stringType());
        properties.put("risk_factors", DifyJsonSchema.array(DifyJsonSchema.object(factorItemProps)));
        properties.put("suggestions", DifyJsonSchema.array(DifyJsonSchema.object(suggestionItemProps)));
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> workflowSpec(String baseUrl,
                                                   String apiKey,
                                                   String inputFormat,
                                                   String responseMode,
                                                   ObjectMapper mapper) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", responseMode);
        spec.put("inputFormat", inputFormat);
        spec.put("inputVariableName", "flat");
        spec.put("inputVariables", List.of(
                "user_id", "user_profile", "questionnaire", "medical_calc_results", "risk_factors"));
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("outputJsonSchema", outputJsonSchema());
        spec.put("inputExample", buildInputObject("usr_xxx", Map.of("age", 35, "gender", "male"),
                Map.of("height", 170, "weight", 70), Map.of("bmi", 24.2, "baseRiskScore", 45), List.of(), mapper));
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }

    private static Map<String, Object> toSchemaCompliantProfile(Map<String, Object> profile) {
        Map<String, Object> result = DifyJsonSchema.asObject(profile);
        if (result.containsKey("age")) {
            result.put("age", DifyJsonSchema.asInteger(result.get("age")));
        }
        if (result.containsKey("gender")) {
            result.put("gender", DifyJsonSchema.asString(result.get("gender")));
        }
        return result;
    }

    private static Map<String, Object> toSchemaCompliantMedicalCalc(Map<String, Object> calc) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (calc == null || calc.isEmpty()) return result;
        if (calc.containsKey("bmi")) result.put("bmi", DifyJsonSchema.asNumber(calc.get("bmi")));
        if (calc.containsKey("bmiLevel")) result.put("bmiLevel", DifyJsonSchema.asString(calc.get("bmiLevel")));
        if (calc.containsKey("glucoseLevel")) result.put("glucoseLevel", DifyJsonSchema.asString(calc.get("glucoseLevel")));
        if (calc.containsKey("baseRiskScore")) result.put("baseRiskScore", DifyJsonSchema.asInteger(calc.get("baseRiskScore")));
        return result;
    }
}
