package com.diabetes.article.service;

import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleRecommendServiceCoverageGapTest {

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

    private ArticleRecommendService service(String apiKey, String inputVar, String inputFormat, String responseMode) {
        return new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", apiKey, inputVar, inputFormat, responseMode, "internal-key");
    }

    @Test
    void constructorUsesDefaultInputVarAndFormats() {
        assertDoesNotThrow(() -> new ArticleRecommendService(
                recommendMapper, redis, objectMapper, minioStorageService,
                healthServiceClient, difyClient, properties, milvusArticleSearchService,
                "http://dify.local", "key", null, null, null, "internal-key"));
    }

    @Test
    void recommend_phase4SkippedWhenApiKeyBlank() {
        properties.setPhase4DifyEnabled(true);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        stubRecommend("u1");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        service.recommend("u1", 1, 10);
        verify(difyClient, never()).runWorkflowBlocking(anyString(), anyString(), any(), anyString());
    }

    @Test
    void recommend_phase4EmptyReasonsReturnsPhase3() throws Exception {
        properties.setPhase4DifyEnabled(true);
        ArticleRecommendService service = service("dify-key", "inputs", "object", "blocking");
        stubRecommend("u1");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        JsonNode empty = objectMapper.readTree("{\"data\":{\"outputs\":{\"recommendations\":[]}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(empty);
        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertTrue(((Number) result.get("phase")).intValue() <= 3);
    }

    @Test
    void recommend_parseDifyFromRootOutputsAndTextFallback() throws Exception {
        properties.setPhase4DifyEnabled(true);
        ArticleRecommendService service = service("dify-key", "flat", "object", "blocking");
        stubRecommend("u1");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        JsonNode rootOutputs = objectMapper.readTree("""
                {"outputs":{"recommendations":[{"article_id":"art_1","reason":"根输出"}]}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(rootOutputs);
        assertEquals(4, service.recommend("u1", 1, 10).get("phase"));

        JsonNode textOnly = objectMapper.readTree("""
                {"outputs":{"text":"{\\"recommendations\\":[{\\"articleId\\":\\"art_1\\",\\"recReason\\":\\"文本\\"}]}"}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(textOnly);
        assertEquals(4, service.recommend("u1", 1, 10).get("phase"));
    }

    @Test
    void recommend_parseDifyArticleInfoTextualAndInvalidJson() throws Exception {
        properties.setPhase4DifyEnabled(true);
        ArticleRecommendService service = service("dify-key", "flat", "object", "blocking");
        stubRecommend("u1");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        JsonNode badArticleInfo = objectMapper.readTree("""
                {"data":{"outputs":{"article_info":"not-json","recommendations":[{"article_id":"art_1"}]}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(badArticleInfo);
        assertDoesNotThrow(() -> service.recommend("u1", 1, 10));

        JsonNode articleInfoArray = objectMapper.readTree("""
                {"data":{"outputs":{"article_info":"{\\"recommendations\\":[{\\"article_id\\":\\"art_1\\",\\"rec_reason\\":\\"JSON文本\\"}]}"}}}
                """);
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(articleInfoArray);
        assertEquals(4, service.recommend("u1", 1, 10).get("phase"));
    }

    @Test
    void recommend_phase3SkipsBlankFingerprintAndLowSimilarity() {
        properties.setPhase4DifyEnabled(false);
        properties.setPhase3Enabled(true);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());

        ArticleCandidate blankFp = candidate("art_blank", 2);
        blankFp.setTextFingerprint("  ");
        ArticleCandidate lowSim = candidate("art_low", 2);
        lowSim.setTextFingerprint("完全不同内容");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(blankFp, lowSim));
        when(recommendMapper.findTagsByArticleId(anyString())).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of("art_read"));
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        ArticleCandidate read = candidate("art_read", 2);
        read.setTextFingerprint("abc def ghi");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(blankFp, lowSim, read));
        assertDoesNotThrow(() -> service.recommend("u1", 1, 10));
    }

    @Test
    void recommend_profileWeightsForAllDiabetesTypesAndRiskLevels() {
        properties.setPhase2Enabled(false);
        properties.setPhase3Enabled(false);
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(candidate("art_1", 2)));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds(anyString(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds(anyString())).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser(anyString(), anyInt())).thenReturn(List.of());

        for (int type : new int[]{0, 1, 2, 3, 4, 9}) {
            when(healthServiceClient.getLatestHealthProfile("u1", "internal-key"))
                    .thenReturn(Map.of("diabetesType", type, "bmi", 22));
            service.recommend("u1", 1, 10);
        }
        when(healthServiceClient.getLatestHealthProfile("u1", "internal-key"))
                .thenReturn(Map.of("diabetesType", 3, "bmi", 26));
        when(healthServiceClient.getLatestRiskAssessment("u1", "internal-key"))
                .thenReturn(Map.of("riskLevel", "low"));
        service.recommend("u1", 1, 10);
        when(healthServiceClient.getLatestRiskAssessment("u1", "internal-key"))
                .thenReturn(Map.of("riskLevel", "medium"));
        service.recommend("u1", 1, 10);
    }

    @Test
    void recommend_phase2DisabledAndCoCountBelowThreshold() {
        properties.setPhase2Enabled(false);
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(candidate("art_1", 2)));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
        service.recommend("u1", 1, 10);

        properties.setPhase2Enabled(true);
        properties.setPhase2MinCoReaders(5);
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(Map.of("articleId", "art_1", "coCount", 1)));
        service.recommend("u1", 1, 10);
    }

    @Test
    void recommend_persistedNonNumericFieldsAndRecReasonFallback() {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("articleId", "art_1");
        row.put("title", "标题");
        row.put("summary", "摘要");
        row.put("category", "bad");
        row.put("viewCount", "bad");
        row.put("recScore", "bad");
        row.put("recReason", null);
        row.put("recPhase", "bad");
        row.put("coverImageId", "https://cdn/c.jpg");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of(row));
        Map<String, Object> result = service.recommend("u1", 1, 10);
        assertEquals(1, result.get("total"));
    }

    @Test
    void popularRecommendFreshnessBranches() {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        when(valueOps.get(anyString())).thenReturn(null);

        ArticleCandidate fresh = candidate("fresh", 1);
        fresh.setPublishedAt(LocalDateTime.now());
        fresh.setViewCount(null);
        fresh.setFavoriteCount(null);
        ArticleCandidate month = candidate("month", 1);
        month.setPublishedAt(LocalDateTime.now().minusDays(20));
        ArticleCandidate old = candidate("old", 1);
        old.setPublishedAt(LocalDateTime.now().minusDays(90));
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(old, month, fresh));
        when(recommendMapper.findTagsByArticleId(anyString())).thenReturn(List.of());
        service.popularRecommend(1, 10);
    }

    @Test
    void related_withoutUserSkipsPhase2() {
        properties.setPhase4DifyEnabled(false);
        ArticleCandidate base = candidate("art_1", 2);
        base.setTags(new ArrayList<>(List.of("饮食")));
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(base));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRelatedCandidates("art_1", 2, 15)).thenReturn(List.of(candidate("art_2", 2)));
        when(recommendMapper.findTagsByArticleId("art_2")).thenReturn(List.of());
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        service.related("art_1", " ", 5);
        verify(recommendMapper, never()).findCoReadArticles(anyString(), anyList(), anyInt());
    }

    @Test
    void recommend_blankUserIdDelegatesPopular() {
        properties.setPhase4DifyEnabled(false);
        ArticleRecommendService service = service("", "flat", "object", "blocking");
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(candidate("art_1", 1)));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        assertEquals("popular", service.recommend("  ", 1, 10).get("strategy"));
    }

    private void stubRecommend(String userId) {
        ArticleCandidate c1 = candidate("art_1", 2);
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds(userId, 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds(userId)).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser(userId, 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);
    }

    private static ArticleCandidate candidate(String id, int category) {
        ArticleCandidate candidate = new ArticleCandidate();
        candidate.setArticleId(id);
        candidate.setTitle("标题-" + id);
        candidate.setSummary("摘要-" + id);
        candidate.setCategory(category);
        candidate.setViewCount(10);
        candidate.setFavoriteCount(1);
        candidate.setPublishedAt(LocalDateTime.now().minusDays(3));
        return candidate;
    }
}
