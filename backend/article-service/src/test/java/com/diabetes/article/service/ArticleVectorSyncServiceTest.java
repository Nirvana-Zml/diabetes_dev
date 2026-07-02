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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleVectorSyncServiceTest {

    private final MilvusArticleClient milvusClient = mock(MilvusArticleClient.class);
    private final ArticleEmbeddingService embeddingService = mock(ArticleEmbeddingService.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final RecommendMapper recommendMapper = mock(RecommendMapper.class);

    private ArticleVectorSyncService service;

    @BeforeEach
    void setUp() {
        service = new ArticleVectorSyncService(milvusClient, embeddingService, articleMapper, recommendMapper);
    }

    @Test
    void syncArticle_skipsWhenMilvusNotReady() {
        when(milvusClient.isReady()).thenReturn(false);

        service.syncArticle("art_1");

        verifyNoInteractions(articleMapper);
    }

    @Test
    void syncArticle_deletesWhenArticleMissing() {
        when(milvusClient.isReady()).thenReturn(true);
        when(articleMapper.findById("art_1")).thenReturn(null);
        service.syncArticle("art_1");
        verify(milvusClient).delete("art_1");
    }

    @Test
    void syncArticle_buildsFingerprintWithNullTitle() {
        when(milvusClient.isReady()).thenReturn(true);
        Article published = publishedArticle();
        published.setTitle(null);
        published.setSummary(null);
        when(articleMapper.findById("art_1")).thenReturn(published);
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(null);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        service.syncArticle("art_1");
        verify(recommendMapper).upsertEmbedding(eq("art_1"), eq(""));
    }

    @Test
    void syncArticle_deletesWhenStatusNull() {
        when(milvusClient.isReady()).thenReturn(true);
        Article article = publishedArticle();
        article.setStatus(null);
        when(articleMapper.findById("art_1")).thenReturn(article);
        service.syncArticle("art_1");
        verify(milvusClient).delete("art_1");
    }

    @Test
    void syncArticle_deletesWhenNotPublished() {
        when(milvusClient.isReady()).thenReturn(true);
        Article draft = new Article();
        draft.setArticleId("art_1");
        draft.setStatus(1);
        when(articleMapper.findById("art_1")).thenReturn(draft);

        service.syncArticle("art_1");

        verify(milvusClient).delete("art_1");
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void syncAllPublished_skipsWhenMilvusNotReady() {
        when(milvusClient.isReady()).thenReturn(false);

        service.syncAllPublished();

        verify(recommendMapper, never()).findPublishedCandidates(anyInt());
    }

    @Test
    void syncAllPublished_logsFailureForSingleArticle() {
        when(milvusClient.isReady()).thenReturn(true);
        ArticleCandidate candidate = new ArticleCandidate();
        candidate.setArticleId("art_1");
        when(recommendMapper.findPublishedCandidates(500)).thenReturn(List.of(candidate));
        when(articleMapper.findById("art_1")).thenReturn(publishedArticle());
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embed fail"));

        assertDoesNotThrow(() -> service.syncAllPublished());
    }

    @Test
    void syncArticle_usesDefaultCategoryWhenNull() {
        when(milvusClient.isReady()).thenReturn(true);
        Article published = publishedArticle();
        published.setCategory(null);
        when(articleMapper.findById("art_1")).thenReturn(published);
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(null);
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});

        service.syncArticle("art_1");

        verify(milvusClient).upsert(eq("art_1"), eq(1), any(float[].class));
    }

    @Test
    void syncArticle_upsertsPublishedArticle() {
        when(milvusClient.isReady()).thenReturn(true);
        when(articleMapper.findById("art_1")).thenReturn(publishedArticle());
        when(recommendMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        service.syncArticle("art_1");

        verify(milvusClient).upsert(eq("art_1"), eq(2), any(float[].class));
        verify(recommendMapper).upsertEmbedding(eq("art_1"), contains("标题"));
    }

    private Article publishedArticle() {
        Article published = new Article();
        published.setArticleId("art_1");
        published.setStatus(3);
        published.setTitle("标题");
        published.setSummary("摘要");
        published.setCategory(2);
        return published;
    }

    @Test
    void syncAllPublished_iteratesCandidates() {
        when(milvusClient.isReady()).thenReturn(true);
        ArticleCandidate candidate = new ArticleCandidate();
        candidate.setArticleId("art_1");
        when(recommendMapper.findPublishedCandidates(500)).thenReturn(List.of(candidate));
        when(articleMapper.findById("art_1")).thenReturn(null);

        service.syncAllPublished();

        verify(recommendMapper).findPublishedCandidates(500);
        verify(milvusClient).delete("art_1");
    }

    @Test
    void removeArticle_deletesWhenReady() {
        when(milvusClient.isReady()).thenReturn(true);

        service.removeArticle("art_1");

        verify(milvusClient).delete("art_1");
    }

    @Test
    void removeArticle_skipsWhenNotReady() {
        when(milvusClient.isReady()).thenReturn(false);

        service.removeArticle("art_1");

        verify(milvusClient, never()).delete(anyString());
    }
}
