package com.diabetes.article.service;

import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleRecommendServiceAdvancedTest {

    private final RecommendMapper recommendMapper = mock(RecommendMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final HealthServiceClient healthServiceClient = mock(HealthServiceClient.class);
    private final DifyClient difyClient = mock(DifyClient.class);
    private final RecommendProperties properties = new RecommendProperties();
    private final MilvusArticleSearchService milvusArticleSearchService = mock(MilvusArticleSearchService.class);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(minioStorageService.buildArticleCoverUrl(anyString())).thenReturn("http://minio/cover.jpg");
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
    }

    private ArticleRecommendService serviceWithKey(String apiKey, String inputVar) {
        return new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", apiKey, inputVar, "object", "blocking", "internal-key");
    }

    @Test
    void recommend_phase2BoostsCollaborativeArticles() {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate c1 = candidate("art_1", 2, 10, 0);
        ArticleCandidate c2 = candidate("art_2", 2, 5, 0);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1, c2));
        when(recommendMapper.findTagsByArticleId(anyString())).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of("art_read"));
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(eq("u1"), anyList(), eq(30)))
                .thenReturn(List.of(Map.of("articleId", "art_2", "coCount", 2)));
        when(recommendMapper.findCoFavoriteArticles(eq("u1"), anyList(), eq(30))).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertEquals("personalized", result.get("strategy"));
        assertTrue(((Number) result.get("phase")).intValue() >= 2);
    }

    @Test
    void recommend_phase3UsesMilvusHits() {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate c1 = candidate("art_1", 2, 10, 0);
        c1.setTextFingerprint("糖尿病 饮食 控制");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of("art_fav"));
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of("art_fav"));
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(anyString(), anyList(), anyInt()))
                .thenReturn(Map.of("art_1", 0.8));

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertTrue(((Number) result.get("phase")).intValue() >= 3);
    }

    @Test
    void recommend_phase3FallsBackToLocalJaccard() {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate read = candidate("art_read", 2, 10, 0);
        read.setTextFingerprint("糖尿病 饮食 管理 控制");
        ArticleCandidate target = candidate("art_1", 2, 8, 0);
        target.setTextFingerprint("糖尿病 饮食 管理 血糖");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(read, target));
        when(recommendMapper.findTagsByArticleId("art_read")).thenReturn(List.of("饮食"));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of("art_read"));
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertTrue(((Number) result.get("phase")).intValue() >= 3);
    }

    @Test
    void recommend_phase4DifyRerankSuccess() throws Exception {
        properties.setPhase4DifyEnabled(true);
        properties.setDifyTopN(5);
        ArticleRecommendService service = serviceWithKey("dify-key", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate c1 = candidate("art_1", 2, 10, 0);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"recommendations":[
                  {"article_id":"art_1","rec_reason":"AI 精选"}
                ]}}}
                """);
        when(difyClient.runWorkflowBlocking(eq("dify-key"), eq("u1"), any(), eq("blocking"))).thenReturn(response);

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertEquals(4, result.get("phase"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articles = (List<Map<String, Object>>) result.get("articles");
        assertEquals("AI 精选", articles.get(0).get("recReason"));
    }

    @Test
    void recommend_phase4DifyFailureDegradesGracefully() throws Exception {
        properties.setPhase4DifyEnabled(true);
        ArticleRecommendService service = serviceWithKey("dify-key", "inputs");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        stubSingleCandidate("u1");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("dify down"));

        assertDoesNotThrow(() -> service.recommend("u1", 1, 10));
    }

    @Test
    void recommend_phase1DisabledAndCoFavoriteBoost() {
        properties.setPhase1Enabled(false);
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate c1 = candidate("art_1", 2, 1, 0);
        ArticleCandidate c2 = candidate("art_2", 2, 1, 0);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1, c2));
        when(recommendMapper.findTagsByArticleId(anyString())).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(eq("u1"), anyList(), eq(30)))
                .thenReturn(List.of(Map.of("articleId", "art_2", "coCount", 3)));
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertEquals(2, result.get("total"));
    }

    @Test
    void recommend_profileWeightsAndPersistedEdgeCases() {
        properties.setPhase2Enabled(false);
        properties.setPhase3Enabled(false);
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate c1 = candidate("art_1", 2, 100, 2);
        c1.setCoverImageId("https://cdn/cover.jpg");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of("art_1"));
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of("art_1"));
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of(Map.of("category", 2, "cnt", 5)));
        when(healthServiceClient.getLatestHealthProfile("u1", "internal-key"))
                .thenReturn(Map.of("diabetesType", 1, "bmi", 25));
        when(healthServiceClient.getLatestRiskAssessment("u1", "internal-key"))
                .thenReturn(Map.of("riskLevel", "medium", "riskScore", 50,
                        "factors", List.of(Map.of("name", "肥胖", "description", "BMI偏高"))));

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertEquals(1, result.get("total"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("articleId", "art_1");
        row.put("title", "标题");
        row.put("summary", "摘要");
        row.put("category", 2);
        row.put("viewCount", 10);
        row.put("recScore", 1.2);
        row.put("recReason", "理由");
        row.put("recPhase", 4);
        when(recommendMapper.findActiveRecommendations("u2")).thenReturn(List.of(row));
        when(valueOps.get(contains("u2"))).thenReturn(null);
        Map<String, Object> persisted = service.recommend("u2", 1, 10);
        assertEquals(4, persisted.get("phase"));
    }

    @Test
    void popularRecommend_cacheHitAndFreshnessScores() throws Exception {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        Map<String, Object> cached = Map.of("articles", List.of(), "total", 0, "strategy", "popular", "phase", 1);
        when(valueOps.get(contains("popular"))).thenReturn(objectMapper.writeValueAsString(cached));
        assertEquals("popular", service.popularRecommend(1, 10).get("strategy"));
    }

    @Test
    void related_withUserAppliesPhase2() {
        properties.setPhase4DifyEnabled(false);
        ArticleCandidate base = candidate("art_1", 2, 10, 0);
        base.setTags(List.of("饮食"));
        ArticleCandidate other = candidate("art_2", 3, 8, 0);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(base));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRelatedCandidates("art_1", 2, 15)).thenReturn(List.of(other));
        when(recommendMapper.findTagsByArticleId("art_2")).thenReturn(List.of("运动"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("articleId", "art_2", "coCount", 2)));

        ArticleRecommendService service = serviceWithKey("", "flat");
        Map<String, Object> result = service.related("art_1", "u1", 5);
        assertEquals("related", result.get("strategy"));
    }

    @Test
    void recordReadUsesDefaultSourceAndInvalidateCacheFailure() {
        ArticleRecommendService service = serviceWithKey("", "flat");
        service.recordRead("u1", "art_1", null, null);
        verify(recommendMapper).upsertUserRead(anyString(), eq("u1"), eq("art_1"), isNull(), eq("detail"));

        when(redis.keys(anyString())).thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> service.invalidateUserRecommendCache("u1"));
    }

    @Test
    void recommend_readCacheFailureAndExistingFingerprint() {
        properties.setPhase2Enabled(false);
        properties.setPhase3Enabled(false);
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = serviceWithKey("", "flat");
        when(valueOps.get(anyString())).thenReturn("{bad");
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate c1 = candidate("art_1", 2, 10, 0);
        c1.setTextFingerprint("existing-fp");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());

        assertEquals(1, service.recommend("u1", 1, 10).get("total"));
        verify(recommendMapper, never()).upsertEmbedding(eq("art_1"), anyString());
    }

    @Test
    void recommend_parseDifyFromTextOutput() throws Exception {
        properties.setPhase4DifyEnabled(true);
        ArticleRecommendService service = serviceWithKey("dify-key", "flat");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        stubSingleCandidate("u1");

        JsonNode response = objectMapper.readTree("""
                {"data":{"outputs":{"text":"{\\"recommendations\\":[{\\"article_id\\":\\"art_1\\",\\"reason\\":\\"文本解析\\"}]}"}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(response);

        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertEquals(4, result.get("phase"));
    }

    private void stubSingleCandidate(String userId) {
        ArticleCandidate c1 = candidate("art_1", 2, 10, 0);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds(userId, 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds(userId)).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser(userId, 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
    }

    private static ArticleCandidate candidate(String id, int category, int views, int favorites) {
        ArticleCandidate candidate = new ArticleCandidate();
        candidate.setArticleId(id);
        candidate.setTitle("标题-" + id);
        candidate.setSummary("摘要-" + id);
        candidate.setCategory(category);
        candidate.setViewCount(views);
        candidate.setFavoriteCount(favorites);
        candidate.setPublishedAt(LocalDateTime.now().minusDays(3));
        return candidate;
    }
}
