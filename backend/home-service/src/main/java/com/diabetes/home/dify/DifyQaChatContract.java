package com.diabetes.home.dify;

import com.diabetes.common.dify.DifyJsonSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科普问答 Dify Workflow 契约（见 docs/科普问答工作流数据契约.md）。
 * Workflow API streaming；工作流结束节点输出见 {@link #OUTPUT_FIELD_NAMES}。
 */
public final class DifyQaChatContract {

    public static final String PHASE1_DOC_TYPE = "guideline";
    public static final String KNOWLEDGE_CONTEXT_INPUT = "knowledge_context";

    /** 代码节点校验输出 */
    public static final String OUTPUT_VALID = "valid";
    public static final String OUTPUT_MESSAGE = "message";
    public static final String OUTPUT_ERROR_MESSAGE = "error_message";
    public static final String OUTPUT_ERROR_TYPE = "error_type";
    /** LLM 节点最终回答 */
    public static final String OUTPUT_TEXT = "text";

    public static final List<String> OUTPUT_FIELD_NAMES = List.of(
            OUTPUT_VALID, OUTPUT_MESSAGE, OUTPUT_ERROR_MESSAGE, OUTPUT_ERROR_TYPE, OUTPUT_TEXT
    );

    private DifyQaChatContract() {
    }

    public static Map<String, Object> buildInputs(String knowledgeContext) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(KNOWLEDGE_CONTEXT_INPUT, knowledgeContext == null ? "" : knowledgeContext);
        return inputs;
    }

    /** Workflow 开始节点：query + knowledge_context 平铺传入 inputs */
    public static Map<String, Object> buildWorkflowInputs(String query, String knowledgeContext) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query == null ? "" : query);
        inputs.put(KNOWLEDGE_CONTEXT_INPUT, knowledgeContext == null ? "" : knowledgeContext);
        return inputs;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", DifyJsonSchema.stringType());
        properties.put(KNOWLEDGE_CONTEXT_INPUT, DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(OUTPUT_VALID, DifyJsonSchema.booleanType());
        properties.put(OUTPUT_MESSAGE, DifyJsonSchema.stringType());
        properties.put(OUTPUT_ERROR_MESSAGE, DifyJsonSchema.stringType());
        properties.put(OUTPUT_ERROR_TYPE, DifyJsonSchema.stringType());
        properties.put(OUTPUT_TEXT, DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> workflowSpec(String baseUrl, String apiKey) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", "streaming");
        spec.put("inputLayout", "flat");
        spec.put("inputFieldNames", List.of("query", KNOWLEDGE_CONTEXT_INPUT));
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("outputFieldNames", OUTPUT_FIELD_NAMES);
        spec.put("outputJsonSchema", outputJsonSchema());
        spec.put("docType", PHASE1_DOC_TYPE);
        return spec;
    }
}
