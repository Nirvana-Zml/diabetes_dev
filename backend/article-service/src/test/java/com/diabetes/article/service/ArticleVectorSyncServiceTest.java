package com.diabetes.article.service;

import com.diabetes.article.entity.Article;
import com.diabetes.article.entity.ArticleCandidate;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.article.mapper.RecommendMapper;
import com.diabetes.article.milvus.ArticleEmbeddingService;
import com.diabetes.article.milvus.MilvusArticleClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleVectorSyncServiceTest {

    private ArticleVectorSyncService vectorSyncService;
    private MilvusArticleClient milvusClient;
    private ArticleEmbeddingService embeddingService;
    private ArticleMapper articleMapper;
    private RecommendMapper recommendMapper;

    @BeforeEach
    void setUp() {
        milvusClient = mock(MilvusArticleClient.class);
        embeddingService = mock(ArticleEmbeddingService.class);
        articleMapper = mock(ArticleMapper.class);
        recommendMapper = mock(RecommendMapper.class);

        vectorSyncService = new ArticleVectorSyncService(
                milvusClient, embeddingService, articleMapper, recommendMapper);
    }

    @Test
    void testSyncArticleMilvusNotReady() {
        when(milvusClient.isReady()).thenReturn(false);

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
        verify(milvusClient, never()).upsert(any(), anyInt(), any());
        verify(milvusClient, never()).delete(any());
    }

    @Test
    void testSyncArticleArticleNotFound() {
        when(milvusClient.isReady()).thenReturn(true);
        when(articleMapper.findById("art_01")).thenReturn(null);

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
        verify(milvusClient).delete("art_01");
    }

    @Test
    void testSyncArticleNotPublished() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(1);
        when(articleMapper.findById("art_01")).thenReturn(article);

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
        verify(milvusClient).delete("art_01");
    }

    @Test
    void testSyncArticleStatusNull() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(null);
        when(articleMapper.findById("art_01")).thenReturn(article);

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
        verify(milvusClient).delete("art_01");
    }

    @Test
    void testSyncArticleSuccess() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(3);
        article.setCategory(2);
        when(articleMapper.findById("art_01")).thenReturn(article);
        when(recommendMapper.findTagsByArticleId("art_01")).thenReturn(List.of("标签A", "标签B"));
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
        verify(milvusClient).upsert(eq("art_01"), eq(2), any(float[].class));
        verify(recommendMapper).upsertEmbedding(eq("art_01"), anyString());
    }

    @Test
    void testSyncArticleCategoryNull() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(3);
        article.setCategory(null);
        when(articleMapper.findById("art_01")).thenReturn(article);
        when(recommendMapper.findTagsByArticleId("art_01")).thenReturn(List.of());
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
        verify(milvusClient).upsert(eq("art_01"), eq(1), any(float[].class));
    }

    @Test
    void testSyncAllPublishedMilvusNotReady() {
        when(milvusClient.isReady()).thenReturn(false);

        assertDoesNotThrow(() -> vectorSyncService.syncAllPublished());
        verify(recommendMapper, never()).findPublishedCandidates(anyInt());
    }

    @Test
    void testSyncAllPublishedSuccess() {
        when(milvusClient.isReady()).thenReturn(true);
        ArticleCandidate c1 = new ArticleCandidate();
        c1.setArticleId("art_01");
        ArticleCandidate c2 = new ArticleCandidate();
        c2.setArticleId("art_02");
        when(recommendMapper.findPublishedCandidates(500)).thenReturn(List.of(c1, c2));

        Article article1 = new Article();
        article1.setArticleId("art_01");
        article1.setStatus(3);
        article1.setCategory(1);
        Article article2 = new Article();
        article2.setArticleId("art_02");
        article2.setStatus(3);
        article2.setCategory(2);

        when(articleMapper.findById("art_01")).thenReturn(article1);
        when(articleMapper.findById("art_02")).thenReturn(article2);
        when(recommendMapper.findTagsByArticleId(any())).thenReturn(List.of());
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});

        assertDoesNotThrow(() -> vectorSyncService.syncAllPublished());
        verify(milvusClient, times(2)).upsert(any(), anyInt(), any(float[].class));
    }

    @Test
    void testSyncAllPublishedWithException() {
        when(milvusClient.isReady()).thenReturn(true);
        ArticleCandidate c1 = new ArticleCandidate();
        c1.setArticleId("art_01");
        ArticleCandidate c2 = new ArticleCandidate();
        c2.setArticleId("art_02");
        when(recommendMapper.findPublishedCandidates(500)).thenReturn(List.of(c1, c2));

        Article article1 = new Article();
        article1.setArticleId("art_01");
        article1.setStatus(3);
        when(articleMapper.findById("art_01")).thenReturn(article1);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("Embed error"));

        Article article2 = new Article();
        article2.setArticleId("art_02");
        article2.setStatus(3);
        when(articleMapper.findById("art_02")).thenReturn(article2);

        assertDoesNotThrow(() -> vectorSyncService.syncAllPublished());
    }

    @Test
    void testRemoveArticleMilvusNotReady() {
        when(milvusClient.isReady()).thenReturn(false);

        assertDoesNotThrow(() -> vectorSyncService.removeArticle("art_01"));
        verify(milvusClient, never()).delete(any());
    }

    @Test
    void testRemoveArticleSuccess() {
        when(milvusClient.isReady()).thenReturn(true);

        assertDoesNotThrow(() -> vectorSyncService.removeArticle("art_01"));
        verify(milvusClient).delete("art_01");
    }

    @Test
    void testBuildFingerprintWithNullTitle() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(3);
        article.setTitle(null);
        article.setSummary("摘要");
        when(articleMapper.findById("art_01")).thenReturn(article);
        when(recommendMapper.findTagsByArticleId("art_01")).thenReturn(List.of("标签"));
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
    }

    @Test
    void testBuildFingerprintWithNullSummary() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(3);
        article.setTitle("标题");
        article.setSummary(null);
        when(articleMapper.findById("art_01")).thenReturn(article);
        when(recommendMapper.findTagsByArticleId("art_01")).thenReturn(List.of("标签"));
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
    }

    @Test
    void testBuildFingerprintWithNullTags() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(3);
        article.setTitle("标题");
        article.setSummary("摘要");
        when(articleMapper.findById("art_01")).thenReturn(article);
        when(recommendMapper.findTagsByArticleId("art_01")).thenReturn(null);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
    }

    @Test
    void testBuildFingerprintWithEmptyAll() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(3);
        article.setTitle(null);
        article.setSummary(null);
        when(articleMapper.findById("art_01")).thenReturn(article);
        when(recommendMapper.findTagsByArticleId("art_01")).thenReturn(null);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});

        assertDoesNotThrow(() -> vectorSyncService.syncArticle("art_01"));
    }
}
