package com.diabetes.article.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyArticleRecommendWorkflowContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildInputObject_encodesStructuredFields() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user1",
                List.of("饮食管理"),
                Map.of(2, 3.0),
                Map.of("diabetesType", 3, "bmi", 26.4),
                Map.of("riskLevel", "high"),
                List.of(Map.of("article_id", "art_1", "title", "标题", "summary", "摘要",
                        "category", 2, "tags", List.of("饮食"), "score", 10.5)),
                mapper);

        assertNotNull(result);
        assertTrue(result.get("user_profile") instanceof String);
        assertTrue(result.get("health_profile") instanceof String);
        assertTrue(result.get("risk_profile") instanceof String);
        assertTrue(result.get("candidate_articles") instanceof String);
    }

    @Test
    void ensureStringEncodedInputs_nullAndEmpty() {
        assertEquals(Map.of(), DifyArticleRecommendWorkflowContract.ensureStringEncodedInputs(null, mapper));
        assertEquals(Map.of(), DifyArticleRecommendWorkflowContract.ensureStringEncodedInputs(Map.of(), mapper));
    }

    @Test
    void ensureStringEncodedInputs_blankStringsBecomeDefaults() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("user_profile", "");
        payload.put("health_profile", " ");
        payload.put("risk_profile", null);
        payload.put("candidate_articles", null);

        Map<String, Object> result = DifyArticleRecommendWorkflowContract.ensureStringEncodedInputs(payload, mapper);

        assertEquals("{}", result.get("user_profile"));
        assertEquals("{}", result.get("health_profile"));
        assertEquals("{}", result.get("risk_profile"));
        assertEquals("[]", result.get("candidate_articles"));
    }

    @Test
    void categorySlug_mapsKnownValues() {
        assertEquals("diet", DifyArticleRecommendWorkflowContract.categorySlug(2));
        assertEquals("diet", DifyArticleRecommendWorkflowContract.categorySlug("饮食管理"));
        assertEquals("exercise", DifyArticleRecommendWorkflowContract.categorySlug("运动"));
        assertEquals("medication", DifyArticleRecommendWorkflowContract.categorySlug(4));
        assertEquals("complications", DifyArticleRecommendWorkflowContract.categorySlug("并发症"));
        assertEquals("diabetes_basics", DifyArticleRecommendWorkflowContract.categorySlug(null));
        assertEquals("glucose", DifyArticleRecommendWorkflowContract.categorySlug("血糖监测"));
    }

    @Test
    void workflowSpec_containsExpectedKeys() {
        Map<String, Object> spec = DifyArticleRecommendWorkflowContract.workflowSpec(
                "http://dify.local", "api-key", "object", "blocking", mapper);

        assertEquals("http://dify.local/v1/workflows/run", spec.get("workflowUrl"));
        assertEquals("api-key", spec.get("apiKey"));
        assertEquals("object", spec.get("inputFormat"));
        assertEquals("blocking", spec.get("responseMode"));
        assertEquals(DifyArticleRecommendWorkflowContract.INPUT_VARIABLE_NAME, spec.get("inputVariableName"));
        assertNotNull(spec.get("inputExample"));
        assertNotNull(spec.get("outputJsonSchema"));
    }

    @Test
    void inputAndOutputJsonSchema_areObjects() {
        assertEquals("object", DifyArticleRecommendWorkflowContract.inputJsonSchema().get("type"));
        assertEquals("object", DifyArticleRecommendWorkflowContract.outputJsonSchema().get("type"));
    }
}
