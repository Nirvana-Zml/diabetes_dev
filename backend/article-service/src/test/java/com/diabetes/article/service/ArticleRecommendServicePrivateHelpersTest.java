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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleRecommendServicePrivateHelpersTest {

    private final RecommendMapper recommendMapper = mock(RecommendMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final RecommendProperties properties = new RecommendProperties();
    private final MilvusArticleSearchService milvusArticleSearchService = mock(MilvusArticleSearchService.class);

    private ArticleRecommendService service;

    @BeforeEach
    void setUp() {
        service = new ArticleRecommendService(
                recommendMapper, redis, objectMapper, mock(MinioStorageService.class),
                mock(HealthServiceClient.class), mock(DifyClient.class), properties,
                milvusArticleSearchService, "http://dify", "key", "flat", "object", "blocking", "k");
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void privateHelpers_coverEdgeBranches() throws Exception {
        assertEquals(Set.of(), invoke("tokenize", new Class[]{String.class}, " "));
        assertEquals(Set.of(), invoke("tokenize", new Class[]{String.class}, new Object[]{null}));
        assertTrue(((Set<?>) invoke("tokenize", new Class[]{String.class}, "a bb ccc")).contains("bb"));

        assertEquals(0.0, (double) invoke("jaccardSimilarity",
                new Class[]{Set.class, Set.class}, Set.of(), Set.of("x")));
        assertEquals(0.0, (double) invoke("jaccardSimilarity",
                new Class[]{Set.class, Set.class}, Set.of(), Set.of()));

        assertEquals("", invoke("truncate", new Class[]{String.class, int.class}, null, 5));
        assertEquals("abc", invoke("truncate", new Class[]{String.class, int.class}, "abcdef", 3));
        assertEquals("detail", invoke("blankToDefault", new Class[]{String.class, String.class}, null, "detail"));
        assertEquals("list", invoke("blankToDefault", new Class[]{String.class, String.class}, "list", "detail"));
        assertEquals(Set.of(), invoke("toSet", new Class[]{List.class}, new Object[]{null}));

        assertEquals(0.0, (double) invoke("freshnessScore",
                new Class[]{ArticleCandidate.class}, candidate(null)));
        assertEquals(0.0, (double) invoke("tagOverlapScore",
                new Class[]{Set.class, List.class}, Set.of(), List.of()));

        JsonNode missing = (JsonNode) invoke("extractRecommendationsList",
                new Class[]{JsonNode.class}, new Object[]{null});
        assertTrue(missing.isMissingNode());

        assertNull(invoke("firstText",
                new Class[]{JsonNode.class, String[].class},
                objectMapper.createObjectNode(), new String[]{"missing"}));
    }

    @Test
    void writeCacheFailureIsIgnored() throws Exception {
        doThrow(new RuntimeException("write fail")).when(valueOps)
                .set(anyString(), anyString(), any());
        assertDoesNotThrow(() -> invoke("writeCache",
                new Class[]{String.class, Map.class}, "k", Map.of("a", 1)));
    }

    @Test
    void phase3MilvusHitWithNonPositiveSimilarity() {
        properties.setPhase4DifyEnabled(false);
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        ArticleCandidate c1 = candidate(LocalDateTime.now());
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of("art_fav"));
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of("art_fav"));
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(true);
        when(milvusArticleSearchService.searchSimilar(anyString(), anyList(), anyInt()))
                .thenReturn(Map.of("art_1", 0.0));

        ArticleCandidate fav = candidate(LocalDateTime.now());
        fav.setArticleId("art_fav");
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1, fav));
        assertDoesNotThrow(() -> service.recommend("u1", 1, 10));
    }

    @Test
    void parseDifyInvalidTextJson() throws Exception {
        properties.setPhase4DifyEnabled(true);
        when(valueOps.get(anyString())).thenReturn(null);
        when(recommendMapper.findActiveRecommendations("u1")).thenReturn(List.of());
        ArticleCandidate c1 = candidate(LocalDateTime.now());
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(c1));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        when(recommendMapper.findCoReadArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(recommendMapper.findCoFavoriteArticles(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(milvusArticleSearchService.isAvailable()).thenReturn(false);

        DifyClient difyClient = mock(DifyClient.class);
        service = new ArticleRecommendService(
                recommendMapper, redis, objectMapper, mock(MinioStorageService.class),
                mock(HealthServiceClient.class), difyClient, properties,
                milvusArticleSearchService, "http://dify", "key", "flat", "object", "blocking", "k");
        JsonNode badText = objectMapper.readTree("{\"data\":{\"outputs\":{\"text\":\"not-json\"}}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString())).thenReturn(badText);
        assertDoesNotThrow(() -> service.recommend("u1", 1, 10));
    }

    @Test
    void relatedWithUserButPhase2Disabled() {
        properties.setPhase2Enabled(false);
        ArticleCandidate base = candidate(LocalDateTime.now());
        base.setArticleId("art_1");
        base.setTags(new ArrayList<>(List.of("饮食")));
        when(recommendMapper.findPublishedCandidates(anyInt())).thenReturn(List.of(base));
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(recommendMapper.findRelatedCandidates("art_1", 2, 15)).thenReturn(List.of(candidate(LocalDateTime.now())));
        when(recommendMapper.findTagsByArticleId("art_2")).thenReturn(List.of());
        when(recommendMapper.findRecentReadArticleIds("u1", 7)).thenReturn(List.of());
        when(recommendMapper.findFavoriteArticleIds("u1")).thenReturn(List.of());
        when(recommendMapper.findCategoryWeightsByUser("u1", 30)).thenReturn(List.of());
        service.related("art_1", "u1", 3);
        verify(recommendMapper, never()).findCoReadArticles(anyString(), anyList(), anyInt());
    }

    @Test
    void buildFingerprintWithNullFields() throws Exception {
        ArticleCandidate c = new ArticleCandidate();
        c.setTitle(null);
        c.setSummary(null);
        Field tagsField = ArticleCandidate.class.getDeclaredField("tags");
        tagsField.setAccessible(true);
        tagsField.set(c, null);
        String fp = (String) invoke("buildFingerprint", new Class[]{ArticleCandidate.class}, c);
        assertEquals("", fp);
    }

    private ArticleCandidate candidate(LocalDateTime publishedAt) {
        ArticleCandidate c = new ArticleCandidate();
        c.setArticleId("art_1");
        c.setTitle("标题");
        c.setSummary("摘要");
        c.setCategory(2);
        c.setViewCount(1);
        c.setPublishedAt(publishedAt);
        return c;
    }

    private Object invoke(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = ArticleRecommendService.class.getDeclaredMethod(name, paramTypes);
        method.setAccessible(true);
        return method.invoke(service, args);
    }
}
