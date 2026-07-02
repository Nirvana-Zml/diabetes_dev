package com.diabetes.article.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyArticleRecommendWorkflowContractExtendedTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildInputObject_mapsHealthAndRiskProfiles() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("height", 170);
        health.put("weight", 70);
        health.put("bmi", 24.2);
        health.put("fastingGlucose", 6.2);
        health.put("postprandialGlucose", 8.1);
        health.put("hba1c", 6.5);
        health.put("systolicBp", 120);
        health.put("diastolicBp", 80);
        health.put("age", 45);
        health.put("gender", "male");
        health.put("diabetesType", 2);
        health.put("exerciseFreq", 3);
        health.put("dietType", "低糖");

        Map<String, Object> risk = Map.of(
                "riskLevel", "中风险",
                "riskScore", 68,
                "reportSummary", "需关注血糖",
                "factors", List.of(
                        Map.of("description", "家族史"),
                        Map.of("name", "肥胖")));

        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user1", List.of("饮食"), Map.of(2, 2.0, 3, 1.0),
                health, risk,
                List.of(Map.of("article_id", "art_1", "title", "t", "summary", "s",
                        "category", "diet", "tags", List.of("饮食"), "score", 8)),
                mapper);

        assertTrue(result.get("health_profile") instanceof String);
        assertTrue(result.get("risk_profile") instanceof String);
        String healthJson = (String) result.get("health_profile");
        assertTrue(healthJson.contains("1型"));
        assertTrue(healthJson.contains("moderate"));
    }

    @Test
    void buildInputObject_nullCandidatesAndInterestTags() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user1", null, null, Map.of(), Map.of(), null, mapper);
        assertNotNull(result);
        assertTrue(result.get("candidate_articles") instanceof String);
    }

    @Test
    void categorySlug_remainingValues() {
        assertEquals("risk", DifyArticleRecommendWorkflowContract.categorySlug("risk"));
        assertEquals("diabetes_basics", DifyArticleRecommendWorkflowContract.categorySlug("diabetes_basics"));
        assertEquals("medication", DifyArticleRecommendWorkflowContract.categorySlug("用药指导"));
        assertEquals("low", normalizeRiskViaBuild("低"));
        assertEquals("unknown", normalizeRiskViaBuild("其他"));
    }

    @Test
    void ensureStringEncodedInputs_preservesExistingJsonStrings() {
        Map<String, Object> payload = Map.of(
                "user_profile", "{\"user_id\":\"u1\"}",
                "health_profile", "{\"bmi\":24}",
                "risk_profile", "{\"risk_level\":\"high\"}",
                "candidate_articles", "[{\"article_id\":\"a1\"}]");
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.ensureStringEncodedInputs(payload, mapper);
        assertEquals("{\"user_id\":\"u1\"}", result.get("user_profile"));
        assertEquals("[{\"article_id\":\"a1\"}]", result.get("candidate_articles"));
    }

    @Test
    void workflowSpec_nullBaseUrl() {
        Map<String, Object> spec = DifyArticleRecommendWorkflowContract.workflowSpec(
                null, "key", "flat", "streaming", mapper);
        assertEquals("/v1/workflows/run", spec.get("workflowUrl"));
    }

    @Test
    void outputJsonSchema_containsErrorFields() {
        Map<String, Object> schema = DifyArticleRecommendWorkflowContract.outputJsonSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("has_error"));
        assertTrue(properties.containsKey("error_message"));
        assertTrue(properties.containsKey("source"));
    }

    @Test
    void buildInputObject_diabetesAndActivityVariants() {
        for (int type : new int[]{0, 1, 2, 3, 4, 9}) {
            Map<String, Object> health = Map.of("diabetesType", type);
            Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                    "u", List.of(), Map.of(), health, Map.of(), List.of(), mapper);
            assertNotNull(result.get("health_profile"));
        }
        for (int freq : new int[]{1, 2, 3, 4, 9}) {
            Map<String, Object> health = Map.of("activity_level", freq, "exercise_freq", freq);
            Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                    "u", List.of(), Map.of(), health, Map.of(), List.of(), mapper);
            assertNotNull(result.get("health_profile"));
        }
    }

    private String normalizeRiskViaBuild(String level) {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "u", List.of(), Map.of(), Map.of(), Map.of("riskLevel", level), List.of(), mapper);
        String riskJson = (String) result.get("risk_profile");
        return riskJson.contains("\"risk_level\":\"high\"") ? "high"
                : riskJson.contains("\"risk_level\":\"medium\"") ? "medium"
                : riskJson.contains("\"risk_level\":\"low\"") ? "low" : "unknown";
    }
}
