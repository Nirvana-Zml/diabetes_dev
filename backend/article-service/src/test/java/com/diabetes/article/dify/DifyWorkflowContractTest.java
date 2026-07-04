package com.diabetes.article.dify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DifyWorkflowContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testBuildInputObjectWithNullInterestTags() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String userProfile = (String) result.get("user_profile");
        assertNotNull(userProfile);
        assertTrue(userProfile.contains("\"interest_tags\":[]"));
    }

    @Test
    void testBuildInputObjectWithNullCategoryWeights() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                null,
                Map.of(),
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String userProfile = (String) result.get("user_profile");
        assertNotNull(userProfile);
        assertTrue(userProfile.contains("\"category_weights\":{}"));
    }

    @Test
    void testBuildInputObjectWithNullCandidates() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                mapper
        );
        assertNotNull(result);
        String candidates = (String) result.get("candidate_articles");
        assertNotNull(candidates);
        assertEquals("[]", candidates);
    }

    @Test
    void testBuildInputObjectWithNonListTags() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(Map.of(
                        "article_id", "art_01",
                        "title", "标题",
                        "summary", "摘要",
                        "category", 1,
                        "tags", "not_a_list",
                        "score", 1.0
                )),
                mapper
        );
        assertNotNull(result);
        String candidates = (String) result.get("candidate_articles");
        assertNotNull(candidates);
        assertTrue(candidates.contains("\"article_id\":\"art_01\""));
        assertTrue(candidates.contains("\"tags\":[]"));
    }

    @Test
    void testWorkflowSpecWithNullBaseUrl() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.workflowSpec(
                null,
                "test-api-key",
                "object",
                "blocking",
                mapper
        );
        assertNotNull(result);
        assertEquals("/v1/workflows/run", result.get("workflowUrl"));
    }

    @Test
    void testWorkflowSpecWithBaseUrlEndingSlash() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.workflowSpec(
                "http://localhost:8080/",
                "test-api-key",
                "object",
                "blocking",
                mapper
        );
        assertNotNull(result);
        assertEquals("http://localhost:8080/v1/workflows/run", result.get("workflowUrl"));
    }

    @Test
    void testBuildInputObjectWithNullHealthProfile() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                null,
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String healthProfile = (String) result.get("health_profile");
        assertNotNull(healthProfile);
        assertTrue(healthProfile.contains("\"user_id\":\"user_01\""));
    }

    @Test
    void testBuildInputObjectWithNullRiskProfile() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                null,
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"user_id\":\"user_01\""));
    }

    @Test
    void testBuildInputObjectWithNullUserId() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                null,
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String userProfile = (String) result.get("user_profile");
        assertNotNull(userProfile);
        assertTrue(userProfile.contains("\"user_id\":\"\""));
    }

    @Test
    void testInputJsonSchema() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.inputJsonSchema();
        assertNotNull(result);
    }

    @Test
    void testOutputJsonSchema() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.outputJsonSchema();
        assertNotNull(result);
    }

    @Test
    void testBuildInputObjectWithValidData() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of("tag1", "tag2"),
                Map.of(1, 0.5, 2, 0.3),
                Map.of("height", 170, "weight", 70),
                Map.of("riskLevel", "high"),
                List.of(Map.of("article_id", "art_01", "title", "标题")),
                mapper
        );
        assertNotNull(result);
    }

    @Test
    void testCategorySlugWithNull() {
        assertEquals("diabetes_basics", DifyArticleRecommendWorkflowContract.categorySlug(null));
    }

    @Test
    void testCategorySlugWithNumber() {
        assertEquals("diabetes_basics", DifyArticleRecommendWorkflowContract.categorySlug(1));
        assertEquals("diet", DifyArticleRecommendWorkflowContract.categorySlug(2));
        assertEquals("exercise", DifyArticleRecommendWorkflowContract.categorySlug(3));
        assertEquals("medication", DifyArticleRecommendWorkflowContract.categorySlug(4));
        assertEquals("complications", DifyArticleRecommendWorkflowContract.categorySlug(5));
        assertEquals("diabetes_basics", DifyArticleRecommendWorkflowContract.categorySlug(99));
    }

    @Test
    void testCategorySlugWithString() {
        assertEquals("diet", DifyArticleRecommendWorkflowContract.categorySlug("diet"));
        assertEquals("diet", DifyArticleRecommendWorkflowContract.categorySlug("饮食"));
        assertEquals("diet", DifyArticleRecommendWorkflowContract.categorySlug("饮食管理"));
        assertEquals("exercise", DifyArticleRecommendWorkflowContract.categorySlug("运动"));
        assertEquals("exercise", DifyArticleRecommendWorkflowContract.categorySlug("exercise"));
        assertEquals("medication", DifyArticleRecommendWorkflowContract.categorySlug("用药"));
        assertEquals("complications", DifyArticleRecommendWorkflowContract.categorySlug("并发症"));
        assertEquals("glucose", DifyArticleRecommendWorkflowContract.categorySlug("血糖监测"));
        assertEquals("risk", DifyArticleRecommendWorkflowContract.categorySlug("risk"));
        assertEquals("diabetes_basics", DifyArticleRecommendWorkflowContract.categorySlug("unknown"));
    }

    @Test
    void testBuildInputObjectWithHealthProfileFields() {
        Map<String, Object> healthProfile = new java.util.LinkedHashMap<>();
        healthProfile.put("height", 175);
        healthProfile.put("weight", 70);
        healthProfile.put("bmi", 22.86);
        healthProfile.put("fastingGlucose", 6.1);
        healthProfile.put("postprandialGlucose", 8.5);
        healthProfile.put("hba1c", 6.5);
        healthProfile.put("systolicBp", 120);
        healthProfile.put("diastolicBp", 80);
        healthProfile.put("age", 50);
        healthProfile.put("gender", "男");
        healthProfile.put("diabetesType", 3);
        healthProfile.put("exerciseFreq", 3);
        healthProfile.put("dietType", "低糖饮食");

        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                healthProfile,
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String healthProfileStr = (String) result.get("health_profile");
        assertNotNull(healthProfileStr);
        assertTrue(healthProfileStr.contains("\"user_id\":\"user_01\""));
        assertTrue(healthProfileStr.contains("\"height\":175"));
        assertTrue(healthProfileStr.contains("\"diabetes_type\":\"2型\""));
        assertTrue(healthProfileStr.contains("\"activity_level\":\"moderate\""));
    }

    @Test
    void testBuildInputObjectWithRiskProfileFields() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "riskLevel", "高",
                        "riskScore", 85,
                        "factors", List.of(
                                Map.of("description", "高血压"),
                                Map.of("name", "高血脂"),
                                "肥胖"
                        ),
                        "reportSummary", "风险评估报告摘要"
                ),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"risk_level\":\"high\""));
        assertTrue(riskProfile.contains("\"risk_score\":85"));
        assertTrue(riskProfile.contains("\"risk_factors\""));
        assertTrue(riskProfile.contains("\"primary_risk\":\"高血压\""));
        assertTrue(riskProfile.contains("\"secondary_risk\":\"高血脂\""));
        assertTrue(riskProfile.contains("\"report_summary\":\"风险评估报告摘要\""));
    }

    @Test
    void testBuildInputObjectWithRiskProfileNullFactors() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of("riskLevel", "低", "riskScore", 20),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"risk_level\":\"low\""));
    }

    @Test
    void testBuildInputObjectWithCategoryWeights() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(2, 5.0, 3, 3.0, 4, 2.0),
                Map.of(),
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String userProfile = (String) result.get("user_profile");
        assertNotNull(userProfile);
        assertTrue(userProfile.contains("\"category_weights\""));
    }

    @Test
    void testEnsureStringEncodedInputsWithNullPayload() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.ensureStringEncodedInputs(null, mapper);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testEnsureStringEncodedInputsWithEmptyPayload() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.ensureStringEncodedInputs(Map.of(), mapper);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildInputObjectWithCandidateArticles() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(
                        Map.of("article_id", "art_01", "title", "饮食指南", "category", "diet", "score", 0.9),
                        Map.of("article_id", "art_02", "title", "运动建议", "category", 3, "score", 0.8)
                ),
                mapper
        );
        assertNotNull(result);
        String candidates = (String) result.get("candidate_articles");
        assertNotNull(candidates);
        assertTrue(candidates.contains("\"article_id\":\"art_01\""));
        assertTrue(candidates.contains("\"article_id\":\"art_02\""));
    }

    @Test
    void testCategorySlugWithRiskStrings() {
        assertEquals("risk", DifyArticleRecommendWorkflowContract.categorySlug("risk"));
        assertEquals("glucose", DifyArticleRecommendWorkflowContract.categorySlug("glucose"));
        assertEquals("glucose", DifyArticleRecommendWorkflowContract.categorySlug("血糖监测"));
    }

    @Test
    void testBuildInputObjectWithZeroCategoryWeights() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(1, 0.0, 2, -1.0),
                Map.of(),
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String userProfile = (String) result.get("user_profile");
        assertNotNull(userProfile);
        assertTrue(userProfile.contains("\"category_weights\":{}"));
    }

    @Test
    void testBuildInputObjectWithHealthProfileNullValues() {
        Map<String, Object> healthProfile = new java.util.LinkedHashMap<>();
        healthProfile.put("height", null);
        healthProfile.put("weight", 70);
        healthProfile.put("age", null);
        healthProfile.put("gender", "女");
        healthProfile.put("diabetesType", 1);
        healthProfile.put("exerciseFreq", 1);

        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                healthProfile,
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String healthProfileStr = (String) result.get("health_profile");
        assertNotNull(healthProfileStr);
        assertTrue(healthProfileStr.contains("\"diabetes_type\":\"前期\""));
        assertTrue(healthProfileStr.contains("\"activity_level\":\"sedentary\""));
    }

    @Test
    void testBuildInputObjectWithHealthProfileStringValues() {
        Map<String, Object> healthProfile = new java.util.LinkedHashMap<>();
        healthProfile.put("diabetesType", "2型");
        healthProfile.put("exerciseFreq", "moderate");
        healthProfile.put("dietType", "balanced");

        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                healthProfile,
                Map.of(),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String healthProfileStr = (String) result.get("health_profile");
        assertNotNull(healthProfileStr);
        assertTrue(healthProfileStr.contains("\"diabetes_type\":\"2型\""));
        assertTrue(healthProfileStr.contains("\"activity_level\":\"moderate\""));
    }

    @Test
    void testBuildInputObjectWithRiskProfileMediumLevel() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of("riskLevel", "中", "riskScore", 50),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"risk_level\":\"medium\""));
    }

    @Test
    void testBuildInputObjectWithRiskProfileLowLevel() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of("riskLevel", "low", "riskScore", 25),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"risk_level\":\"low\""));
    }

    @Test
    void testBuildInputObjectWithRiskProfileUnknownLevel() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of("riskLevel", "unknown", "riskScore", 0),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"risk_level\":\"unknown\""));
    }

    @Test
    void testBuildInputObjectWithRiskProfileOnlyOneFactor() {
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01",
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "riskLevel", "high",
                        "factors", List.of(Map.of("description", "高血压"))
                ),
                List.of(),
                mapper
        );
        assertNotNull(result);
        String riskProfile = (String) result.get("risk_profile");
        assertNotNull(riskProfile);
        assertTrue(riskProfile.contains("\"primary_risk\":\"高血压\""));
    }

    @Test
    void testBuildInputObjectWithHealthProfileOtherDiabetesTypes() {
        Map<String, Object> healthProfile = new java.util.LinkedHashMap<>();
        healthProfile.put("diabetesType", 2);
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertNotNull(result);
        assertTrue(((String) result.get("health_profile")).contains("\"diabetes_type\":\"1型\""));

        healthProfile.put("diabetesType", 4);
        result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertTrue(((String) result.get("health_profile")).contains("\"diabetes_type\":\"妊娠\""));

        healthProfile.put("diabetesType", 0);
        result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertTrue(((String) result.get("health_profile")).contains("\"diabetes_type\":\"无\""));

        healthProfile.put("diabetesType", 99);
        result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertTrue(((String) result.get("health_profile")).contains("\"diabetes_type\":\"未知\""));
    }

    @Test
    void testBuildInputObjectWithHealthProfileActivityLevels() {
        Map<String, Object> healthProfile = new java.util.LinkedHashMap<>();
        healthProfile.put("exerciseFreq", 1);
        Map<String, Object> result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertNotNull(result);
        assertTrue(((String) result.get("health_profile")).contains("\"activity_level\":\"sedentary\""));

        healthProfile.put("exerciseFreq", 2);
        result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertTrue(((String) result.get("health_profile")).contains("\"activity_level\":\"light\""));

        healthProfile.put("exerciseFreq", 4);
        result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertTrue(((String) result.get("health_profile")).contains("\"activity_level\":\"active\""));

        healthProfile.put("exerciseFreq", 99);
        result = DifyArticleRecommendWorkflowContract.buildInputObject(
                "user_01", List.of(), Map.of(), healthProfile, Map.of(), List.of(), mapper);
        assertTrue(((String) result.get("health_profile")).contains("\"activity_level\":\"unknown\""));
    }

    @Test
    void testDraftBuildInputObjectWithNullTopic() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject(null, "keywords");
        assertNotNull(result);
        assertEquals("", result.get("topic"));
        assertEquals("keywords", result.get("keywords"));
    }

    @Test
    void testDraftBuildInputObjectWithNullKeywords() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject("topic", null);
        assertNotNull(result);
        assertEquals("topic", result.get("topic"));
        assertEquals("", result.get("keywords"));
    }

    @Test
    void testDraftBuildInputObjectWithBlankTopic() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject("  topic  ", "keywords");
        assertNotNull(result);
        assertEquals("topic", result.get("topic"));
    }

    @Test
    void testDraftBuildInputObjectWithBlankKeywords() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject("topic", "  keywords  ");
        assertNotNull(result);
        assertEquals("keywords", result.get("keywords"));
    }

    @Test
    void testDraftWorkflowConfigWithNullBaseUrl() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.workflowConfig(
                null,
                "test-api-key",
                null
        );
        assertNotNull(result);
        assertEquals("/v1/workflows/run", result.get("workflowUrl"));
    }

    @Test
    void testDraftWorkflowConfigWithWorkflowUrlOverride() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.workflowConfig(
                "http://localhost:8080",
                "test-api-key",
                "http://custom-url.com/workflow"
        );
        assertNotNull(result);
        assertEquals("http://custom-url.com/workflow", result.get("workflowUrl"));
    }

    @Test
    void testDraftWorkflowConfigWithBlankWorkflowUrlOverride() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.workflowConfig(
                "http://localhost:8080",
                "test-api-key",
                "   "
        );
        assertNotNull(result);
        assertEquals("http://localhost:8080/v1/workflows/run", result.get("workflowUrl"));
    }

    @Test
    void testDraftWorkflowConfigWithNullApiKey() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.workflowConfig(
                "http://localhost:8080",
                null,
                null
        );
        assertNotNull(result);
        assertEquals("", result.get("apiKey"));
    }

    @Test
    void testDraftInputJsonSchema() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.inputJsonSchema();
        assertNotNull(result);
    }

    @Test
    void testDraftOutputJsonSchema() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.outputJsonSchema();
        assertNotNull(result);
    }

    @Test
    void testDraftArticleDraftJsonSchema() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.articleDraftJsonSchema();
        assertNotNull(result);
    }

    @Test
    void testDraftBuildInputObjectWithValidData() {
        Map<String, Object> result = DifyArticleDraftWorkflowContract.buildInputObject("主题", "关键词");
        assertNotNull(result);
        assertEquals("主题", result.get("topic"));
        assertEquals("关键词", result.get("keywords"));
    }
}
