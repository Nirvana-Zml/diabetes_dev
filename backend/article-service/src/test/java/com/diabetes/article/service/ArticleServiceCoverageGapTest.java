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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleServiceCoverageGapTest {

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
        when(redis.opsForValue()).thenReturn(valueOps);
        when(minioStorageService.buildArticleCoverUrl(anyString())).thenReturn("http://minio/cover.jpg");
    }

    @Test
    void recommendPopularStrategyIgnoresCase() {
        when(articleRecommendService.popularRecommend(1, 10)).thenReturn(Map.of("strategy", "popular"));
        service.recommend("u1", 1, 10, " POPULAR ");
        verify(articleRecommendService).popularRecommend(1, 10);
    }

    @Test
    void searchNullKeywordThrows() {
        assertEquals(400, assertThrows(BusinessException.class, () -> service.search(null, 1, 10)).getCode());
    }

    @Test
    void detailFavoriteFalseWhenZero() {
        Article article = published("art_1");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(null);
        when(articleMapper.findFavoriteStatus("u1", "art_1")).thenReturn(0);
        when(redis.keys(anyString())).thenReturn(Set.of("k1"), Set.of("k2"), Set.of("k3"));
        Map<String, Object> detail = service.detail("art_1", "u1");
        assertEquals(false, detail.get("favorited"));
        assertNull(detail.get("tags"));
        verify(redis, times(3)).delete(anySet());
    }

    @Test
    void toggleFavoriteWhenStatusNull() {
        Article article = published("art_1");
        article.setStatus(null);
        when(articleMapper.findById("art_1")).thenReturn(article);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.toggleFavorite("u1", "art_1")).getCode());
    }

    @Test
    void updateDraftDoesNotSyncVector() {
        Article draft = published("art_1");
        draft.setStatus(1);
        when(articleMapper.findById("art_1")).thenReturn(draft);
        when(redis.keys(anyString())).thenReturn(Set.of());
        service.update("art_1", Map.of("title", "t", "content", "c", "summary", "s", "category", 2));
        verify(articleVectorSyncService, never()).syncArticle(anyString());
    }

    @Test
    void submitReviewNotFoundAndInvalidStatus() {
        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.submitReview("missing")).getCode());

        Article pending = published("art_1");
        pending.setStatus(2);
        when(articleMapper.findById("art_1")).thenReturn(pending);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.submitReview("art_1")).getCode());
    }

    @Test
    void reviewInvalidAction() {
        Article pending = published("art_1");
        pending.setStatus(2);
        when(articleMapper.findById("art_1")).thenReturn(pending);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.review("art_1", "unknown", null)).getCode());
    }

    @Test
    void reviewRejectNullReason() {
        Article pending = published("art_1");
        pending.setStatus(2);
        when(articleMapper.findById("art_1")).thenReturn(pending);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.review("art_1", "reject", null)).getCode());
    }

    @Test
    void reviewNotPending() {
        Article draft = published("art_1");
        draft.setStatus(1);
        when(articleMapper.findById("art_1")).thenReturn(draft);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.review("art_1", "approve", null)).getCode());
    }

    @Test
    void uploadCoverNullContentTypeAndBusinessException() {
        when(articleMapper.findById("art_1")).thenReturn(published("art_1"));
        MockMultipartFile noType = new MockMultipartFile("file", "a.jpg", null, new byte[]{1});
        assertEquals(400, assertThrows(BusinessException.class, () -> service.uploadCover("art_1", noType)).getCode());

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        doThrow(new BusinessException(400, "存储失败")).when(minioStorageService)
                .uploadArticleCover(anyString(), any(), anyLong(), anyString());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.uploadCover("art_1", file)).getCode());
    }

    @Test
    void listCacheWriteFailureStillReturns() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(articleMapper.findPublished(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(articleMapper.countPublished(any())).thenReturn(0);
        doThrow(new RuntimeException("redis write fail")).when(valueOps).set(anyString(), anyString(), any());
        assertEquals(0, service.list(null, 1, 10).get("total"));
    }

    @Test
    void invalidateListCacheSwallowsRedisErrors() {
        when(redis.keys(anyString())).thenThrow(new RuntimeException("redis down"));
        Article article = published("art_1");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        assertDoesNotThrow(() -> service.detail("art_1", null));
    }

    @Test
    void createMapsAllCategoriesAndDefault() {
        Map<String, Object> body = baseBody();
        for (String cat : List.of("medication", "complications", "diabetes_basics", "用药", "并发症", "糖尿病基础")) {
            body.put("category", cat);
            service.create(body);
        }
        body.remove("category");
        body.remove("category_id");
        service.create(body);
        verify(articleMapper, atLeastOnce()).insert(any(Article.class));
    }

    @Test
    void adminDetailShowsPendingAndRejectedStatus() {
        Article pending = published("art_p");
        pending.setStatus(2);
        when(articleMapper.findById("art_p")).thenReturn(pending);
        when(articleMapper.findTagsByArticleId("art_p")).thenReturn(null);
        assertEquals("pending", service.adminDetail("art_p").get("status"));

        Article rejected = published("art_r");
        rejected.setStatus(4);
        when(articleMapper.findById("art_r")).thenReturn(rejected);
        when(articleMapper.findTagsByArticleId("art_r")).thenReturn(List.of());
        assertEquals("rejected", service.adminDetail("art_r").get("status"));
    }

    @Test
    void parseTagsSkipsNullItems() {
        Map<String, Object> body = baseBody();
        body.put("tags", new java.util.ArrayList<>(java.util.Arrays.asList(null, "有效标签")));
        service.create(body);
        verify(articleMapper).insertTag(anyString(), anyString(), eq("有效标签"));
    }

    @Test
    void resolveCoverImageHttpPrefix() {
        Article article = published("art_1");
        article.setCoverImageId("http://cdn.example.com/x.jpg");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(redis.keys(anyString())).thenReturn(null);
        assertEquals("http://cdn.example.com/x.jpg",
                service.detail("art_1", null).get("coverImage"));
    }

    private static Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "标题");
        body.put("content", "内容");
        body.put("summary", "摘要");
        body.put("category", 2);
        body.put("tags", List.of());
        return body;
    }

    @Test
    void detailFavoritedTrueAndHttpsCover() {
        Article article = published("art_1");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(articleMapper.findFavoriteStatus("u1", "art_1")).thenReturn(1);
        when(redis.keys(anyString())).thenReturn(Set.of());
        assertEquals(true, service.detail("art_1", "u1").get("favorited"));

        article.setCoverImageId("https://cdn.example.com/x.jpg");
        assertEquals("https://cdn.example.com/x.jpg", service.detail("art_1", "u1").get("coverImage"));
    }

    @Test
    void submitReviewFromRejectedStatus() {
        Article rejected = published("art_1");
        rejected.setStatus(4);
        when(articleMapper.findById("art_1")).thenReturn(rejected);
        assertEquals("pending", service.submitReview("art_1").get("status"));
    }

    @Test
    void updateWhenExistingStatusNull() {
        Article existing = published("art_1");
        existing.setStatus(null);
        when(articleMapper.findById("art_1")).thenReturn(existing);
        when(redis.keys(anyString())).thenReturn(Set.of());
        service.update("art_1", Map.of("title", "t", "content", "c", "summary", "s", "category", 2));
        verify(articleVectorSyncService, never()).syncArticle(anyString());
    }

    @Test
    void reviewWhenStatusNull() {
        Article article = published("art_1");
        article.setStatus(null);
        when(articleMapper.findById("art_1")).thenReturn(article);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.review("art_1", "approve", null)).getCode());
    }

    @Test
    void resolveCoverImageBlankUsesMinio() {
        Article article = published("art_1");
        article.setCoverImageId("  ");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(redis.keys(anyString())).thenReturn(Set.of());
        assertEquals("http://minio/cover.jpg", service.detail("art_1", null).get("coverImage"));
    }

    @Test
    void adminListTrimsNonBlankKeyword() {
        when(articleMapper.findAdminList(isNull(), eq("search"), anyInt(), anyInt())).thenReturn(List.of());
        when(articleMapper.countAdminList(isNull(), eq("search"))).thenReturn(0);
        service.adminList(null, "  search  ", 1, 10);
        verify(articleMapper).findAdminList(isNull(), eq("search"), eq(0), eq(10));
    }

    @Test
    void recommendNonPopularStrategyDelegatesPersonalized() {
        when(articleRecommendService.recommend("u1", 1, 10)).thenReturn(Map.of("strategy", "personalized"));
        service.recommend("u1", 1, 10, "personalized");
        verify(articleRecommendService).recommend("u1", 1, 10);
    }

    @Test
    void listUsesTrimmedKeywordForSearchPath() {
        Article article = published("art_1");
        when(articleMapper.findPublished(isNull(), anyInt(), anyInt())).thenReturn(List.of(article));
        when(articleMapper.countPublished(isNull())).thenReturn(1);
        when(valueOps.get(anyString())).thenReturn(null);
        when(redis.keys(anyString())).thenReturn(Set.of());
        Map<String, Object> result = service.list(null, 1, 10);
        assertEquals(1, result.get("total"));
    }

    @Test
    void detailCoverImageHttpOnlyPrefix() {
        Article article = published("art_1");
        article.setCoverImageId("http://only-http.example/x.png");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(redis.keys(anyString())).thenReturn(Set.of());
        assertEquals("http://only-http.example/x.png", service.detail("art_1", null).get("coverImage"));
    }

    @Test
    void detailCoverImageHttpsOnlyPrefix() {
        Article article = published("art_1");
        article.setCoverImageId("https://only-https.example/x.png");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(redis.keys(anyString())).thenReturn(Set.of());
        assertEquals("https://only-https.example/x.png", service.detail("art_1", null).get("coverImage"));
    }

    @Test
    void recommendNullStrategyDelegatesPersonalized() {
        when(articleRecommendService.recommend("u1", 1, 10)).thenReturn(Map.of("strategy", "personalized"));
        service.recommend("u1", 1, 10, null);
        verify(articleRecommendService).recommend("u1", 1, 10);
        verify(articleRecommendService, never()).popularRecommend(anyInt(), anyInt());
    }

    @Test
    void detailFavoriteNullTreatedAsFalse() {
        Article article = published("art_1");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(articleMapper.findFavoriteStatus("u1", "art_1")).thenReturn(null);
        when(redis.keys(anyString())).thenReturn(Set.of());
        assertEquals(false, service.detail("art_1", "u1").get("favorited"));
    }

    @Test
    void submitReviewWhenStatusNull() {
        Article article = published("art_1");
        article.setStatus(null);
        when(articleMapper.findById("art_1")).thenReturn(article);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.submitReview("art_1")).getCode());
    }

    @Test
    void adminListBlankKeywordNormalizedToNull() {
        when(articleMapper.findAdminList(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(articleMapper.countAdminList(isNull(), isNull())).thenReturn(0);
        service.adminList(null, "   ", 1, 10);
        verify(articleMapper).findAdminList(isNull(), isNull(), eq(0), eq(10));
    }

    @Test
    void adminListNullKeywordUsesBlankToNull() {
        when(articleMapper.findAdminList(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(articleMapper.countAdminList(isNull(), isNull())).thenReturn(0);
        service.adminList(null, null, 1, 10);
        verify(articleMapper).findAdminList(isNull(), isNull(), eq(0), eq(10));
    }

    @Test
    void detailCoverImageRelativeIdUsesMinio() {
        Article article = published("art_1");
        article.setCoverImageId("relative-cover-id");
        when(articleMapper.findById("art_1")).thenReturn(article, article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of());
        when(redis.keys(anyString())).thenReturn(Set.of());
        assertEquals("http://minio/cover.jpg", service.detail("art_1", null).get("coverImage"));
    }

    @Test
    void createWithNullTitleUsesNullStringVal() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", null);
        body.put("content", "内容");
        body.put("summary", "摘要");
        body.put("category", 2);
        service.create(body);
        verify(articleMapper).insert(argThat(a -> a.getTitle() == null));
    }

    private static Article published(String id) {
        Article article = new Article();
        article.setArticleId(id);
        article.setTitle("标题");
        article.setSummary("摘要");
        article.setContent("内容");
        article.setCategory(2);
        article.setStatus(3);
        article.setViewCount(1);
        article.setPublishedAt(LocalDateTime.now());
        return article;
    }
}
