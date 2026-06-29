package com.diabetes.article.dify;

import com.diabetes.common.dify.DifyJsonSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资讯个性化推荐 Dify 工作流 JSON 契约。
 */
public final class DifyArticleRecommendWorkflowContract {

    public static final String INPUT_VARIABLE_NAME = "inputs";
    public static final String OUTPUT_KEY = "recommendations";

    private DifyArticleRecommendWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String userId,
                                                       List<String> interestTags,
                                                       Map<Integer, Double> categoryWeights,
                                                       Map<String, Object> healthProfile,
                                                       Map<String, Object> riskProfile,
                                                       List<Map<String, Object>> candidateArticles) {
        Map<String, Object> userProfileObj = new LinkedHashMap<>();
        userProfileObj.put("user_id", DifyJsonSchema.asString(userId));
        userProfileObj.put("interest_tags", interestTags == null ? List.of() : interestTags);
        userProfileObj.put("category_weights", toCategoryWeightsObject(categoryWeights));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_profile", userProfileObj);
        payload.put("health_profile", DifyJsonSchema.asObject(healthProfile));
        payload.put("risk_profile", DifyJsonSchema.asObject(riskProfile));
        payload.put("candidate_articles", toSchemaCompliantCandidates(candidateArticles));
        return payload;
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> candidateItemProps = new LinkedHashMap<>();
        candidateItemProps.put("article_id", DifyJsonSchema.stringType());
        candidateItemProps.put("title", DifyJsonSchema.stringType());
        candidateItemProps.put("summary", DifyJsonSchema.stringType());
        candidateItemProps.put("category", DifyJsonSchema.stringType());
        candidateItemProps.put("tags", DifyJsonSchema.array(DifyJsonSchema.stringType()));
        candidateItemProps.put("score", DifyJsonSchema.numberType());

        Map<String, Object> recItemProps = new LinkedHashMap<>();
        recItemProps.put("article_id", DifyJsonSchema.stringType());
        recItemProps.put("rec_reason", DifyJsonSchema.stringType());

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("user_profile", DifyJsonSchema.object(Map.of(
                "user_id", DifyJsonSchema.stringType(),
                "interest_tags", DifyJsonSchema.array(DifyJsonSchema.stringType()),
                "category_weights", DifyJsonSchema.object(Map.of())
        )));
        properties.put("health_profile", DifyJsonSchema.object(Map.of(
                "height", DifyJsonSchema.numberType(),
                "weight", DifyJsonSchema.numberType(),
                "bmi", DifyJsonSchema.numberType()
        )));
        properties.put("risk_profile", DifyJsonSchema.object(Map.of(
                "riskLevel", DifyJsonSchema.stringType(),
                "riskScore", DifyJsonSchema.integerType()
        )));
        properties.put("candidate_articles", DifyJsonSchema.array(DifyJsonSchema.object(candidateItemProps)));
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> recItemProps = new LinkedHashMap<>();
        recItemProps.put("article_id", DifyJsonSchema.stringType());
        recItemProps.put("rec_reason", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(Map.of(
                "recommendations", DifyJsonSchema.array(DifyJsonSchema.object(recItemProps))
        ));
    }

    public static Map<String, Object> workflowSpec(String baseUrl, String apiKey, String inputFormat, String responseMode) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", responseMode);
        spec.put("inputFormat", inputFormat);
        spec.put("inputVariableName", INPUT_VARIABLE_NAME);
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("outputJsonSchema", outputJsonSchema());
        spec.put("inputExample", buildInputObject("usr_xxx", List.of("饮食"), Map.of(), Map.of(), Map.of(), List.of()));
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }

    private static Map<String, Object> toCategoryWeightsObject(Map<Integer, Double> weights) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (weights == null) return result;
        weights.forEach((k, v) -> result.put(String.valueOf(k), DifyJsonSchema.asNumber(v)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toSchemaCompliantCandidates(List<Map<String, Object>> candidates) {
        if (candidates == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("article_id", DifyJsonSchema.asString(c.get("article_id")));
            item.put("title", DifyJsonSchema.asString(c.get("title")));
            item.put("summary", DifyJsonSchema.asString(c.get("summary")));
            item.put("category", DifyJsonSchema.asString(c.get("category")));
            Object tags = c.get("tags");
            item.put("tags", tags instanceof List ? tags : List.of());
            item.put("score", DifyJsonSchema.asNumber(c.get("score")));
            result.add(item);
        }
        return result;
    }
}
