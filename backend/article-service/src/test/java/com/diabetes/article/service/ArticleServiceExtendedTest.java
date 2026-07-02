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

class ArticleServiceExtendedTest {

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
                "http://dify.local", "http://custom/draft", "draft-key");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(minioStorageService.buildArticleCoverUrl(anyString())).thenReturn("http://minio/cover.jpg");
    }

    @Test
    void delegatesRecommendRelatedAndRead() {
        Map<String, Object> rec = Map.of("strategy", "personalized");
        when(articleRecommendService.recommend("u1", 1, 10)).thenReturn(rec);
        assertEquals(rec, service.recommend("u1", 1, 10, null));

        Map<String, Object> spec = Map.of("workflowUrl", "http://dify");
        when(articleRecommendService.getDifyWorkflowSpec()).thenReturn(spec);
        assertEquals(spec, service.getDifyRecommendWorkflowSpec());

        Map<String, Object> related = Map.of("strategy", "related");
        when(articleRecommendService.related("art_1", "u1", 5)).thenReturn(related);
        assertEquals(related, service.related("art_1", "u1", 5));

        service.recordRead("u1", "art_1", 10, "list");
        verify(articleRecommendService).recordRead("u1", "art_1", 10, "list");
    }

    @Test
    void detail_handlesGuestUnpublishedAndHttpCover() {
        Article unpublished = article("art_1", 1);
        when(articleMapper.findById("art_1")).thenReturn(unpublished);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.detail("art_1", null)).getCode());

        Article noStatus = article("art_2", null);
        when(articleMapper.findById("art_2")).thenReturn(noStatus);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.detail("art_2", null)).getCode());

        Article published = article("art_3", 3);
        published.setCoverImageId("https://cdn.example.com/cover.jpg");
        when(articleMapper.findById("art_3")).thenReturn(published, (Article) null);
        when(articleMapper.findTagsByArticleId("art_3")).thenReturn(List.of());
        when(redis.keys(anyString())).thenReturn(Set.of());

        Map<String, Object> detail = service.detail("art_3", null);
        assertEquals("https://cdn.example.com/cover.jpg", detail.get("coverImage"));
        assertEquals(false, detail.get("favorited"));
        assertEquals("published", detail.get("status"));
    }

    @Test
    void toggleFavoriteAndFavorites() {
        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.toggleFavorite("u1", "missing")).getCode());

        Article draft = article("art_1", 1);
        when(articleMapper.findById("art_draft")).thenReturn(draft);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.toggleFavorite("u1", "art_draft")).getCode());

        Article published = article("art_1", 3);
        when(articleMapper.findById("art_1")).thenReturn(published);
        when(articleMapper.findFavoriteStatus("u1", "art_1")).thenReturn(0);
        service.toggleFavorite("u1", "art_1");

        when(articleMapper.findFavorites("u1", 0, 10)).thenReturn(List.of(published));
        when(articleMapper.countFavorites("u1")).thenReturn(1);
        Map<String, Object> favorites = service.favorites("u1", 1, 10);
        assertEquals(1, favorites.get("total"));
    }

    @Test
    void adminOperations() {
        Article article = article("art_1", 3);
        when(articleMapper.findAdminList(3, "关键词", 0, 10)).thenReturn(List.of(article));
        when(articleMapper.countAdminList(3, "关键词")).thenReturn(1);
        assertEquals(1, service.adminList(3, "关键词", 1, 10).get("total"));

        when(articleMapper.findAdminList(isNull(), isNull(), anyInt(), anyInt())).thenReturn(List.of());
        when(articleMapper.countAdminList(isNull(), isNull())).thenReturn(0);
        assertEquals(0, service.adminList(null, "  ", 1, 10).get("total"));

        when(articleMapper.findById("art_1")).thenReturn(article);
        when(articleMapper.findTagsByArticleId("art_1")).thenReturn(List.of("饮食"));
        Map<String, Object> detail = service.adminDetail("art_1");
        assertEquals(List.of("饮食"), detail.get("tags"));

        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.adminDetail("missing")).getCode());

        when(articleMapper.findPendingReview(0, 10)).thenReturn(List.of(article));
        when(articleMapper.countPendingReview()).thenReturn(1);
        assertEquals(1, service.pendingReview(1, 10).get("total"));
    }

    @Test
    void createCoversCategoryVariants() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "标题");
        body.put("content", "内容");
        body.put("summary", "摘要");
        body.put("category_id", 3);
        body.put("tags", List.of("运动"));
        Map<String, Object> created = service.create(body);
        assertEquals("draft", created.get("status"));
        verify(articleMapper).insert(argThat(a -> Integer.valueOf(3).equals(a.getCategory())));

        body.put("category", "exercise");
        service.create(body);
        verify(articleMapper, atLeastOnce()).insert(argThat(a -> Integer.valueOf(3).equals(a.getCategory())));

        body.put("category", "unknown-x");
        service.create(body);
        verify(articleMapper, atLeastOnce()).insert(argThat(a -> Integer.valueOf(1).equals(a.getCategory())));

        body.remove("category");
        body.remove("category_id");
        body.put("category", "999");
        service.create(body);
        verify(articleMapper, atLeastOnce()).insert(argThat(a -> Integer.valueOf(999).equals(a.getCategory())));
    }

    @Test
    void updatePublishedSyncsVectorAndNotFound() {
        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.update("missing", Map.of("title", "t"))).getCode());

        Article published = article("art_1", 3);
        published.setCoverImageId("keep.jpg");
        when(articleMapper.findById("art_1")).thenReturn(published);
        when(redis.keys(anyString())).thenReturn(Set.of());
        service.update("art_1", Map.of("title", "新标题", "content", "c", "summary", "s", "category", 2));
        verify(articleVectorSyncService).syncArticle("art_1");
    }

    @Test
    void deleteAndSubmitReviewValidation() {
        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class, () -> service.delete("missing")).getCode());

        Article rejected = article("art_1", 4);
        when(articleMapper.findById("art_1")).thenReturn(rejected);
        assertEquals("pending", service.submitReview("art_1").get("status"));

        Article pending = article("art_2", 2);
        when(articleMapper.findById("art_2")).thenReturn(pending);
        assertEquals(400, assertThrows(BusinessException.class, () -> service.submitReview("art_2")).getCode());
    }

    @Test
    void reviewValidationPaths() {
        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.review("missing", "approve", null)).getCode());

        Article draft = article("art_1", 1);
        when(articleMapper.findById("art_1")).thenReturn(draft);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.review("art_1", "approve", null)).getCode());

        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.review("art_1", "unknown", null)).getCode());
    }

    @Test
    void list_degradesWhenCacheInvalid() {
        when(valueOps.get(anyString())).thenReturn("{bad-json");
        when(articleMapper.findPublished(null, 0, 10)).thenReturn(List.of());
        when(articleMapper.countPublished(null)).thenReturn(0);
        assertEquals(0, service.list(null, 1, 10).get("total"));
    }

    @Test
    void uploadCoverNotFoundAndIoFailure() throws Exception {
        when(articleMapper.findById("missing")).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.uploadCover("missing", new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1})))
                .getCode());

        when(articleMapper.findById("art_1")).thenReturn(article("art_1", 1));
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        doThrow(new RuntimeException("io fail")).when(minioStorageService)
                .uploadArticleCover(eq("art_1"), any(), anyLong(), anyString());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.uploadCover("art_1", file));
        assertEquals(500, ex.getCode());
    }

    @Test
    void aiDraftConfigUsesCustomWorkflowUrl() {
        Map<String, Object> config = service.aiDraftConfig();
        assertEquals("http://custom/draft", config.get("workflowUrl"));
        assertEquals("draft-key", config.get("apiKey"));
    }

    private static Article article(String id, Integer status) {
        Article article = new Article();
        article.setArticleId(id);
        article.setTitle("标题");
        article.setSummary("摘要");
        article.setContent("内容");
        article.setCategory(2);
        article.setStatus(status);
        article.setViewCount(0);
        article.setPublishedAt(LocalDateTime.now());
        return article;
    }
}
