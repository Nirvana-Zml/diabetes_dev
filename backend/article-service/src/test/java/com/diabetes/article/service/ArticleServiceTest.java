package com.diabetes.article.service;

import com.diabetes.article.entity.Article;
import com.diabetes.article.mapper.ArticleMapper;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final ArticleRecommendService articleRecommendService = mock(ArticleRecommendService.class);
    private final ArticleVectorSyncService articleVectorSyncService = mock(ArticleVectorSyncService.class);

    private ArticleService service;

    @BeforeEach
    void setUp() {
        service = new ArticleService(
                articleMapper, redis, objectMapper, minioStorageService,
                articleRecommendService, articleVectorSyncService,
                "http://dify.local", "", "");
    }

    @Test
    void recommend_delegatesPopularStrategy() {
        Map<String, Object> expected = Map.of("strategy", "popular");
        when(articleRecommendService.popularRecommend(1, 10)).thenReturn(expected);

        assertEquals(expected, service.recommend("u1", 1, 10, "popular"));
        verify(articleRecommendService).popularRecommend(1, 10);
        verify(articleRecommendService, never()).recommend(anyString(), anyInt(), anyInt());
    }

    @Test
    void search_rejectsBlankKeyword() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.search("  ", 1, 10));
        assertEquals(400, ex.getCode());
    }

    @Test
    void search_returnsResults() {
        Article article = publishedArticle("art_1");
        when(articleMapper.searchPublished("糖尿病", 0, 10)).thenReturn(List.of(article));
        when(articleMapper.countSearch("糖尿病")).thenReturn(1);
        when(minioStorageService.buildArticleCoverUrl("art_1")).thenReturn("http://minio/art_1.jpg");

        Map<String, Object> result = service.search("糖尿病", 1, 10);

        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articles = (List<Map<String, Object>>) result.get("articles");
        assertEquals("art_1", articles.get(0).get("articleId"));
    }

    @Test
    void detail_incrementsViewAndReturnsFavoriteStatus() {
        Article article = publishedArticle("art_1");
        article.setViewCount(5);
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        when(articleMapper.findFavoriteStatus("u1", "art_1")).thenReturn(1);
        when(minioStorageService.buildArticleCoverUrl("art_1")).thenReturn("http://minio/art_1.jpg");
        when(redis.keys(anyString())).thenReturn(Set.of());

        Map<String, Object> result = service.detail("art_1", "u1");

        assertEquals("art_1", result.get("articleId"));
        assertEquals(true, result.get("favorited"));
        assertEquals(List.of("饮食"), result.get("tags"));
        verify(articleMapper).incrementViewCount("art_1");
    }

    @Test
    void detail_notFound() {
        when(articleMapper.findById("missing")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail("missing", null));
        assertEquals(404, ex.getCode());
    }

    @Test
    void toggleFavorite_addAndRemove() {
        Article article = publishedArticle("art_1");
        when(articleMapper.findById("art_1")).thenReturn(article);
        when(articleMapper.findFavoriteStatus("u1", "art_1")).thenReturn(null, 1);
        doNothing().when(articleRecommendService).invalidateUserRecommendCache("u1");

        Map<String, Object> added = service.toggleFavorite("u1", "art_1");
        assertEquals(true, added.get("favorited"));
        verify(articleMapper).adjustFavoriteCount("art_1", 1);

        Map<String, Object> removed = service.toggleFavorite("u1", "art_1");
        assertEquals(false, removed.get("favorited"));
        verify(articleMapper).adjustFavoriteCount("art_1", -1);
    }

    @Test
    void list_usesCacheWhenPresent() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        Map<String, Object> cached = Map.of("articles", List.of(), "total", 0);
        when(valueOps.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        Map<String, Object> result = service.list(1, 1, 10);

        assertEquals(0, result.get("total"));
        verifyNoInteractions(articleMapper);
    }

    @Test
    void list_loadsFromDatabaseAndWritesCache() {
        Article article = publishedArticle("art_1");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(articleMapper.findPublished(1, 0, 10)).thenReturn(List.of(article));
        when(articleMapper.countPublished(1)).thenReturn(1);
        when(minioStorageService.buildArticleCoverUrl("art_1")).thenReturn("http://minio/art_1.jpg");

        Map<String, Object> result = service.list(1, 1, 10);

        assertEquals(1, result.get("total"));
        verify(valueOps).set(anyString(), contains("art_1"), any());
    }

    @Test
    void createUpdateDeleteAndReview() {
        Map<String, Object> body = Map.of(
                "title", "标题",
                "content", "内容",
                "summary", "摘要",
                "category", "diet",
                "tags", List.of("饮食", "饮食", "  "));
        when(articleMapper.findById("art_1")).thenReturn(
                draftArticle("art_1"),
                draftArticle("art_1"),
                draftArticle("art_1"),
                pendingArticle("art_1"));
        when(minioStorageService.buildArticleCoverUrl(anyString())).thenReturn("http://minio/cover.jpg");
        when(redis.keys(anyString())).thenReturn(Set.of());

        Map<String, Object> created = service.create(body);
        assertEquals("draft", created.get("status"));
        verify(articleMapper).insert(any(Article.class));
        verify(articleMapper).insertTag(anyString(), anyString(), eq("饮食"));

        Map<String, Object> updated = service.update("art_1", body);
        assertEquals("draft", updated.get("status"));
        verify(articleMapper).update(any(Article.class));

        service.delete("art_1");
        verify(articleMapper).softDelete("art_1");
        verify(articleVectorSyncService).removeArticle("art_1");

        Map<String, Object> submitted = service.submitReview("art_1");
        assertEquals("pending", submitted.get("status"));
        verify(articleMapper).updateStatus("art_1", 2);

        Map<String, Object> approved = service.review("art_1", "approve", null);
        assertEquals("published", approved.get("status"));
        verify(articleVectorSyncService).syncArticle("art_1");
    }

    @Test
    void review_rejectRequiresReason() {
        Article article = pendingArticle("art_1");
        when(articleMapper.findById("art_1")).thenReturn(article);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.review("art_1", "reject", " "));
        assertEquals(400, ex.getCode());

        Map<String, Object> rejected = service.review("art_1", "reject", "内容不完整");
        assertEquals("rejected", rejected.get("status"));
    }

    @Test
    void uploadCover_validatesFile() {
        when(articleMapper.findById("art_1")).thenReturn(draftArticle("art_1"));

        assertEquals(400, assertThrows(BusinessException.class, () -> service.uploadCover("art_1", null)).getCode());

        MockMultipartFile empty = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.uploadCover("art_1", empty)).getCode());

        byte[] large = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile tooLarge = new MockMultipartFile("file", "a.jpg", "image/jpeg", large);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.uploadCover("art_1", tooLarge)).getCode());

        MockMultipartFile badType = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});
        assertEquals(400, assertThrows(BusinessException.class, () -> service.uploadCover("art_1", badType)).getCode());
    }

    @Test
    void uploadCover_success() throws Exception {
        when(articleMapper.findById("art_1")).thenReturn(draftArticle("art_1"));
        when(redis.keys(anyString())).thenReturn(Set.of());
        when(minioStorageService.buildArticleCoverUrl("art_1")).thenReturn("http://minio/art_1.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2});

        Map<String, Object> result = service.uploadCover("art_1", file);

        assertTrue(result.get("coverImage").toString().startsWith("http://minio/art_1.jpg?v="));
        verify(minioStorageService).uploadArticleCover(eq("art_1"), any(), eq(2L), eq("image/jpeg"));
        verify(articleMapper).updateCoverImageId("art_1", "art_1.jpg");
    }

    @Test
    void aiDraftConfig_returnsWorkflowConfig() {
        Map<String, Object> config = service.aiDraftConfig();
        assertEquals("http://dify.local/v1/workflows/run", config.get("workflowUrl"));
        assertNotNull(config.get("inputJsonSchema"));
    }

    private static Article publishedArticle(String id) {
        Article article = new Article();
        article.setArticleId(id);
        article.setTitle("标题");
        article.setSummary("摘要");
        article.setContent("内容");
        article.setCategory(2);
        article.setStatus(3);
        article.setPublishedAt(LocalDateTime.now());
        return article;
    }

    private static Article draftArticle(String id) {
        Article article = publishedArticle(id);
        article.setStatus(1);
        return article;
    }

    private static Article pendingArticle(String id) {
        Article article = publishedArticle(id);
        article.setStatus(2);
        return article;
    }
}
