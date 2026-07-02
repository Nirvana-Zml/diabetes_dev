package com.diabetes.article.service;

import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
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

class ArticleRecommendServiceTest {

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

    private ArticleRecommendService service;

    @BeforeEach
    void setUp() {
        properties.setPhase4DifyEnabled(false);
        service = new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", "", "flat", "object", "blocking", "internal-key");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(minioStorageService.buildArticleCoverUrl(anyString())).thenReturn("http://minio/cover.jpg");
    }

    @Test
    void recommend_blankUserIdUsesPopular() {
        ArticleCandidate candidate = candidate("art_1", 2, 100, 5);
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(candidate));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));

        Map<String, Object> result = service.recommend(null, 1, 10);

        assertEquals("popular", result.get("strategy"));
        assertEquals(1, result.get("total"));
    }

    @Test
    void recommend_returnsCachedResult() throws Exception {
        Map<String, Object> cached = Map.of("articles", List.of(), "total", 0, "strategy", "personalized", "phase", 1);
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        Map<String, Object> result = service.recommend("u1", 1, 10);

        assertEquals("personalized", result.get("strategy"));
        verifyNoInteractions(recommendMapper);
    }

    @Test
    void recommend_usesPersistedRecommendations() {
        when(valueOps.get(anyString())).thenReturn(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("articleId", "art_1");
        row.put("title", "标题");
        row.put("summary", "摘要");
        row.put("category", 2);
        row.put("viewCount", 10);
        row.put("recScore", 8.5);
        row.put("recReason", "为您精选");
        row.put("recPhase", 2);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of(row));

        Map<String, Object> result = service.recommend("u1", 1, 10);

        assertEquals("personalized", result.get("strategy"));
        assertEquals(1, result.get("total"));
        verify(recommendMapper, never()).softDeleteUserRecommendations("u1");
    }

    @Test
    void recommend_buildsPersonalizedRecommendations() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate candidate = candidate("art_1", 2, 50, 2);
        candidate.setPublishedAt(LocalDateTime.now().minusDays(2));
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(candidate));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of("art_1"));
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of(Map.of("category", 2, "cnt", 3)));
        when(healthServiceClient.getLatestHealthProfile("u1", "internal-key"))
                .thenReturn(Map.of("diabetesType", 3, "bmi", 26));
        when(healthServiceClient.getLatestRiskAssessment("u1", "internal-key"))
                .thenReturn(Map.of("riskLevel", "high"));
        when(recommendMapper.findCoReadArticles(eq("u1"), anyList(), eq(30))).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(eq("u1"), anyList(), eq(30))).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        Map<String, Object> result = service.recommend("u1", 1, 10);

        assertEquals("personalized", result.get("strategy"));
        assertEquals(1, result.get("total"));
        verify(recommendMapper).insertRecommendation(anyString(), eq("u1"), eq("art_1"),
                anyDouble(), anyString(), anyString(), anyInt(), isNull(), any());
        verify(valueOps).set(anyString(), contains("personalized"), any());
    }

    @Test
    void popularRecommend_sortsByScoreAndCaches() {
        ArticleCandidate hot = candidate("art_hot", 2, 1000, 10);
        hot.setPublishedAt(LocalDateTime.now());
        ArticleCandidate cold = candidate("art_cold", 1, 1, 0);
        cold.setPublishedAt(LocalDateTime.now().minusDays(60));
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(cold, hot));
        when(recommendMapper.findTagsByArticleId(anyString())).thenReturn(List.of());

        Map<String, Object> result = service.popularRecommend(1, 10);

        assertEquals("popular", result.get("strategy"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articles = (List<Map<String, Object>>) result.get("articles");
        assertEquals("art_hot", articles.get(0).get("articleId"));
    }

    @Test
    void related_notFound() {
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.related("missing", "u1", 5));
        assertEquals(404, ex.getCode());
    }

    @Test
    void related_returnsSimilarArticles() {
        ArticleCandidate base = candidate("art_1", 2, 10, 1);
        base.setTags(List.of("饮食"));
        ArticleCandidate other = candidate("art_2", 2, 8, 0);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(base));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRelatedCandidates("art_1", 2, 15))
                .thenReturn(List.of(other));
        when(recommendMapper.findTagsByArticleId("art_2")).thenReturn(List.of("饮食"));

        Map<String, Object> result = service.related("art_1", null, 5);

        assertEquals("related", result.get("strategy"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articles = (List<Map<String, Object>>) result.get("articles");
        assertFalse(articles.isEmpty());
    }

    @Test
    void recordRead_requiresLogin() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordRead(null, "art_1", 10, "detail"));
        assertEquals(401, ex.getCode());
    }

    @Test
    void recordRead_persistsAndInvalidatesCache() {
        when(redis.keys(anyString())).thenReturn(Set.of("cache-key"));

        service.recordRead("u1", "art_1", 30, "detail");

        verify(recommendMapper).upsertUserRead(anyString(), eq("u1"), eq("art_1"), eq(30), eq("detail"));
        verify(redis).delete(Set.of("cache-key"));
        verify(recommendMapper).softDeleteUserRecommendations("u1");
    }

    @Test
    void invalidateUserRecommendCache_ignoresBlankUserId() {
        service.invalidateUserRecommendCache(" ");
        verifyNoInteractions(redis);
    }

    @Test
    void getDifyWorkflowSpec_returnsContractSpec() {
        Map<String, Object> spec = service.getDifyWorkflowSpec();
        assertEquals("http://dify.local/v1/workflows/run", spec.get("workflowUrl"));
        assertNotNull(spec.get("inputJsonSchema"));
    }

    private static ArticleCandidate candidate(String id, int category, int views, int favorites) {
        ArticleCandidate candidate = new ArticleCandidate();
        candidate.setArticleId(id);
        candidate.setTitle("标题-" + id);
        candidate.setSummary("摘要-" + id);
        candidate.setCategory(category);
        candidate.setViewCount(views);
        candidate.setFavoriteCount(favorites);
        candidate.setPublishedAt(LocalDateTime.now());
        return candidate;
    }
}
