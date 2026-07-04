package com.diabetes.article.service;

import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.entity.ScoredArticle;
import com.diabetes.article.testutil.Stubs;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.common.util.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleRecommendServiceTest {

    private ArticleRecommendService recommendService;
    private Stubs.RecommendMapperStub recommendMapper;
    private RedisOperations<String, String> redisTemplate;
    private RecommendProperties properties;
    private MilvusArticleSearchService milvusArticleSearchService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        recommendMapper = new Stubs.RecommendMapperStub();
        redisTemplate = mock(RedisOperations.class);
        objectMapper = new ObjectMapper();
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        DifyClient difyClient = mock(DifyClient.class);
        milvusArticleSearchService = mock(MilvusArticleSearchService.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        properties = new RecommendProperties();
        properties.setPhase1Enabled(true);
        properties.setPhase2Enabled(true);
        properties.setPhase3Enabled(true);
        properties.setPhase4DifyEnabled(false);
        properties.setMilvusEnabled(true);
        properties.setCandidateLimit(100);
        properties.setCacheTtlMinutes(30);
        properties.setPhase2MinCoReaders(1);
        properties.setDifyTopN(10);

        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("/images/articles/test.jpg");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        doNothing().when(valueOps).set(any(), any());
        doNothing().when(valueOps).set(any(), any(), any());
        when(redisTemplate.keys(any())).thenReturn(Collections.emptySet());
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2, "bmi", 25.0));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt())).thenReturn(Map.of());

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                healthService,
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );
    }

    @Test
    void testPopularRecommend() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "热门文章2", 2));

        Map<String, Object> result = recommendService.popularRecommend(5, 5);
        assertNotNull(result);
        assertEquals("popular", result.get("strategy"));
        assertEquals(1, result.get("phase"));
    }

    @Test
    void testRelated() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "相关文章2", 1));
        recommendMapper.addCandidate(createCandidate("art_04", "无关文章", 2));

        recommendMapper.addTags("art_01", List.of("标签A", "标签B"));
        recommendMapper.addTags("art_02", List.of("标签A", "标签C"));
        recommendMapper.addTags("art_03", List.of("标签D"));

        Map<String, Object> result = recommendService.related("art_01", null, 2);
        assertNotNull(result);
        assertEquals("related", result.get("strategy"));
        assertEquals(2, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testRelatedArticleNotFound() {
        assertThrows(com.diabetes.common.exception.BusinessException.class,
                () -> recommendService.related("not_exist", null, 2));
    }

    @Test
    void testRecordRead() {
        assertDoesNotThrow(() -> recommendService.recordRead("user_01", "art_01", 100, "manual"));
    }

    @Test
    void testRecordReadDurationZero() {
        assertDoesNotThrow(() -> recommendService.recordRead("user_01", "art_01", 0, "manual"));
    }

    @Test
    void testRecordReadNullSource() {
        assertDoesNotThrow(() -> recommendService.recordRead("user_01", "art_01", 100, null));
    }

    @Test
    void testRecordReadDurationLessThanZero() {
        assertDoesNotThrow(() -> recommendService.recordRead("user_01", "art_01", -10, "manual"));
    }

    @Test
    void testRecordReadNullUserId() {
        assertThrows(com.diabetes.common.exception.BusinessException.class,
                () -> recommendService.recordRead(null, "art_01", 100, "manual"));
    }

    @Test
    void testRecordReadBlankUserId() {
        assertThrows(com.diabetes.common.exception.BusinessException.class,
                () -> recommendService.recordRead("", "art_01", 100, "manual"));
    }

    @Test
    void testRecommendWithNullUserId() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));

        Map<String, Object> result = recommendService.recommend(null, 1, 10);
        assertNotNull(result);
        assertEquals("popular", result.get("strategy"));
    }

    @Test
    void testRecommendWithEmptyUserId() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));

        Map<String, Object> result = recommendService.recommend("", 1, 10);
        assertNotNull(result);
        assertEquals("popular", result.get("strategy"));
    }

    @Test
    void testRecommendWithUserId() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
        assertEquals("personalized", result.get("strategy"));
    }

    @Test
    void testRecommendWithCachedResult() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));
        String cachedJson = "{\"articles\":[],\"total\":0,\"strategy\":\"popular\",\"phase\":1}";
        when(redisTemplate.opsForValue().get(any())).thenReturn(cachedJson);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithPersistedRecommendations() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));
        List<Map<String, Object>> persisted = List.of(Map.of(
                "articleId", "art_01", "title", "热门文章", "summary", "摘要",
                "category", 1, "viewCount", 100, "recScore", 10.0,
                "recReason", "为您推荐", "recPhase", 4
        ));
        recommendMapper.setActiveRecommendations("user_01", persisted);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
        assertEquals("personalized", result.get("strategy"));
        assertEquals(4, result.get("phase"));
    }

    @Test
    void testRecommendWithCategoryWeights() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 3));

        List<Map<String, Object>> categoryWeights = List.of(
                Map.of("category", 2, "cnt", 5.0)
        );
        recommendMapper.setUserCategoryWeights("user_01", categoryWeights);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
        assertEquals("personalized", result.get("strategy"));
    }

    @Test
    void testRecommendWithFavoriteIds() {
        recommendMapper.addCandidate(createCandidate("art_01", "已收藏文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "未收藏文章", 2));

        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithRecentReadIds() {
        recommendMapper.addCandidate(createCandidate("art_01", "已读文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "未读文章", 2));

        recommendMapper.setUserRecentReads("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithCoReadArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 2));

        List<Map<String, Object>> coRead = List.of(
                Map.of("articleId", "art_02", "coCount", 5.0)
        );
        recommendMapper.setCoReadArticles("user_01", coRead);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithCoFavoriteArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 2));

        List<Map<String, Object>> coFavorite = List.of(
                Map.of("articleId", "art_02", "coCount", 3.0)
        );
        recommendMapper.setCoFavoriteArticles("user_01", coFavorite);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPhase2Disabled() {
        properties.setPhase2Enabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPhase3EnabledMilvusAvailable() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt())).thenReturn(Map.of("art_01", 0.8));

        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPhase4DifyEnabled() {
        properties.setPhase4DifyEnabled(true);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode outputs = objectMapper.createObjectNode();
        ArrayNode recommendations = objectMapper.createArrayNode();
        ObjectNode rec1 = objectMapper.createObjectNode();
        rec1.put("article_id", "art_01");
        rec1.put("rec_reason", "AI推荐");
        recommendations.add(rec1);
        outputs.set("recommendations", recommendations);
        response.set("data", objectMapper.createObjectNode().set("outputs", outputs));

        DifyClient difyClient = mock(DifyClient.class);
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(response);

        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPhase4DifyFailed() {
        properties.setPhase4DifyEnabled(true);

        DifyClient difyClient = mock(DifyClient.class);
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Dify error"));

        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPhase4DifyEmptyResponse() {
        properties.setPhase4DifyEnabled(true);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();

        DifyClient difyClient = mock(DifyClient.class);
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(response);

        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPhase1Disabled() {
        properties.setPhase1Enabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithUserId() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签A"));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithUserIdPhase2Enabled() {
        properties.setPhase2Enabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签A"));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testGetDifyWorkflowSpec() {
        Map<String, Object> result = recommendService.getDifyWorkflowSpec();
        assertNotNull(result);
    }

    @Test
    void testInvalidateUserRecommendCache() {
        assertDoesNotThrow(() -> recommendService.invalidateUserRecommendCache("user_01"));
    }

    @Test
    void testInvalidateUserRecommendCacheNullUserId() {
        assertDoesNotThrow(() -> recommendService.invalidateUserRecommendCache(null));
    }

    @Test
    void testInvalidateUserRecommendCacheBlankUserId() {
        assertDoesNotThrow(() -> recommendService.invalidateUserRecommendCache(""));
    }

    @Test
    void testPopularRecommendWithCache() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));
        String cachedJson = "{\"articles\":[],\"total\":0,\"strategy\":\"popular\",\"phase\":1}";
        when(redisTemplate.opsForValue().get(any())).thenReturn(cachedJson);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendPagination() {
        for (int i = 1; i <= 15; i++) {
            recommendMapper.addCandidate(createCandidate("art_" + i, "文章" + i, 1));
        }

        Map<String, Object> page1 = recommendService.popularRecommend(1, 10);
        assertEquals(10, ((List<?>) page1.get("articles")).size());

        Map<String, Object> page2 = recommendService.popularRecommend(2, 10);
        assertEquals(5, ((List<?>) page2.get("articles")).size());
    }

    @Test
    void testRelatedWithNoTags() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));

        Map<String, Object> result = recommendService.related("art_01", null, 2);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithHighRiskProfile() {
        recommendMapper.addCandidate(createCandidate("art_01", "并发症文章", 5));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "high"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithHighBmi() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 3));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2, "bmi", 26.0));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithType1Diabetes() {
        recommendMapper.addCandidate(createCandidate("art_01", "用药文章", 4));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 1));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithType4Diabetes() {
        recommendMapper.addCandidate(createCandidate("art_01", "并发症文章", 5));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 4));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithUnknownDiabetesType() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 99));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithNullRiskLevel() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of());

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithEmptyInterestText() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testPopularRecommendNoCandidates() {
        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
        assertEquals(0, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testRecommendNoCandidates() {
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
        assertEquals(0, ((List<?>) result.get("articles")).size());
    }

    private ArticleCandidate createCandidate(String id, String title, int category) {
        return Stubs.createCandidate(id, title, category);
    }

    @Test
    void testPopularRecommendWithZeroViewCount() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setViewCount(0);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithCacheWriteFailure() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        doThrow(new RuntimeException("Redis error")).when(valueOps).set(any(), any(), any());

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithCacheReadFailure() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        when(redisTemplate.opsForValue().get(any())).thenThrow(new RuntimeException("Redis error"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRelatedArticleNotPublished() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签A"));

        Map<String, Object> result = recommendService.related("art_01", null, 2);
        assertNotNull(result);
    }

    @Test
    void testResolveCoverImageHttpUrl() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setCoverImageId("https://example.com/cover.jpg");
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testResolveCoverImageHttpUrlPrefix() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setCoverImageId("http://example.com/cover.jpg");
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testFreshnessScoreNullDate() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testFreshnessScoreRecent() {
        ArticleCandidate candidate = createCandidate("art_01", "新文章", 1);
        candidate.setPublishedAt(java.time.LocalDateTime.now().minusDays(3));
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testFreshnessScoreWithinMonth() {
        ArticleCandidate candidate = createCandidate("art_01", "近期文章", 1);
        candidate.setPublishedAt(java.time.LocalDateTime.now().minusDays(15));
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testFreshnessScoreOld() {
        ArticleCandidate candidate = createCandidate("art_01", "旧文章", 1);
        candidate.setPublishedAt(java.time.LocalDateTime.now().minusDays(45));
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testPopularityScoreWithNullCounts() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setViewCount(null);
        candidate.setFavoriteCount(null);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testPopularityScoreWithCounts() {
        ArticleCandidate candidate = createCandidate("art_01", "热门文章", 1);
        candidate.setViewCount(1000);
        candidate.setFavoriteCount(50);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreEmpty() {
        recommendMapper.addCandidate(createCandidate("art_01", "无标签文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreNoMatch() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签B"));

        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreMatch() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 1));

        recommendMapper.addTags("art_01", List.of("标签A", "标签B"));
        recommendMapper.addTags("art_02", List.of("标签A", "标签C"));

        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testJaccardSimilarityEmpty() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTokenizeNull() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTokenizeBlank() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        recommendMapper.addCandidate(createCandidate("art_01", "", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testToSetNull() {
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testToSetEmpty() {
        recommendMapper.setUserRecentReads("user_01", List.of());

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildFingerprintNull() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTitle(null);
        candidate.setSummary(null);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testEnrichTagsEmpty() {
        recommendMapper.addCandidate(createCandidate("art_01", "无标签文章", 1));

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testInvalidateUserRecommendCacheWithKeys() {
        java.util.Set<String> keys = java.util.Set.of("article:rec:user_01:*");
        when(redisTemplate.keys(any())).thenReturn(keys);

        assertDoesNotThrow(() -> recommendService.invalidateUserRecommendCache("user_01"));
    }

    @Test
    void testInvalidateUserRecommendCacheWithException() {
        when(redisTemplate.keys(any())).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> recommendService.invalidateUserRecommendCache("user_01"));
    }

    @Test
    void testParseDifyRecommendationsEmptyArray() {
        properties.setPhase4DifyEnabled(true);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode outputs = objectMapper.createObjectNode();
        ArrayNode recommendations = objectMapper.createArrayNode();
        outputs.set("recommendations", recommendations);
        data.set("outputs", outputs);
        response.set("data", data);

        DifyClient difyClient = mock(DifyClient.class);
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(response);

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithNullFields() {
        properties.setPhase4DifyEnabled(true);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode outputs = objectMapper.createObjectNode();
        ArrayNode recommendations = objectMapper.createArrayNode();
        ObjectNode rec1 = objectMapper.createObjectNode();
        rec1.put("article_id", "art_01");
        recommendations.add(rec1);
        outputs.set("recommendations", recommendations);
        data.set("outputs", outputs);
        response.set("data", data);

        DifyClient difyClient = mock(DifyClient.class);
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(response);

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsArticleNotFound() {
        properties.setPhase4DifyEnabled(true);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode outputs = objectMapper.createObjectNode();
        ArrayNode recommendations = objectMapper.createArrayNode();
        ObjectNode rec1 = objectMapper.createObjectNode();
        rec1.put("article_id", "not_exist");
        rec1.put("rec_reason", "推荐");
        recommendations.add(rec1);
        outputs.set("recommendations", recommendations);
        data.set("outputs", outputs);
        response.set("data", data);

        DifyClient difyClient = mock(DifyClient.class);
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(response);

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFromPersistedRecommendationsEmpty() {
        List<Map<String, Object>> persisted = List.of();
        recommendMapper.setActiveRecommendations("user_01", persisted);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFromPersistedRecommendationsWithNullFields() {
        Map<String, Object> persistedMap = new LinkedHashMap<>();
        persistedMap.put("articleId", "art_01");
        persistedMap.put("title", "文章");
        persistedMap.put("summary", null);
        persistedMap.put("category", null);
        persistedMap.put("viewCount", null);
        persistedMap.put("recScore", 10.0);
        persistedMap.put("recReason", "推荐");
        persistedMap.put("recPhase", 4);
        recommendMapper.setActiveRecommendations("user_01", List.of(persistedMap));
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testScoreCandidatesEmpty() {
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
        assertEquals(0, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testScoreCandidatesNoCategoryWeights() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testScoreCandidatesWithCategoryWeights() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 3));

        List<Map<String, Object>> categoryWeights = List.of(
                Map.of("category", 2, "cnt", 5.0),
                Map.of("category", 3, "cnt", 3.0)
        );
        recommendMapper.setUserCategoryWeights("user_01", categoryWeights);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithNoRelatedArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.related("art_01", null, 5);
        assertNotNull(result);
        assertEquals(0, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testRelatedWithPhase2CoReadDisabled() {
        properties.setPhase2Enabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签A"));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithPhase2CoReadEnabled() {
        properties.setPhase2Enabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "另一篇文章", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签A"));
        recommendMapper.addTags("art_03", List.of("标签B"));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithEmptyCandidateTags() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTags(null);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithNullPublishedAt() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setPublishedAt(null);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithEmptyTags() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "另一篇", 1));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testPopularRecommendWithEmptyCandidates() {
        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
        assertEquals(0, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testRecommendWithEmptyCategoryWeights() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.setUserCategoryWeights("user_01", List.of());

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithHealthProfile() {
        recommendMapper.addCandidate(createCandidate("art_01", "健康文章", 1));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "medium"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithDifyRerankingEnabled() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "高血压管理", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[{\"article_id\":\"art_01\",\"reason\":\"AI推荐\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithDuplicateArticleIdsTriggersMergeFunction() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食", 1));
        recommendMapper.setReturnDuplicateCandidates(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[{\"article_id\":\"art_01\",\"reason\":\"AI推荐\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(objectMapper.readTree(json));

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        when(valueOps.get(any())).thenReturn(null);
        doNothing().when(valueOps).set(any(), any());

        RedisOperations<String, String> redisTemplate = mock(RedisOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.keys(any())).thenReturn(Collections.emptySet());

        ArticleRecommendService serviceWithDify = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                mock(MilvusArticleSearchService.class),
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                "test-api-key"
        );

        Map<String, Object> result = serviceWithDify.recommend("user_01", 1, 10);
        assertNotNull(result);
        recommendMapper.setReturnDuplicateCandidates(false);
    }

    @Test
    void testJaccardSimilarityWithEmptySet() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "另一篇", 1));
        
        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithMultipleMatchingArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食指南", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "糖尿病饮食管理", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "高血压饮食", 1));
        
        recommendMapper.addTags("art_01", List.of("糖尿病", "饮食"));
        recommendMapper.addTags("art_02", List.of("糖尿病", "饮食"));
        recommendMapper.addTags("art_03", List.of("高血压", "饮食"));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 3);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithMultipleCandidatesForSorting() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "较热门文章", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "普通文章", 1));
        
        recommendMapper.setUserRecentReads("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testPopularRecommendWithMultipleCandidates() {
        recommendMapper.addCandidate(createCandidate("art_01", "热门文章A", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "热门文章B", 1));
        
        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithPhase1Disabled() {
        properties.setPhase1Enabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithPhase3Disabled() {
        properties.setPhase3Enabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithPhase4Disabled() {
        properties.setPhase4DifyEnabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithArticlesArray() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"articles\":[{\"articleId\":\"art_01\",\"reason\":\"测试推荐\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithTextField() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"text\":\"{\\\"recommendations\\\":[{\\\"id\\\":\\\"art_01\\\",\\\"recReason\\\":\\\"文本推荐\\\"}]}\"}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsDirectOutputs() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"outputs\":{\"recommendations\":[{\"article_id\":\"art_01\",\"rec_reason\":\"直接输出\"}]}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testJaccardSimilarityWithMatchingTokens() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食管理", 1));
        recommendMapper.addTags("art_01", List.of("糖尿病", "饮食"));
        
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTokenizeWithPunctuation() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "饮食，运动；用药。", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsWithHighBMI() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 3));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2, "bmi", 28.0));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsWithType3Diabetes() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 3));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsWithChineseHighRisk() {
        recommendMapper.addCandidate(createCandidate("art_01", "并发症文章", 5));

        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "高风险"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithNullOutputs() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":null}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithEmptyArray() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTokenizeWithEmptyString() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTruncateShortString() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "短", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTruncateLongString() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        String longTitle = "这是一个非常长的标题用来测试truncate方法的覆盖情况";
        recommendMapper.addCandidate(createCandidate("art_01", longTitle, 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildFingerprintWithEmptyTags() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTags(null);
        recommendMapper.addCandidate(candidate);
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testEnsureFingerprintAlreadyExists() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        recommendMapper.addCandidate(candidate);
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithEmptyCandidates() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt())).thenReturn(Map.of());
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithMilvusUnavailable() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addTags("art_01", List.of("标签"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreWithEmptyTagSet() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreWithMatchingTags() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addTags("art_01", List.of("标签1", "标签2"));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testResolveCoverImageWithHttpUrl() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setCoverImageId("http://example.com/cover.jpg");
        recommendMapper.addCandidate(candidate);
        
        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testResolveCoverImageWithNullCover() {
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setCoverImageId(null);
        recommendMapper.addCandidate(candidate);
        
        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testInvalidateUserRecommendCacheWithNullKeys() {
        when(redisTemplate.keys(any())).thenReturn(null);
        
        recommendService.invalidateUserRecommendCache("user_01");
    }

    @Test
    void testInvalidateUserRecommendCacheWithEmptyKeys() {
        when(redisTemplate.keys(any())).thenReturn(java.util.Set.of());
        
        recommendService.invalidateUserRecommendCache("user_01");
    }

    @Test
    void testFromPersistedRecommendationsWithNull() {
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFromPersistedRecommendationsWithEmptyList() {
        recommendMapper.setUserRecentReads("user_01", List.of());
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBlankToDefaultWithBlankString() {
        recommendMapper.addCandidate(createCandidate("art_01", "", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBlankToDefaultWithNull() {
        ArticleCandidate candidate = createCandidate("art_01", null, 1);
        recommendMapper.addCandidate(candidate);
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFirstTextWithNullPath() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"unknown_field\":\"test\"}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase2CollaborativeWithEmptyFavorites() {
        properties.setPhase2Enabled(true);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase2CollaborativeWithNoSimilarUsers() {
        properties.setPhase2Enabled(true);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTextWithEmptyContext() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTagsWithEmptyContext() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithNoCandidates() {
        assertThrows(com.diabetes.common.exception.BusinessException.class, 
                () -> recommendService.related("art_01", "user_01", 5));
    }

    @Test
    void testRecommendWithCacheHit() {
        when(redisTemplate.opsForValue().get(any())).thenReturn("{\"articles\":[]}");
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRecommendWithCacheMiss() {
        when(redisTemplate.opsForValue().get(any())).thenReturn(null);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTruncateWithExactLength() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        String exactTitle = "1234567890";
        recommendMapper.addCandidate(createCandidate("art_01", exactTitle, 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testToSetWithNullList() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testToSetWithNonNullList() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addTags("art_01", List.of("标签1", "标签2"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testJaccardSimilarityWithNonEmptySets() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食管理", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "糖尿病饮食指南", 1));
        recommendMapper.addTags("art_01", List.of("糖尿病", "饮食"));
        recommendMapper.addTags("art_02", List.of("糖尿病", "饮食"));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTokenizeWithPunctuationAndFilter() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "a b 运动 用药", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithDifferentFieldNames() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"items\":[{\"id\":\"art_01\",\"reason\":\"测试\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase4DifyRerankWithSorting() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "文章3", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[" +
                "{\"article_id\":\"art_03\",\"reason\":\"最相关\"}," +
                "{\"article_id\":\"art_01\",\"reason\":\"相关\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTextWithMatchingArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食管理", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动康复", 3));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        recommendMapper.setUserRecentReads("user_01", List.of("art_02"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTagsWithMatchingArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章", 1));
        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签B"));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        recommendMapper.setUserRecentReads("user_01", List.of("art_02"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFromPersistedRecommendationsWithValidData() {
        Map<String, Object> persistedMap = new LinkedHashMap<>();
        persistedMap.put("articleId", "art_01");
        persistedMap.put("title", "测试文章");
        persistedMap.put("summary", "摘要");
        persistedMap.put("category", 2);
        persistedMap.put("viewCount", 100);
        persistedMap.put("recScore", 10.0);
        persistedMap.put("recReason", "测试推荐");
        persistedMap.put("recPhase", 2);
        recommendMapper.setActiveRecommendations("user_01", List.of(persistedMap));
        recommendMapper.addCandidate(createCandidate("art_01", "测试文章", 2));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTagsWithMatchingCandidates() {
        ArticleCandidate candidate1 = createCandidate("art_01", "饮食文章", 2);
        candidate1.setTags(List.of("饮食", "糖尿病"));
        ArticleCandidate candidate2 = createCandidate("art_02", "运动文章", 3);
        candidate2.setTags(List.of("运动", "健康"));
        recommendMapper.addCandidate(candidate1);
        recommendMapper.addCandidate(candidate2);
        
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        recommendMapper.setUserRecentReads("user_01", List.of("art_02"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTagsWithNonMatchingCandidates() {
        ArticleCandidate candidate1 = createCandidate("art_01", "饮食文章", 2);
        candidate1.setTags(List.of("饮食"));
        recommendMapper.addCandidate(candidate1);
        
        recommendMapper.setUserFavorites("user_01", List.of("art_not_exist"));
        recommendMapper.setUserRecentReads("user_01", List.of("art_not_exist"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTagsWithNullTags() throws Exception {
        ArticleCandidate candidate1 = createCandidate("art_01", "文章", 2);
        java.lang.reflect.Field tagsField = ArticleCandidate.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        tagsField.set(candidate1, null);
        recommendMapper.addCandidate(candidate1);
        
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTextWithMatchingCandidates() {
        ArticleCandidate candidate1 = createCandidate("art_01", "饮食管理", 2);
        candidate1.setTags(List.of("饮食"));
        ArticleCandidate candidate2 = createCandidate("art_02", "运动指南", 3);
        candidate2.setTags(List.of("运动"));
        recommendMapper.addCandidate(candidate1);
        recommendMapper.addCandidate(candidate2);
        
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        recommendMapper.setUserRecentReads("user_01", List.of("art_02"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTextWithNonMatchingCandidates() {
        ArticleCandidate candidate1 = createCandidate("art_01", "文章", 2);
        recommendMapper.addCandidate(candidate1);
        
        recommendMapper.setUserFavorites("user_01", List.of("art_not_exist"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreWithNoMatchingTags() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "无关文章", 1));
        
        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签B"));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testTagOverlapScoreWithNullInput() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章", 1));
        
        recommendMapper.addTags("art_01", List.of());
        recommendMapper.addTags("art_02", List.of());

        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testResolveCoverImageNullCover() {
        ArticleCandidate candidate = createCandidate("art_01", "无封面文章", 1);
        candidate.setCoverImageId(null);
        recommendMapper.addCandidate(candidate);
        
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("http://localhost/cover/art_01");

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(HealthServiceClient.class),
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testResolveCoverImageBlankCover() {
        ArticleCandidate candidate = createCandidate("art_01", "空白封面文章", 1);
        candidate.setCoverImageId("   ");
        recommendMapper.addCandidate(candidate);
        
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("http://localhost/cover/art_01");

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(HealthServiceClient.class),
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testFirstTextWithNullNode() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"text\":null}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testConstructorWithNullAndBlankParams() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                null,
                null,
                null,
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFromPersistedRecommendationsWithAllNullFields() {
        Map<String, Object> persistedMap = new LinkedHashMap<>();
        persistedMap.put("articleId", "art_01");
        persistedMap.put("title", null);
        persistedMap.put("summary", null);
        persistedMap.put("category", null);
        persistedMap.put("viewCount", null);
        persistedMap.put("recScore", null);
        persistedMap.put("recReason", null);
        persistedMap.put("recPhase", null);
        recommendMapper.setActiveRecommendations("user_01", List.of(persistedMap));
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFromPersistedRecommendationsWithNonNumberFields() {
        Map<String, Object> persistedMap = new LinkedHashMap<>();
        persistedMap.put("articleId", "art_01");
        persistedMap.put("title", "文章");
        persistedMap.put("summary", "摘要");
        persistedMap.put("category", "not_a_number");
        persistedMap.put("viewCount", "not_a_number");
        persistedMap.put("recScore", "not_a_number");
        persistedMap.put("recReason", "推荐");
        persistedMap.put("recPhase", "not_a_number");
        recommendMapper.setActiveRecommendations("user_01", List.of(persistedMap));
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildFingerprintWithNullTitleAndSummary() {
        ArticleCandidate candidate = createCandidate("art_01", null, 1);
        candidate.setSummary(null);
        candidate.setTags(null);
        recommendMapper.addCandidate(candidate);

        Map<String, Object> result = recommendService.popularRecommend(1, 10);
        assertNotNull(result);
    }

    @Test
    void testJaccardSimilarityWithOverlappingSets() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "糖尿病用药", 1));
        recommendMapper.addTags("art_01", List.of("糖尿病", "饮食"));
        recommendMapper.addTags("art_02", List.of("糖尿病", "用药"));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testJaccardSimilarityWithDisjointSets() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 1));
        recommendMapper.addTags("art_01", List.of("饮食"));
        recommendMapper.addTags("art_02", List.of("运动"));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testFirstTextWithEmptyPath() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"articles\":[{\"articleId\":\"art_01\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithArticlesField() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"outputs\":{\"articles\":[{\"id\":\"art_01\",\"recReason\":\"推荐理由\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithTextNotJson() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"text\":\"not a json\"}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testTokenizeWithShortWords() {
        recommendMapper.addCandidate(createCandidate("art_01", "a b c", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase4DifyRerankWithMultipleArticles() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "文章3", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[{\"article_id\":\"art_02\",\"reason\":\"推荐\"},{\"article_id\":\"art_01\",\"reason\":\"推荐\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTagsWithDuplicateArticleIds() {
        ArticleCandidate candidate1 = createCandidate("art_01", "饮食管理", 2);
        candidate1.setTags(List.of("饮食"));
        ArticleCandidate candidate2 = createCandidate("art_01", "重复ID文章", 2);
        candidate2.setTags(List.of("运动"));
        recommendMapper.addCandidate(candidate1);
        recommendMapper.addCandidate(candidate2);
        
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildInterestTextWithDuplicateArticleIds() {
        ArticleCandidate candidate1 = createCandidate("art_01", "饮食管理", 2);
        candidate1.setTags(List.of("饮食"));
        ArticleCandidate candidate2 = createCandidate("art_01", "重复ID文章", 2);
        candidate2.setTags(List.of("运动"));
        recommendMapper.addCandidate(candidate1);
        recommendMapper.addCandidate(candidate2);
        
        recommendMapper.setUserRecentReads("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase4DifyRerankWithDuplicateScoredArticles() throws Exception {
        properties.setPhase4DifyEnabled(true);
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_01", "重复ID文章", 1));
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[{\"article_id\":\"art_01\",\"reason\":\"推荐\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithDuplicateCandidateIds() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_01", "重复ID文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 1));

        recommendMapper.addTags("art_01", List.of("标签A"));
        recommendMapper.addTags("art_02", List.of("标签A"));

        Map<String, Object> result = recommendService.related("art_01", null, 2);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithOutputsPath() throws Exception {
        String json = "{\"outputs\":{\"recommendations\":[{\"article_id\":\"art_01\",\"reason\":\"推荐理由\"}]}}";
        JsonNode response = objectMapper.readTree(json);
        
        Method parseMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseMethod.setAccessible(true);
        
        Map<String, String> result = (Map<String, String>) parseMethod.invoke(recommendService, response);
        assertEquals(1, result.size());
        assertEquals("推荐理由", result.get("art_01"));
    }

    @Test
    void testParseDifyRecommendationsWithArticlesPath() throws Exception {
        String json = "{\"outputs\":{\"articles\":[{\"articleId\":\"art_01\",\"reason\":\"推荐理由\"}]}}";
        JsonNode response = objectMapper.readTree(json);
        
        Method parseMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseMethod.setAccessible(true);
        
        Map<String, String> result = (Map<String, String>) parseMethod.invoke(recommendService, response);
        assertEquals(1, result.size());
        assertEquals("推荐理由", result.get("art_01"));
    }

    @Test
    void testParseDifyRecommendationsWithTextPath() throws Exception {
        String json = "{\"outputs\":{\"text\":\"{\\\"recommendations\\\":[{\\\"id\\\":\\\"art_01\\\"}]}\"}}";
        JsonNode response = objectMapper.readTree(json);
        
        Method parseMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseMethod.setAccessible(true);
        
        Map<String, String> result = (Map<String, String>) parseMethod.invoke(recommendService, response);
        assertEquals(1, result.size());
        assertEquals("AI 为您推荐", result.get("art_01"));
    }

    @Test
    void testParseDifyRecommendationsWithTextAsArray() throws Exception {
        String json = "{\"outputs\":{\"text\":\"[{\\\"article_id\\\":\\\"art_01\\\"}]\"}}";
        JsonNode response = objectMapper.readTree(json);
        
        Method parseMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseMethod.setAccessible(true);
        
        Map<String, String> result = (Map<String, String>) parseMethod.invoke(recommendService, response);
        assertEquals(1, result.size());
    }

    @Test
    void testParseDifyRecommendationsWithInvalidJsonText() throws Exception {
        String json = "{\"outputs\":{\"text\":\"invalid json\"}}";
        JsonNode response = objectMapper.readTree(json);
        
        Method parseMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseMethod.setAccessible(true);
        
        Map<String, String> result = (Map<String, String>) parseMethod.invoke(recommendService, response);
        assertEquals(0, result.size());
    }

    @Test
    void testParseDifyRecommendationsWithEmptyRecommendations() throws Exception {
        String json = "{\"outputs\":{\"recommendations\":[]}}";
        JsonNode response = objectMapper.readTree(json);
        
        Method parseMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseMethod.setAccessible(true);
        
        Map<String, String> result = (Map<String, String>) parseMethod.invoke(recommendService, response);
        assertEquals(0, result.size());
    }

    @Test
    void testTruncateWithNull() throws Exception {
        Method truncateMethod = ArticleRecommendService.class.getDeclaredMethod("truncate", String.class, int.class);
        truncateMethod.setAccessible(true);
        
        String result = (String) truncateMethod.invoke(recommendService, null, 10);
        assertEquals("", result);
    }

    @Test
    void testTruncateWithShortString() throws Exception {
        Method truncateMethod = ArticleRecommendService.class.getDeclaredMethod("truncate", String.class, int.class);
        truncateMethod.setAccessible(true);
        
        String result = (String) truncateMethod.invoke(recommendService, "short", 10);
        assertEquals("short", result);
    }

    @Test
    void testTruncateWithLongString() throws Exception {
        Method truncateMethod = ArticleRecommendService.class.getDeclaredMethod("truncate", String.class, int.class);
        truncateMethod.setAccessible(true);
        
        String result = (String) truncateMethod.invoke(recommendService, "这是一个很长的字符串", 5);
        assertEquals("这是一个很", result);
    }

    @Test
    void testToSetWithNull() throws Exception {
        Method toSetMethod = ArticleRecommendService.class.getDeclaredMethod("toSet", List.class);
        toSetMethod.setAccessible(true);
        
        Set<String> result = (Set<String>) toSetMethod.invoke(recommendService, (Object) null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testToSetWithEmptyList() throws Exception {
        Method toSetMethod = ArticleRecommendService.class.getDeclaredMethod("toSet", List.class);
        toSetMethod.setAccessible(true);
        
        Set<String> result = (Set<String>) toSetMethod.invoke(recommendService, List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testTokenizeWithNull() throws Exception {
        Method tokenizeMethod = ArticleRecommendService.class.getDeclaredMethod("tokenize", String.class);
        tokenizeMethod.setAccessible(true);
        
        Set<String> result = (Set<String>) tokenizeMethod.invoke(recommendService, (Object) null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testTokenizeWithBlank() throws Exception {
        Method tokenizeMethod = ArticleRecommendService.class.getDeclaredMethod("tokenize", String.class);
        tokenizeMethod.setAccessible(true);
        
        Set<String> result = (Set<String>) tokenizeMethod.invoke(recommendService, "   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void testTokenizeWithSingleChar() throws Exception {
        Method tokenizeMethod = ArticleRecommendService.class.getDeclaredMethod("tokenize", String.class);
        tokenizeMethod.setAccessible(true);
        
        Set<String> result = (Set<String>) tokenizeMethod.invoke(recommendService, "a");
        assertTrue(result.isEmpty());
    }

    @Test
    void testJaccardSimilarityWithEmptySets() throws Exception {
        Method jaccardMethod = ArticleRecommendService.class.getDeclaredMethod("jaccardSimilarity", Set.class, Set.class);
        jaccardMethod.setAccessible(true);
        
        double result = (double) jaccardMethod.invoke(recommendService, Set.of(), Set.of("a"));
        assertEquals(0.0, result, 0.0001);
        
        result = (double) jaccardMethod.invoke(recommendService, Set.of("a"), Set.of());
        assertEquals(0.0, result, 0.0001);
    }

    @Test
    void testJaccardSimilarityWithOverlap() throws Exception {
        Method jaccardMethod = ArticleRecommendService.class.getDeclaredMethod("jaccardSimilarity", Set.class, Set.class);
        jaccardMethod.setAccessible(true);
        
        double result = (double) jaccardMethod.invoke(recommendService, Set.of("a", "b"), Set.of("b", "c"));
        assertEquals(1.0 / 3.0, result, 0.0001);
    }

    @Test
    void testTagOverlapScoreAllNullEmptyCases() throws Exception {
        Method tagOverlapMethod = ArticleRecommendService.class.getDeclaredMethod("tagOverlapScore", Set.class, List.class);
        tagOverlapMethod.setAccessible(true);
        
        assertEquals(0.0, (double) tagOverlapMethod.invoke(recommendService, null, List.of("a")), 0.0001);
        assertEquals(0.0, (double) tagOverlapMethod.invoke(recommendService, Set.of("a"), null), 0.0001);
        assertEquals(0.0, (double) tagOverlapMethod.invoke(recommendService, Set.of(), List.of("a")), 0.0001);
        assertEquals(0.0, (double) tagOverlapMethod.invoke(recommendService, Set.of("a"), List.of()), 0.0001);
        assertEquals(0.0, (double) tagOverlapMethod.invoke(recommendService, null, null), 0.0001);
        
        double result = (double) tagOverlapMethod.invoke(recommendService, Set.of("a", "b"), List.of("b", "c"));
        assertEquals(1.0 / 2.0, result, 0.0001);
    }

    @Test
    void testFirstTextWithValidValue() throws Exception {
        Method firstTextMethod = ArticleRecommendService.class.getDeclaredMethod("firstText", JsonNode.class, String[].class);
        firstTextMethod.setAccessible(true);
        
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key1", "value1");
        node.put("key2", "value2");
        
        assertEquals("value1", firstTextMethod.invoke(recommendService, node, new String[]{"key1", "key2"}));
        assertEquals("value2", firstTextMethod.invoke(recommendService, node, new String[]{"key2", "key1"}));
        assertEquals(null, firstTextMethod.invoke(recommendService, node, new String[]{"nonexistent"}));
        
        ObjectNode nullNode = objectMapper.createObjectNode();
        nullNode.putNull("null_key");
        assertEquals(null, firstTextMethod.invoke(recommendService, nullNode, new String[]{"null_key"}));
    }

    @Test
    void testResolveCoverImageHttpsUrl() throws Exception {
        Method resolveCoverMethod = ArticleRecommendService.class.getDeclaredMethod("resolveCoverImage", ArticleCandidate.class);
        resolveCoverMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setCoverImageId("https://example.com/cover.jpg");
        
        String result = (String) resolveCoverMethod.invoke(recommendService, candidate);
        assertEquals("https://example.com/cover.jpg", result);
    }

    @Test
    void testEnsureFingerprintWithExistingFingerprint() throws Exception {
        Method ensureFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("ensureFingerprint", ArticleCandidate.class);
        ensureFingerprintMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTextFingerprint("existing_fingerprint");
        
        ensureFingerprintMethod.invoke(recommendService, candidate);
        
        assertEquals("existing_fingerprint", candidate.getTextFingerprint());
    }

    @Test
    void testBlankToDefaultWithNonBlankString() throws Exception {
        Method blankToDefaultMethod = ArticleRecommendService.class.getDeclaredMethod("blankToDefault", String.class, String.class);
        blankToDefaultMethod.setAccessible(true);
        
        assertEquals("trimmed", blankToDefaultMethod.invoke(recommendService, "  trimmed  ", "default"));
        assertEquals("default", blankToDefaultMethod.invoke(recommendService, null, "default"));
        assertEquals("default", blankToDefaultMethod.invoke(recommendService, "", "default"));
        assertEquals("default", blankToDefaultMethod.invoke(recommendService, "   ", "default"));
    }

    @Test
    void testEnsureFingerprintWithNullFingerprint() throws Exception {
        Method ensureFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("ensureFingerprint", ArticleCandidate.class);
        ensureFingerprintMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章标题", 1);
        candidate.setTextFingerprint(null);
        recommendMapper.addCandidate(candidate);
        
        ensureFingerprintMethod.invoke(recommendService, candidate);
        
        assertNotNull(candidate.getTextFingerprint());
        assertTrue(!candidate.getTextFingerprint().isBlank());
    }

    @Test
    void testEnsureFingerprintWithBlankFingerprint() throws Exception {
        Method ensureFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("ensureFingerprint", ArticleCandidate.class);
        ensureFingerprintMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章标题", 1);
        candidate.setTextFingerprint("   ");
        recommendMapper.addCandidate(candidate);
        
        ensureFingerprintMethod.invoke(recommendService, candidate);
        
        assertNotNull(candidate.getTextFingerprint());
        assertTrue(!candidate.getTextFingerprint().isBlank());
    }

    @Test
    void testResolveCoverImageWithNonUrlId() throws Exception {
        Method resolveCoverMethod = ArticleRecommendService.class.getDeclaredMethod("resolveCoverImage", ArticleCandidate.class);
        resolveCoverMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setCoverImageId("local-image-id");
        
        String result = (String) resolveCoverMethod.invoke(recommendService, candidate);
        assertEquals("/images/articles/test.jpg", result);
    }

    @Test
    void testConstructorWithBlankDifyInputVarName() {
        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "",
                "object",
                "blocking",
                ""
        );
        assertNotNull(recommendService);
    }

    @Test
    void testRelatedWithBlankUserId() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章", 2));
        
        Map<String, Object> result = recommendService.related("art_01", "   ", 2);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithDifferentCategory() {
        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 3));
        
        recommendMapper.addTags("art_01", List.of("标签"));
        recommendMapper.addTags("art_02", List.of("标签"));
        
        Map<String, Object> result = recommendService.related("art_01", "user_01", 2);
        assertNotNull(result);
    }

    @Test
    void testBuildFingerprintWithNullTags() throws Exception {
        Method buildFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("buildFingerprint", ArticleCandidate.class);
        buildFingerprintMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "标题", 1);
        java.lang.reflect.Field tagsField = ArticleCandidate.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        tagsField.set(candidate, null);
        
        String result = (String) buildFingerprintMethod.invoke(recommendService, candidate);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase2Disabled() {
        properties.setPhase2Enabled(false);
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase2WithCoReadersBelowMin() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithMilvusSimZero() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt())).thenReturn(Map.of("art_01", 0.0));
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithBlankInterestText() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.setUserFavorites("user_01", List.of());
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithBlankFingerprint() throws Exception {
        properties.setPhase3Enabled(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTextFingerprint("");
        
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "糖尿病饮食");
        
        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic", List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        List<ScoredArticle> scored = new ArrayList<>();
        scored.add(new ScoredArticle(candidate));
        
        applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
        
        assertNotNull(scored);
    }

    @Test
    void testApplyPhase3SemanticWithLowSimilarity() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "完全不相关的文章", 1));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithTextOutput() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"text\":\"[{\\\"article_id\\\":\\\"art_01\\\",\\\"reason\\\":\\\"AI推荐\\\"}]\"}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithTextOutputInvalidJson() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"text\":\"invalid json\"}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    

    @Test
    void testProfileCategoryWeightsHighRisk() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "high"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "并发症文章", 5));
        recommendMapper.addCandidate(createCandidate("art_02", "用药文章", 4));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsBmiOver24() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2, "bmi", 25.0));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "运动文章", 3));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsDiabetesType1() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 1));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "用药文章", 4));
        recommendMapper.addCandidate(createCandidate("art_02", "基础文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsDiabetesType4() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 4));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "饮食文章", 2));
        recommendMapper.addCandidate(createCandidate("art_02", "并发症文章", 5));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsDefault() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 9));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                healthService,
                mock(DifyClient.class),
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "",
                "inputs",
                "object",
                "blocking",
                ""
        );

        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase2CollaborativeWithCoReaders() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.setCoReadArticles("user_01", List.of(Map.of("articleId", "art_01", "coCount", 5)));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase2CollaborativeWithCoFavoriteArticles() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.setCoFavoriteArticles("user_01", List.of(Map.of("articleId", "art_01", "coCount", 5)));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithMilvusAvailableAndResults() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt())).thenReturn(Map.of("art_01", 0.8));
        
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食", 2));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithMilvusAvailableButEmptyResults() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt())).thenReturn(Map.of());
        
        recommendMapper.addCandidate(createCandidate("art_01", "文章", 1));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticLocalSemanticMatch() {
        properties.setPhase3Enabled(true);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食指南", 2));
        recommendMapper.addTags("art_01", List.of("饮食", "糖尿病"));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        
        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildFingerprintWithNullTitleSummary() throws Exception {
        Method buildFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("buildFingerprint", ArticleCandidate.class);
        buildFingerprintMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", null, 1);
        candidate.setSummary(null);
        candidate.setTags(null);
        
        String result = (String) buildFingerprintMethod.invoke(recommendService, candidate);
        assertEquals("", result);
    }

    @Test
    void testBuildFingerprintWithEmptyTitleSummary() throws Exception {
        Method buildFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("buildFingerprint", ArticleCandidate.class);
        buildFingerprintMethod.setAccessible(true);
        
        ArticleCandidate candidate = createCandidate("art_01", "", 1);
        candidate.setSummary("");
        candidate.setTags(List.of());
        
        String result = (String) buildFingerprintMethod.invoke(recommendService, candidate);
        assertEquals("", result);
    }

    @Test
    void testRelatedDifferentCategory() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章1", 2));
        recommendMapper.addCandidate(createCandidate("art_03", "相关文章2", 2));

        assertDoesNotThrow(() -> recommendService.related("art_01", "user_01", 5));
    }

    @Test
    void testApplyPhase2CollaborativeWithCoReadersAboveThreshold() {
        recommendMapper.setCoReadArticles("user_01", List.of(Map.of("articleId", "art_01", "coCount", 5)));
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "文章2", 1));
        recommendMapper.setUserRecentReads("user_01", List.of("art_02"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithMilvusHits() {
        recommendMapper.addCandidate(createCandidate("art_01", "糖尿病饮食", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "高血压管理", 1));
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(any(), any(), anyInt()))
                .thenReturn(Map.of("art_01", 0.8));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithNonNullFingerprint() {
        ArticleCandidate candidate = createCandidate("art_01", "糖尿病饮食管理", 1);
        candidate.setTextFingerprint("diabetes diet management");
        recommendMapper.addCandidate(candidate);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithHighSimilarity() {
        ArticleCandidate candidate = createCandidate("art_01", "糖尿病饮食管理", 1);
        candidate.setTextFingerprint("diabetes diet management");
        recommendMapper.addCandidate(candidate);
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testParseDifyRecommendationsWithNonNullId() throws Exception {
        Method parseDifyRecommendationsMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseDifyRecommendationsMethod.setAccessible(true);

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode outputs = objectMapper.createObjectNode();
        ArrayNode array = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("article_id", "art_01");
        item.put("reason", "AI推荐");
        array.add(item);
        outputs.set("recommendations", array);
        data.set("outputs", outputs);
        root.set("data", data);

        Map<String, String> result = (Map<String, String>) parseDifyRecommendationsMethod.invoke(recommendService, root);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("AI推荐", result.get("art_01"));
    }

    @Test
    void testBuildInterestTagsWithNonNullTags() {
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));
        recommendMapper.setUserFavorites("user_01", List.of("art_01"));
        recommendMapper.setUserRecentReads("user_01", List.of("art_01"));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsWithBmiBelowThreshold() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2, "bmi", 22.0));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "low"));

        recommendService = new ArticleRecommendService(
                recommendMapper, redisTemplate, objectMapper,
                mock(com.diabetes.common.storage.MinioStorageService.class),
                healthService, mock(com.diabetes.common.dify.DifyClient.class),
                properties, milvusArticleSearchService,
                "http://localhost:8080", "", "inputs", "object", "blocking", ""
        );
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testProfileCategoryWeightsWithHighRiskLevel() {
        HealthServiceClient healthService = mock(HealthServiceClient.class);
        when(healthService.getLatestHealthProfile(any(), any())).thenReturn(Map.of("diabetesType", 2, "bmi", 25.0));
        when(healthService.getLatestRiskAssessment(any(), any())).thenReturn(Map.of("riskLevel", "high"));

        recommendService = new ArticleRecommendService(
                recommendMapper, redisTemplate, objectMapper,
                mock(com.diabetes.common.storage.MinioStorageService.class),
                healthService, mock(com.diabetes.common.dify.DifyClient.class),
                properties, milvusArticleSearchService,
                "http://localhost:8080", "", "inputs", "object", "blocking", ""
        );
        recommendMapper.addCandidate(createCandidate("art_01", "文章1", 1));

        Map<String, Object> result = recommendService.recommend("user_01", 1, 10);
        assertNotNull(result);
    }

    @Test
    void testBuildFingerprintWithNonNullTags() throws Exception {
        Method buildFingerprintMethod = ArticleRecommendService.class.getDeclaredMethod("buildFingerprint", ArticleCandidate.class);
        buildFingerprintMethod.setAccessible(true);

        ArticleCandidate candidate = createCandidate("art_01", "测试文章", 1);
        candidate.setSummary("测试摘要");
        candidate.setTags(List.of("标签1", "标签2"));

        String result = (String) buildFingerprintMethod.invoke(recommendService, candidate);
        assertTrue(result.contains("标签1"));
        assertTrue(result.contains("标签2"));
    }

    @Test
    void testApplyPhase2CollaborativeWithCoReadersBelowThreshold() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");

        Method applyPhase2CollaborativeMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase2Collaborative",
                List.class, ctxClass);
        applyPhase2CollaborativeMethod.setAccessible(true);

        ArticleCandidate candidate = createCandidate("art_01", "文章1", 1);
        recommendMapper.setCoReadArticles("user_01", List.of(Map.of("articleId", "art_01", "coCount", 0)));

        List<com.diabetes.article.entity.ScoredArticle> scored = List.of(new com.diabetes.article.entity.ScoredArticle(candidate));
        applyPhase2CollaborativeMethod.invoke(recommendService, scored, ctx);
    }

    @Test
    void testApplyPhase3SemanticWithNonNullInterestTextAndHighSimilarity() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "diabetes diet");

        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic",
                List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        ArticleCandidate candidate = createCandidate("art_01", "糖尿病饮食管理", 1);
        candidate.setTextFingerprint("diabetes diet management");

        List<com.diabetes.article.entity.ScoredArticle> scored = new ArrayList<>();
        scored.add(new com.diabetes.article.entity.ScoredArticle(candidate));
        applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
    }

    @Test
    void testApplyPhase3SemanticWithNonNullInterestTextAndLowSimilarity() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "diabetes diet");

        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic",
                List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        ArticleCandidate candidate = createCandidate("art_01", "无关文章", 1);
        candidate.setTextFingerprint("completely unrelated text");

        List<com.diabetes.article.entity.ScoredArticle> scored = new ArrayList<>();
        scored.add(new com.diabetes.article.entity.ScoredArticle(candidate));
        applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
    }

    @Test
    void testApplyPhase3SemanticWithNonNullInterestTextAndNullFingerprint() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "diabetes diet");

        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic",
                List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTextFingerprint(null);

        List<com.diabetes.article.entity.ScoredArticle> scored = new ArrayList<>();
        scored.add(new com.diabetes.article.entity.ScoredArticle(candidate));
        applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
    }

    @Test
    void testParseDifyRecommendationsWithNullId() throws Exception {
        Method parseDifyRecommendationsMethod = ArticleRecommendService.class.getDeclaredMethod("parseDifyRecommendations", JsonNode.class);
        parseDifyRecommendationsMethod.setAccessible(true);

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode outputs = objectMapper.createObjectNode();
        ArrayNode array = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.putNull("article_id");
        item.put("reason", "AI推荐");
        array.add(item);
        outputs.set("recommendations", array);
        data.set("outputs", outputs);
        root.set("data", data);

        Map<String, String> result = (Map<String, String>) parseDifyRecommendationsMethod.invoke(recommendService, root);
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void testBuildInterestTagsWithCandidateNull() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        
        java.lang.reflect.Field favoriteIdsField = ctxClass.getDeclaredField("favoriteIds");
        favoriteIdsField.setAccessible(true);
        favoriteIdsField.set(ctx, Set.of("art_nonexistent"));
        
        java.lang.reflect.Field recentReadIdsField = ctxClass.getDeclaredField("recentReadIds");
        recentReadIdsField.setAccessible(true);
        recentReadIdsField.set(ctx, Set.of("art_nonexistent"));

        Method buildInterestTagsMethod = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags",
                ctxClass, List.class);
        buildInterestTagsMethod.setAccessible(true);

        List<ArticleCandidate> candidates = List.of();

        Set<String> result = (Set<String>) buildInterestTagsMethod.invoke(recommendService, ctx, candidates);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildInterestTagsWithTagsNull() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        
        java.lang.reflect.Field favoriteIdsField = ctxClass.getDeclaredField("favoriteIds");
        favoriteIdsField.setAccessible(true);
        favoriteIdsField.set(ctx, Set.of("art_01"));
        
        java.lang.reflect.Field recentReadIdsField = ctxClass.getDeclaredField("recentReadIds");
        recentReadIdsField.setAccessible(true);
        recentReadIdsField.set(ctx, Set.of("art_01"));

        Method buildInterestTagsMethod = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags",
                ctxClass, List.class);
        buildInterestTagsMethod.setAccessible(true);

        ArticleCandidate candidate = createCandidate("art_01", "文章", 1);
        candidate.setTags(null);

        List<ArticleCandidate> candidates = List.of(candidate);

        Set<String> result = (Set<String>) buildInterestTagsMethod.invoke(recommendService, ctx, candidates);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testProfileCategoryWeightsWithBmiNonNumber() throws Exception {
        Method profileCategoryWeightsMethod = ArticleRecommendService.class.getDeclaredMethod("profileCategoryWeights",
                Map.class, Map.class);
        profileCategoryWeightsMethod.setAccessible(true);

        Map<String, Object> health = new HashMap<>();
        health.put("diabetesType", 2);
        health.put("bmi", "invalid");

        Map<String, Object> risk = new HashMap<>();
        risk.put("riskLevel", "low");

        Map<Integer, Double> result = (Map<Integer, Double>) profileCategoryWeightsMethod.invoke(recommendService, health, risk);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testRelatedWithSameCategory() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章1", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "相关文章2", 1));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 5);
        assertNotNull(result);
    }

    @Test
    void testRelatedWithMixedCategories() {
        recommendMapper.addCandidate(createCandidate("art_01", "基础文章", 1));
        recommendMapper.addCandidate(createCandidate("art_02", "相关文章同分类", 1));
        recommendMapper.addCandidate(createCandidate("art_03", "相关文章不同分类", 2));

        Map<String, Object> result = recommendService.related("art_01", "user_01", 5);
        assertNotNull(result);
    }

    @Test
    void testApplyPhase3SemanticWithInterestTextAndLowSimilarity() throws Exception {
        properties.setPhase3Enabled(true);
        
        ArticleCandidate candidate = createCandidate("art_low_sim", "低相似度文章", 1);
        
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "糖尿病饮食管理");
        
        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic", List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        List<ScoredArticle> scored = new ArrayList<>();
        scored.add(new ScoredArticle(candidate));
        
        applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
        
        assertNotNull(scored);
    }

    @Test
    void testApplyPhase3SemanticWithEmptyInterestText() throws Exception {
        properties.setPhase3Enabled(true);
        
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "");
        
        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic", List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        List<ScoredArticle> scored = new ArrayList<>();
        scored.add(new ScoredArticle(createCandidate("art_01", "文章", 1)));
        
        int result = (int) applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
        assertEquals(1, result);
    }

    @Test
    void testApplyPhase3SemanticWithNullFingerprint() throws Exception {
        properties.setPhase3Enabled(true);
        
        ArticleCandidate candidate = createCandidate("art_null_fp", "文章", 1);
        candidate.setTextFingerprint(null);
        
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, "糖尿病饮食");
        
        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic", List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        List<ScoredArticle> scored = new ArrayList<>();
        scored.add(new ScoredArticle(candidate));
        
        applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
        
        assertNotNull(scored);
    }

    @Test
    void testBuildInterestTagsWithNullCandidate() throws Exception {
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field favoriteIdsField = ctxClass.getDeclaredField("favoriteIds");
        favoriteIdsField.setAccessible(true);
        favoriteIdsField.set(ctx, Set.of("non_existent_article"));

        Method buildInterestTagsMethod = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        buildInterestTagsMethod.setAccessible(true);

        Set<String> result = (Set<String>) buildInterestTagsMethod.invoke(recommendService, ctx, List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildInterestTagsWithNonNullCandidateNullTags() throws Exception {
        ArticleCandidate candidate = createCandidate("art_tags_null", "文章", 1);
        java.lang.reflect.Field tagsField = ArticleCandidate.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        tagsField.set(candidate, null);

        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field favoriteIdsField = ctxClass.getDeclaredField("favoriteIds");
        favoriteIdsField.setAccessible(true);
        favoriteIdsField.set(ctx, Set.of("art_tags_null"));

        Method buildInterestTagsMethod = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        buildInterestTagsMethod.setAccessible(true);

        Set<String> result = (Set<String>) buildInterestTagsMethod.invoke(recommendService, ctx, List.of(candidate));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testApplyPhase3SemanticWithNullInterestText() throws Exception {
        properties.setPhase3Enabled(true);
        
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field interestTextField = ctxClass.getDeclaredField("interestText");
        interestTextField.setAccessible(true);
        interestTextField.set(ctx, null);
        
        Method applyPhase3SemanticMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase3Semantic", List.class, ctxClass);
        applyPhase3SemanticMethod.setAccessible(true);

        List<ScoredArticle> scored = new ArrayList<>();
        scored.add(new ScoredArticle(createCandidate("art_01", "文章", 1)));
        
        int result = (int) applyPhase3SemanticMethod.invoke(recommendService, scored, ctx);
        assertEquals(1, result);
    }

    @Test
    void testBuildInterestTagsWithRecentReadNullTags() throws Exception {
        ArticleCandidate candidate = createCandidate("art_recent_null_tags", "最近阅读文章", 1);
        java.lang.reflect.Field tagsField = ArticleCandidate.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        tagsField.set(candidate, null);

        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        java.lang.reflect.Field recentReadIdsField = ctxClass.getDeclaredField("recentReadIds");
        recentReadIdsField.setAccessible(true);
        recentReadIdsField.set(ctx, Set.of("art_recent_null_tags"));

        Method buildInterestTagsMethod = ArticleRecommendService.class.getDeclaredMethod("buildInterestTags", ctxClass, List.class);
        buildInterestTagsMethod.setAccessible(true);

        Set<String> result = (Set<String>) buildInterestTagsMethod.invoke(recommendService, ctx, List.of(candidate));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testApplyPhase4DifyRerankWithDuplicateArticleIds() throws Exception {
        properties.setPhase4DifyEnabled(true);
        
        ArticleCandidate candidate1 = createCandidate("art_dup", "重复ID文章", 1);
        ArticleCandidate candidate2 = createCandidate("art_dup", "重复ID文章", 1);
        
        ScoredArticle sa1 = new ScoredArticle(candidate1);
        sa1.addScore(1.0);
        ScoredArticle sa2 = new ScoredArticle(candidate2);
        sa2.addScore(2.0);
        
        List<ScoredArticle> scored = new ArrayList<>();
        scored.add(sa1);
        scored.add(sa2);
        
        Class<?> ctxClass = Class.forName("com.diabetes.article.service.ArticleRecommendService$RecommendContext");
        Object ctx = ctxClass.getDeclaredConstructor(String.class).newInstance("user_01");
        
        DifyClient difyClient = mock(DifyClient.class);
        String json = "{\"data\":{\"outputs\":{\"recommendations\":[{\"id\":\"art_dup\",\"reason\":\"AI推荐\"}]}}}";
        when(difyClient.runWorkflowBlocking(any(), any(), any(), any())).thenReturn(objectMapper.readTree(json));

        recommendService = new ArticleRecommendService(
                recommendMapper,
                redisTemplate,
                objectMapper,
                mock(MinioStorageService.class),
                mock(HealthServiceClient.class),
                difyClient,
                properties,
                milvusArticleSearchService,
                "http://localhost:8080",
                "test-api-key",
                "inputs",
                "object",
                "blocking",
                ""
        );

        Method applyPhase4DifyRerankMethod = ArticleRecommendService.class.getDeclaredMethod("applyPhase4DifyRerank", String.class, List.class, ctxClass);
        applyPhase4DifyRerankMethod.setAccessible(true);

        int result = (int) applyPhase4DifyRerankMethod.invoke(recommendService, "user_01", scored, ctx);
        assertEquals(4, result);
    }
}
