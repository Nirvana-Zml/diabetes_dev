package com.diabetes.article.service;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.milvus.ArticleEmbeddingService;
import com.diabetes.article.milvus.MilvusArticleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MilvusArticleSearchServiceTest {

    private final MilvusProperties milvusProperties = new MilvusProperties();
    private final RecommendProperties recommendProperties = new RecommendProperties();
    private final MilvusArticleClient milvusClient = mock(MilvusArticleClient.class);
    private final ArticleEmbeddingService embeddingService = mock(ArticleEmbeddingService.class);

    private MilvusArticleSearchService service;

    @BeforeEach
    void setUp() {
        service = new MilvusArticleSearchService(
                milvusProperties, recommendProperties, milvusClient, embeddingService);
    }

    @Test
    void isAvailable_requiresMilvusEnabledAndReady() {
        recommendProperties.setMilvusEnabled(false);
        when(milvusClient.isReady()).thenReturn(true);
        assertFalse(service.isAvailable());

        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(false);
        assertFalse(service.isAvailable());

        when(milvusClient.isReady()).thenReturn(true);
        assertTrue(service.isAvailable());
    }

    @Test
    void searchSimilar_nullCandidateIdsAndNullInterestText() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        assertTrue(service.searchSimilar(null, null, 10).isEmpty());
        when(embeddingService.embed("x")).thenReturn(new float[]{0.1f});
        when(milvusClient.search(any(float[].class), eq(10), isNull())).thenReturn(Map.of("a", 1.0));
        assertFalse(service.searchSimilar("x", null, 10).isEmpty());
    }

    @Test
    void searchSimilar_skipsRetryWhenFilterEmpty() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("x")).thenReturn(new float[]{0.1f});
        when(milvusClient.search(any(float[].class), eq(10), isNull())).thenReturn(Map.of());
        assertTrue(service.searchSimilar("x", List.of(), 10).isEmpty());
        verify(milvusClient, times(1)).search(any(float[].class), anyInt(), isNull());
    }

    @Test
    void searchSimilar_returnsEmptyWhenAvailableButBlankText() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        assertTrue(service.searchSimilar("  ", List.of("art_1"), 10).isEmpty());
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void searchSimilar_unavailableWithValidTextReturnsEmpty() {
        recommendProperties.setMilvusEnabled(false);
        when(milvusClient.isReady()).thenReturn(true);
        assertTrue(service.searchSimilar("valid interest text", List.of("art_1"), 10).isEmpty());
    }

    @Test
    void searchSimilar_returnsEmptyWhenInterestTextNull() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        assertTrue(service.searchSimilar(null, List.of("art_1"), 10).isEmpty());
    }

    @Test
    void searchSimilar_withEmptyCandidateFilter() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("兴趣")).thenReturn(new float[]{0.2f});
        when(milvusClient.search(any(float[].class), eq(10), isNull()))
                .thenReturn(Map.of("art_1", 0.6));

        Map<String, Double> hits = service.searchSimilar("兴趣", List.of(), 10);

        assertEquals(0.6, hits.get("art_1"));
    }

    @Test
    void searchSimilar_returnsEmptyWhenUnavailableOrBlankText() {
        recommendProperties.setMilvusEnabled(false);
        assertTrue(service.searchSimilar("兴趣文本", List.of("art_1"), 10).isEmpty());
        assertTrue(service.searchSimilar(" ", List.of("art_1"), 10).isEmpty());
    }

    @Test
    void searchSimilar_queriesMilvusWithEmbedding() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("兴趣文本")).thenReturn(new float[]{0.1f});
        when(milvusClient.search(any(float[].class), eq(10), eq(Set.of("art_1", "art_2"))))
                .thenReturn(Map.of("art_1", 0.9));

        Map<String, Double> hits = service.searchSimilar("兴趣文本", List.of("art_1", "art_2"), 10);

        assertEquals(0.9, hits.get("art_1"));
    }

    @Test
    void searchSimilar_retriesWithLargerTopKWhenFirstSearchEmpty() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("兴趣")).thenReturn(new float[]{0.2f});
        when(milvusClient.search(any(float[].class), eq(10), eq(Set.of("art_1"))))
                .thenReturn(Map.of());
        when(milvusClient.search(any(float[].class), eq(50), eq(Set.of("art_1"))))
                .thenReturn(Map.of("art_1", 0.7));

        Map<String, Double> hits = service.searchSimilar("兴趣", List.of("art_1"), 10);

        assertEquals(0.7, hits.get("art_1"));
        verify(milvusClient, times(2)).search(any(float[].class), anyInt(), any());
    }
}
