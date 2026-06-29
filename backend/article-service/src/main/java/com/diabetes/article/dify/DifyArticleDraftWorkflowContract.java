package com.diabetes.article.dify;

import com.diabetes.common.dify.DifyJsonSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资讯 AI 初稿生成 Dify 工作流 JSON 契约。
 *
 * <p>开始节点为平铺 String 变量 {@code topic}、{@code keywords}；结束节点输出 {@code article_draft}。</p>
 */
public final class DifyArticleDraftWorkflowContract {

    public static final String OUTPUT_KEY = "article_draft";

    private DifyArticleDraftWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String topic, String keywords) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topic", topic == null ? "" : topic.trim());
        payload.put("keywords", keywords == null ? "" : keywords.trim());
        return payload;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("topic", DifyJsonSchema.stringType());
        properties.put("keywords", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> articleDraftJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", DifyJsonSchema.stringType());
        properties.put("summary", DifyJsonSchema.stringType());
        properties.put("content", DifyJsonSchema.stringType());
        properties.put("tags", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        return DifyJsonSchema.rootObject(Map.of(OUTPUT_KEY, articleDraftJsonSchema()));
    }

    public static Map<String, Object> workflowConfig(String baseUrl, String apiKey, String workflowUrlOverride) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        String workflowUrl = workflowUrlOverride != null && !workflowUrlOverride.isBlank()
                ? workflowUrlOverride
                : normalizedBase + "/v1/workflows/run";

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("workflowUrl", workflowUrl);
        config.put("apiKey", apiKey == null ? "" : apiKey);
        config.put("responseMode", "streaming");
        config.put("inputFormat", "flat");
        config.put("inputVariables", List.of("topic", "keywords"));
        config.put("inputJsonSchema", inputJsonSchema());
        config.put("outputJsonSchema", outputJsonSchema());
        config.put("outputKey", OUTPUT_KEY);
        config.put("inputs", Map.of(
                "topic", "string(主题)",
                "keywords", "string(关键词)"
        ));
        config.put("inputExample", buildInputObject("糖尿病饮食管理", "糖尿病,饮食,GI值,血糖控制,低糖"));
        return config;
    }
}
