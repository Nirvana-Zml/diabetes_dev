package com.diabetes.article.service;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.milvus.ArticleEmbeddingService;
import com.diabetes.article.milvus.MilvusArticleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MilvusArticleSearchServiceTest {

    private MilvusArticleSearchService milvusSearchService;
    private MilvusProperties milvusProperties;
    private RecommendProperties recommendProperties;
    private MilvusArticleClient milvusClient;
    private ArticleEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        milvusProperties = new MilvusProperties();
        recommendProperties = new RecommendProperties();
        milvusClient = mock(MilvusArticleClient.class);
        embeddingService = mock(ArticleEmbeddingService.class);

        milvusSearchService = new MilvusArticleSearchService(
                milvusProperties, recommendProperties, milvusClient, embeddingService);
    }

    @Test
    void testIsAvailableMilvusDisabled() {
        recommendProperties.setMilvusEnabled(false);

        assertFalse(milvusSearchService.isAvailable());
    }

    @Test
    void testIsAvailableMilvusNotReady() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(false);

        assertFalse(milvusSearchService.isAvailable());
    }

    @Test
    void testIsAvailableSuccess() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);

        assertTrue(milvusSearchService.isAvailable());
    }

    @Test
    void testSearchSimilarNotAvailable() {
        recommendProperties.setMilvusEnabled(false);

        Map<String, Double> result = milvusSearchService.searchSimilar("test", List.of("art_01"), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchSimilarEmptyQuery() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);

        Map<String, Double> result = milvusSearchService.searchSimilar("", List.of("art_01"), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchSimilarNullQuery() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);

        Map<String, Double> result = milvusSearchService.searchSimilar(null, List.of("art_01"), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchSimilarBlankQuery() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);

        Map<String, Double> result = milvusSearchService.searchSimilar("   ", List.of("art_01"), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchSimilarSuccess() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), any())).thenReturn(
                Map.of("art_01", 0.8, "art_02", 0.6)
        );

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01", "art_02"), 10);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
    }

    @Test
    void testSearchSimilarWithEmptyCandidateIds() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), eq(null))).thenReturn(
                Map.of("art_01", 0.8)
        );

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of(), 10);
        assertFalse(result.isEmpty());
    }

    @Test
    void testSearchSimilarWithNullCandidateIds() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), eq(null))).thenReturn(
                Map.of("art_01", 0.8)
        );

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", null, 10);
        assertFalse(result.isEmpty());
    }

    @Test
    void testSearchSimilarFirstSearchEmpty() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), any())).thenReturn(
                Map.of(),
                Map.of("art_01", 0.8)
        );

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01"), 10);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void testSearchSimilarEmptyResult() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), any())).thenReturn(Map.of());

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01"), 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchSimilarWithFilterSecondSearch() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), eq(null))).thenReturn(Map.of());
        when(milvusClient.search(any(float[].class), anyInt(), any(java.util.Set.class))).thenReturn(Map.of("art_01", 0.8));

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01"), 10);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void testSearchSimilarHitsEmptyWithFilter() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), any(java.util.Set.class)))
                .thenReturn(Map.of())
                .thenReturn(Map.of("art_01", 0.8));

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01"), 10);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        verify(milvusClient, times(2)).search(any(float[].class), anyInt(), any(java.util.Set.class));
    }

    @Test
    void testSearchSimilarHitsEmptyWithFilterSecondSearchAlsoEmpty() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), any(java.util.Set.class))).thenReturn(Map.of());

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01"), 10);
        assertTrue(result.isEmpty());
        verify(milvusClient, times(2)).search(any(float[].class), anyInt(), any(java.util.Set.class));
    }

    @Test
    void testSearchSimilarHitsNotEmptyWithFilter() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), any(java.util.Set.class)))
                .thenReturn(Map.of("art_01", 0.8, "art_02", 0.6));

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of("art_01", "art_02"), 10);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        verify(milvusClient, times(1)).search(any(float[].class), anyInt(), any(java.util.Set.class));
    }

    @Test
    void testSearchSimilarEmptyHitsWithEmptyFilter() {
        recommendProperties.setMilvusEnabled(true);
        when(milvusClient.isReady()).thenReturn(true);
        when(embeddingService.embed("test query")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(milvusClient.search(any(float[].class), anyInt(), eq(null))).thenReturn(Map.of());

        Map<String, Double> result = milvusSearchService.searchSimilar("test query", List.of(), 10);
        assertTrue(result.isEmpty());
        verify(milvusClient, times(1)).search(any(float[].class), anyInt(), eq(null));
    }
}
