package com.diabetes.home.dify;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 科普问答 Dify Chatbot 契约（见 docs/科普问答工作流数据契约.md）。
 */
public final class DifyQaChatContract {

    public static final String PHASE1_DOC_TYPE = "guideline";
    public static final String KNOWLEDGE_CONTEXT_INPUT = "knowledge_context";

    private DifyQaChatContract() {
    }

    public static Map<String, Object> buildInputs(String knowledgeContext) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(KNOWLEDGE_CONTEXT_INPUT, knowledgeContext == null ? "" : knowledgeContext);
        return inputs;
    }

    public static Map<String, Object> workflowSpec(String baseUrl, String apiKey) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/chat-messages");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", "streaming");
        spec.put("inputVariableName", KNOWLEDGE_CONTEXT_INPUT);
        spec.put("docType", PHASE1_DOC_TYPE);
        return spec;
    }
}
