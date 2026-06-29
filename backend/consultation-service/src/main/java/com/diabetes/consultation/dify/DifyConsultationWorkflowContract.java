package com.diabetes.consultation.dify;

import com.diabetes.common.dify.DifyJsonSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模拟医生问诊 Dify 工作流 JSON 契约。
 */
public final class DifyConsultationWorkflowContract {

    public static final String INPUT_LAYOUT = "flat";
    public static final List<String> INPUT_FIELD_NAMES = List.of(
            "query", "conversation_id", "doctor_role", "patient_profile",
            "conversation_history", "knowledge_context"
    );
    public static final String OUTPUT_KEY = "doctor_reply";

    private DifyConsultationWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String query,
                                                       String conversationId,
                                                       String doctorRole,
                                                       String patientProfile,
                                                       String conversationHistory,
                                                       String knowledgeContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", DifyJsonSchema.asString(query));
        payload.put("conversation_id", DifyJsonSchema.asString(conversationId));
        payload.put("doctor_role", DifyJsonSchema.asString(doctorRole));
        payload.put("patient_profile", DifyJsonSchema.asString(patientProfile));
        payload.put("conversation_history", DifyJsonSchema.asString(conversationHistory));
        payload.put("knowledge_context", DifyJsonSchema.asString(knowledgeContext));
        return payload;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", DifyJsonSchema.stringType());
        properties.put("conversation_id", DifyJsonSchema.stringType());
        properties.put("doctor_role", DifyJsonSchema.stringType());
        properties.put("patient_profile", DifyJsonSchema.stringType());
        properties.put("conversation_history", DifyJsonSchema.stringType());
        properties.put("knowledge_context", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> diagnosisItem = new LinkedHashMap<>();
        diagnosisItem.put("name", DifyJsonSchema.stringType());
        diagnosisItem.put("probability", DifyJsonSchema.stringType());

        Map<String, Object> suggestionProps = new LinkedHashMap<>();
        suggestionProps.put("possible_diagnoses", DifyJsonSchema.array(DifyJsonSchema.object(diagnosisItem)));
        suggestionProps.put("suggested_questions", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        suggestionProps.put("recommended_exams", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        suggestionProps.put("treatment_strategy", DifyJsonSchema.stringType());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content", DifyJsonSchema.stringType());
        properties.put("suggestion", DifyJsonSchema.object(suggestionProps));
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
        spec.put("inputExample", buildInputObject(
                "最近空腹血糖偏高，需要做什么检查？",
                "sess_xxx",
                "你是一名资深内分泌科主任医师...",
                "{\"age\":52,\"gender\":\"male\"}",
                "[用户] 医生您好...",
                "【片段1】空腹血糖≥7.0mmol/L..."
        ));
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }
}
