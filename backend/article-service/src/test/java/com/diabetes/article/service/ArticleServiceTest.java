package com.diabetes.article.service;

import com.diabetes.article.entity.Article;
import com.diabetes.article.testutil.Stubs;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleServiceTest {

    private ArticleService articleService;
    private Stubs.ArticleMapperStub articleMapper;
    private RedisOperations<String, String> redisTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        articleMapper = new Stubs.ArticleMapperStub();
        redisTemplate = mock(RedisOperations.class);
        objectMapper = new ObjectMapper();
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        ArticleRecommendService recommendService = mock(ArticleRecommendService.class);
        ArticleVectorSyncService vectorSync = mock(ArticleVectorSyncService.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("/images/articles/test.jpg");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(redisTemplate.keys(any())).thenReturn(java.util.Collections.emptySet());

        articleService = new ArticleService(
                articleMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                recommendService,
                vectorSync,
                "http://dify:8080",
                "",
                "test-api-key"
        );
    }

    @Test
    void testListArticles() {
        articleMapper.addArticle(createArticle("art_01", "文章1", 1, 3));
        articleMapper.addArticle(createArticle("art_02", "文章2", 2, 3));
        articleMapper.addArticle(createArticle("art_03", "文章3", 1, 3));

        Map<String, Object> result = articleService.list(null, 1, 10);
        assertEquals(3, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testListArticlesByCategory() {
        articleMapper.addArticle(createArticle("art_01", "饮食文章", 2, 3));
        articleMapper.addArticle(createArticle("art_02", "运动文章", 3, 3));
        articleMapper.addArticle(createArticle("art_03", "饮食文章2", 2, 3));

        Map<String, Object> result = articleService.list(2, 1, 10);
        assertEquals(2, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testSearchArticles() {
        articleMapper.addArticle(createArticle("art_01", "糖尿病饮食指南", 2, 3));
        articleMapper.addArticle(createArticle("art_02", "运动康复训练", 3, 3));
        articleMapper.addArticle(createArticle("art_03", "糖尿病用药指导", 4, 3));

        Map<String, Object> result = articleService.search("糖尿病", 1, 10);
        assertEquals(2, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testSearchBlankKeyword() {
        assertThrows(BusinessException.class, () -> articleService.search("", 1, 10));
        assertThrows(BusinessException.class, () -> articleService.search("   ", 1, 10));
        assertThrows(BusinessException.class, () -> articleService.search(null, 1, 10));
    }

    @Test
    void testDetailArticle() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 3));

        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("测试文章", result.get("title"));
        assertTrue(((Number) result.get("viewCount")).intValue() >= 1);
    }

    @Test
    void testDetailArticleWithUserId() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 3));
        articleMapper.upsertFavorite("fav_01", "user_01", "art_01", 1);

        Map<String, Object> result = articleService.detail("art_01", "user_01");
        assertEquals("测试文章", result.get("title"));
        assertTrue((Boolean) result.get("favorited"));
    }

    @Test
    void testDetailArticleWithUserIdNotFavorited() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 3));

        Map<String, Object> result = articleService.detail("art_01", "user_01");
        assertEquals("测试文章", result.get("title"));
        assertFalse((Boolean) result.get("favorited"));
    }

    @Test
    void testDetailNotFound() {
        assertThrows(BusinessException.class, () -> articleService.detail("not_exist", null));
    }

    @Test
    void testDetailNotPublished() {
        Article article = createArticle("art_01", "未发布文章", 1, 1);
        articleMapper.addArticle(article);

        assertThrows(BusinessException.class, () -> articleService.detail("art_01", null));
    }

    @Test
    void testToggleFavorite() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 3));

        Map<String, Object> result1 = articleService.toggleFavorite("user_01", "art_01");
        assertTrue((Boolean) result1.get("favorited"));

        Map<String, Object> result2 = articleService.toggleFavorite("user_01", "art_01");
        assertFalse((Boolean) result2.get("favorited"));
    }

    @Test
    void testToggleFavoriteArticleNotFound() {
        assertThrows(BusinessException.class, () -> articleService.toggleFavorite("user_01", "not_exist"));
    }

    @Test
    void testFavorites() {
        articleMapper.addArticle(createArticle("art_01", "收藏文章1", 1, 3));
        articleMapper.addArticle(createArticle("art_02", "收藏文章2", 2, 3));

        articleService.toggleFavorite("user_01", "art_01");
        articleService.toggleFavorite("user_01", "art_02");

        Map<String, Object> result = articleService.favorites("user_01", 1, 10);
        assertEquals(2, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testAdminList() {
        articleMapper.addArticle(createArticle("art_01", "草稿文章", 1, 1));
        articleMapper.addArticle(createArticle("art_02", "审核文章", 2, 2));
        articleMapper.addArticle(createArticle("art_03", "发布文章", 3, 3));

        Map<String, Object> result = articleService.adminList(null, null, 1, 10);
        assertEquals(3, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testAdminListByStatus() {
        articleMapper.addArticle(createArticle("art_01", "草稿文章", 1, 1));
        articleMapper.addArticle(createArticle("art_02", "审核文章", 2, 2));

        Map<String, Object> result = articleService.adminList(1, null, 1, 10);
        assertEquals(1, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testAdminDetail() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 3));

        Map<String, Object> result = articleService.adminDetail("art_01");
        assertEquals("测试文章", result.get("title"));
    }

    @Test
    void testAdminDetailNotFound() {
        assertThrows(BusinessException.class, () -> articleService.adminDetail("not_exist"));
    }

    @Test
    void testCreateArticle() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新文章");
        body.put("content", "内容");
        body.put("summary", "摘要");
        body.put("category", 1);

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
        assertEquals("draft", result.get("status"));
    }

    @Test
    void testCreateArticleWithTags() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "带标签文章");
        body.put("content", "内容");
        body.put("tags", List.of("标签A", "标签B"));

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleWithTagsContainingNull() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "空标签");
        body.put("content", "内容");
        List<String> tags = new java.util.ArrayList<>();
        tags.add("标签A");
        tags.add(null);
        tags.add("标签B");
        tags.add("");
        tags.add("标签A");
        body.put("tags", tags);

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleWithCategoryString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "饮食文章");
        body.put("content", "内容");
        body.put("category", "diet");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleWithCategoryChinese() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "运动文章");
        body.put("content", "内容");
        body.put("category", "运动");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleWithCategoryId() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "用药文章");
        body.put("content", "内容");
        body.put("category_id", 4);

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleWithCategoryInvalid() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "测试文章");
        body.put("content", "内容");
        body.put("category", "invalid");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testUpdateArticle() {
        articleMapper.addArticle(createArticle("art_01", "旧标题", 1, 1));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        body.put("content", "新内容");

        Map<String, Object> result = articleService.update("art_01", body);
        assertEquals("art_01", result.get("articleId"));

        Article updated = articleMapper.findById("art_01");
        assertEquals("新标题", updated.getTitle());
    }

    @Test
    void testUpdateArticleNotFound() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");

        assertThrows(BusinessException.class, () -> articleService.update("not_exist", body));
    }

    @Test
    void testUpdateArticlePublished() {
        Article article = createArticle("art_01", "已发布文章", 1, 3);
        article.setCoverImageId("art_01.jpg");
        articleMapper.addArticle(article);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "更新标题");

        Map<String, Object> result = articleService.update("art_01", body);
        assertEquals("art_01", result.get("articleId"));
    }

    @Test
    void testDeleteArticle() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));

        articleService.delete("art_01");

        assertNull(articleMapper.findById("art_01"));
    }

    @Test
    void testDeleteArticleNotFound() {
        assertThrows(BusinessException.class, () -> articleService.delete("not_exist"));
    }

    @Test
    void testSubmitReview() {
        articleMapper.addArticle(createArticle("art_01", "草稿文章", 1, 1));

        Map<String, Object> result = articleService.submitReview("art_01");
        assertEquals("pending", result.get("status"));

        Article article = articleMapper.findById("art_01");
        assertEquals(2, article.getStatus());
    }

    @Test
    void testSubmitReviewNotFound() {
        assertThrows(BusinessException.class, () -> articleService.submitReview("not_exist"));
    }

    @Test
    void testSubmitReviewWrongStatus() {
        articleMapper.addArticle(createArticle("art_01", "已发布文章", 1, 3));

        assertThrows(BusinessException.class, () -> articleService.submitReview("art_01"));
    }

    @Test
    void testSubmitReviewRejected() {
        Article article = createArticle("art_01", "已驳回文章", 1, 4);
        articleMapper.addArticle(article);

        Map<String, Object> result = articleService.submitReview("art_01");
        assertEquals("pending", result.get("status"));

        Article updated = articleMapper.findById("art_01");
        assertEquals(2, updated.getStatus());
    }

    @Test
    void testReviewApprove() {
        articleMapper.addArticle(createArticle("art_01", "审核文章", 1, 2));

        Map<String, Object> result = articleService.review("art_01", "approve", null);
        assertEquals("published", result.get("status"));

        Article article = articleMapper.findById("art_01");
        assertEquals(3, article.getStatus());
    }

    @Test
    void testReviewReject() {
        articleMapper.addArticle(createArticle("art_01", "审核文章", 1, 2));

        Map<String, Object> result = articleService.review("art_01", "reject", "内容不符合要求");
        assertEquals("rejected", result.get("status"));

        Article article = articleMapper.findById("art_01");
        assertEquals(4, article.getStatus());
        assertEquals("内容不符合要求", article.getRejectReason());
    }

    @Test
    void testReviewRejectWithoutReason() {
        articleMapper.addArticle(createArticle("art_01", "审核文章", 1, 2));

        assertThrows(BusinessException.class, () -> articleService.review("art_01", "reject", null));
        assertThrows(BusinessException.class, () -> articleService.review("art_01", "reject", ""));
    }

    @Test
    void testReviewWrongAction() {
        articleMapper.addArticle(createArticle("art_01", "审核文章", 1, 2));

        assertThrows(BusinessException.class, () -> articleService.review("art_01", "invalid", null));
    }

    @Test
    void testReviewWrongStatus() {
        articleMapper.addArticle(createArticle("art_01", "草稿文章", 1, 1));

        assertThrows(BusinessException.class, () -> articleService.review("art_01", "approve", null));
    }

    @Test
    void testPendingReview() {
        articleMapper.addArticle(createArticle("art_01", "待审核1", 1, 2));
        articleMapper.addArticle(createArticle("art_02", "待审核2", 2, 2));
        articleMapper.addArticle(createArticle("art_03", "已发布", 3, 3));

        Map<String, Object> result = articleService.pendingReview(1, 10);
        assertEquals(2, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testAiDraftConfig() {
        Map<String, Object> result = articleService.aiDraftConfig();
        assertNotNull(result);
    }

    @Test
    void testRecommend() {
        Map<String, Object> result = articleService.recommend(null, 1, 10, null);
        assertNotNull(result);
    }

    @Test
    void testRelated() {
        articleMapper.addArticle(createArticle("art_01", "基础文章", 1, 3));
        articleMapper.addArticle(createArticle("art_02", "相关文章1", 1, 3));
        articleMapper.addArticle(createArticle("art_03", "相关文章2", 1, 3));

        Map<String, Object> result = articleService.related("art_01", null, 5);
        assertNotNull(result);
    }

    @Test
    void testUploadCover() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});

        Map<String, Object> result = articleService.uploadCover("art_01", file);
        assertNotNull(result.get("coverImage"));
    }

    @Test
    void testUploadCoverArticleNotFound() {
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThrows(BusinessException.class, () -> articleService.uploadCover("not_exist", file));
    }

    @Test
    void testUploadCoverEmptyFile() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{});

        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testUploadCoverNullFile() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));

        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", null));
    }

    @Test
    void testUploadCoverInvalidContentType() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        MultipartFile file = new MockMultipartFile("file", "cover.txt", "text/plain", new byte[]{1, 2, 3});

        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testUploadCoverTooLarge() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        byte[] largeContent = new byte[6 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", largeContent);

        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testUploadCoverWithGenericException() throws Exception {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});

        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("/images/articles/test.jpg");
        doThrow(new RuntimeException("Network error")).when(minioStorage).uploadArticleCover(any(), any(), anyLong(), any());

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(redisTemplate.keys(any())).thenReturn(java.util.Collections.emptySet());

        ArticleService serviceWithError = new ArticleService(
                articleMapper, redisTemplate, objectMapper, minioStorage,
                mock(ArticleRecommendService.class), mock(ArticleVectorSyncService.class),
                "http://dify:8080", "", "test-api-key"
        );

        BusinessException e = assertThrows(BusinessException.class, () -> serviceWithError.uploadCover("art_01", file));
        assertEquals(500, e.getCode());
    }

    @Test
    void testUploadCoverWithBusinessException() throws Exception {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});

        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("/images/articles/test.jpg");
        doThrow(new com.diabetes.common.exception.BusinessException(400, "存储服务异常")).when(minioStorage).uploadArticleCover(any(), any(), anyLong(), any());

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(redisTemplate.keys(any())).thenReturn(java.util.Collections.emptySet());

        ArticleService serviceWithError = new ArticleService(
                articleMapper, redisTemplate, objectMapper, minioStorage,
                mock(ArticleRecommendService.class), mock(ArticleVectorSyncService.class),
                "http://dify:8080", "", "test-api-key"
        );

        com.diabetes.common.exception.BusinessException e = assertThrows(
                com.diabetes.common.exception.BusinessException.class, 
                () -> serviceWithError.uploadCover("art_01", file));
        assertEquals(400, e.getCode());
        assertEquals("存储服务异常", e.getMessage());
    }

    @Test
    void testAdminListWithKeyword() {
        articleMapper.addArticle(createArticle("art_01", "糖尿病饮食", 2, 3));
        articleMapper.addArticle(createArticle("art_02", "运动康复", 3, 3));
        articleMapper.addArticle(createArticle("art_03", "糖尿病用药", 4, 3));

        Map<String, Object> result = articleService.adminList(null, "糖尿病", 1, 10);
        assertEquals(2, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testAdminListWithBlankKeyword() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 3));

        Map<String, Object> result = articleService.adminList(null, "   ", 1, 10);
        assertEquals(1, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testAdminDetailWithTags() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        articleMapper.addArticle(article);
        articleMapper.insertTag("tag_01", "art_01", "标签A");
        articleMapper.insertTag("tag_02", "art_01", "标签B");

        Map<String, Object> result = articleService.adminDetail("art_01");
        assertEquals("测试文章", result.get("title"));
        assertTrue(((List<?>) result.get("tags")).size() >= 2);
    }

    @Test
    void testListWithCache() {
        articleMapper.addArticle(createArticle("art_01", "缓存测试文章", 1, 3));

        when(redisTemplate.opsForValue().get(any())).thenReturn(null);
        when(redisTemplate.keys(any())).thenReturn(java.util.Collections.emptySet());

        Map<String, Object> result1 = articleService.list(null, 1, 10);
        assertEquals(1, ((List<?>) result1.get("articles")).size());
    }

    @Test
    void testDetailWithNullStatus() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        article.setStatus(null);
        articleMapper.addArticle(article);

        assertThrows(BusinessException.class, () -> articleService.detail("art_01", null));
    }

    @Test
    void testToggleFavoriteWithNullStatus() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        article.setStatus(null);
        articleMapper.addArticle(article);

        assertThrows(BusinessException.class, () -> articleService.toggleFavorite("user_01", "art_01"));
    }

    @Test
    void testSubmitReviewNullStatus() {
        Article article = createArticle("art_01", "测试文章", 1, 1);
        article.setStatus(null);
        articleMapper.addArticle(article);

        assertThrows(BusinessException.class, () -> articleService.submitReview("art_01"));
    }

    @Test
    void testReviewNullStatus() {
        Article article = createArticle("art_01", "测试文章", 1, 2);
        article.setStatus(null);
        articleMapper.addArticle(article);

        assertThrows(BusinessException.class, () -> articleService.review("art_01", "approve", null));
    }

    @Test
    void testCreateArticleNullCategory() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "无分类文章");
        body.put("content", "内容");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleCategoryComplications() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "并发症文章");
        body.put("content", "内容");
        body.put("category", "complications");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleCategoryDiabetesBasics() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "基础文章");
        body.put("content", "内容");
        body.put("category", "diabetes_basics");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleCategoryNumberString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "数字分类文章");
        body.put("content", "内容");
        body.put("category", "2");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testCreateArticleCategoryInvalidString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "无效分类文章");
        body.put("content", "内容");
        body.put("category", "invalid_category");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testUpdateArticleCoverPreserved() {
        Article article = createArticle("art_01", "旧标题", 1, 1);
        article.setCoverImageId("art_01_cover.jpg");
        articleMapper.addArticle(article);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");

        articleService.update("art_01", body);

        Article updated = articleMapper.findById("art_01");
        assertEquals("art_01_cover.jpg", updated.getCoverImageId());
    }

    @Test
    void testListPagination() {
        for (int i = 1; i <= 15; i++) {
            articleMapper.addArticle(createArticle("art_" + i, "文章" + i, 1, 3));
        }

        Map<String, Object> page1 = articleService.list(null, 1, 10);
        assertEquals(10, ((List<?>) page1.get("articles")).size());

        Map<String, Object> page2 = articleService.list(null, 2, 10);
        assertEquals(5, ((List<?>) page2.get("articles")).size());
    }

    @Test
    void testSearchPagination() {
        for (int i = 1; i <= 15; i++) {
            articleMapper.addArticle(createArticle("art_" + i, "糖尿病文章" + i, 1, 3));
        }

        Map<String, Object> page1 = articleService.search("糖尿病", 1, 10);
        assertEquals(10, ((List<?>) page1.get("articles")).size());

        Map<String, Object> page2 = articleService.search("糖尿病", 2, 10);
        assertEquals(5, ((List<?>) page2.get("articles")).size());
    }

    @Test
    void testFavoritesPagination() {
        for (int i = 1; i <= 15; i++) {
            articleMapper.addArticle(createArticle("art_" + i, "收藏文章" + i, 1, 3));
            articleMapper.upsertFavorite("fav_" + i, "user_01", "art_" + i, 1);
        }

        Map<String, Object> page1 = articleService.favorites("user_01", 1, 10);
        assertEquals(10, ((List<?>) page1.get("articles")).size());

        Map<String, Object> page2 = articleService.favorites("user_01", 2, 10);
        assertEquals(5, ((List<?>) page2.get("articles")).size());
    }

    @Test
    void testPendingReviewPagination() {
        for (int i = 1; i <= 15; i++) {
            Article article = createArticle("art_" + i, "待审核文章" + i, 1, 2);
            articleMapper.addArticle(article);
        }

        Map<String, Object> page1 = articleService.pendingReview(1, 10);
        assertEquals(10, ((List<?>) page1.get("articles")).size());

        Map<String, Object> page2 = articleService.pendingReview(2, 10);
        assertEquals(5, ((List<?>) page2.get("articles")).size());
    }

    @Test
    void testAdminListPagination() {
        for (int i = 1; i <= 15; i++) {
            articleMapper.addArticle(createArticle("art_" + i, "管理文章" + i, 1, 3));
        }

        Map<String, Object> page1 = articleService.adminList(null, null, 1, 10);
        assertEquals(10, ((List<?>) page1.get("articles")).size());

        Map<String, Object> page2 = articleService.adminList(null, null, 2, 10);
        assertEquals(5, ((List<?>) page2.get("articles")).size());
    }

    @Test
    void testDeleteArticlePublished() {
        Article article = createArticle("art_01", "已发布文章", 1, 3);
        articleMapper.addArticle(article);

        assertDoesNotThrow(() -> articleService.delete("art_01"));
        assertNull(articleMapper.findById("art_01"));
    }

    @Test
    void testRecordRead() {
        assertDoesNotThrow(() -> articleService.recordRead("user_01", "art_01", 100, "manual"));
    }

    @Test
    void testRecordReadNullUserId() {
        assertDoesNotThrow(() -> articleService.recordRead(null, "art_01", 100, "manual"));
    }

    @Test
    void testRecordReadBlankUserId() {
        assertDoesNotThrow(() -> articleService.recordRead("", "art_01", 100, "manual"));
    }

    @Test
    void testRecordReadNullDuration() {
        assertDoesNotThrow(() -> articleService.recordRead("user_01", "art_01", null, "manual"));
    }

    @Test
    void testRecordReadNegativeDuration() {
        assertDoesNotThrow(() -> articleService.recordRead("user_01", "art_01", -10, "manual"));
    }

    @Test
    void testGetDifyRecommendWorkflowSpec() {
        Map<String, Object> result = articleService.getDifyRecommendWorkflowSpec();
        assertNotNull(result);
    }

    @Test
    void testListWithCacheHit() throws Exception {
        articleMapper.addArticle(createArticle("art_01", "缓存测试文章", 1, 3));
        
        String cachedJson = "{\"articles\":[{\"articleId\":\"art_cache\",\"title\":\"Cached\"}],\"total\":1}";
        when(redisTemplate.opsForValue().get(any())).thenReturn(cachedJson);

        Map<String, Object> result = articleService.list(null, 1, 10);
        assertEquals(1, ((List<?>) result.get("articles")).size());
    }

    @Test
    void testListWithCacheDeserializationError() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        when(redisTemplate.opsForValue().get(any())).thenReturn("invalid json");

        Map<String, Object> result = articleService.list(null, 1, 10);
        assertNotNull(result);
    }

    @Test
    void testInvalidateListCacheWithKeys() {
        java.util.Set<String> keys = java.util.Set.of("article:list:*", "article:rec:*");
        when(redisTemplate.keys(any())).thenReturn(keys);

        assertDoesNotThrow(() -> {
            articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
            articleService.detail("art_01", null);
        });
    }

    @Test
    void testResolveCoverImageUrlWithHttpUrl() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        Article article = articleMapper.findById("art_01");
        article.setCoverImageId("https://example.com/cover.jpg");
        articleMapper.update(article);

        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("https://example.com/cover.jpg", result.get("coverImage"));
    }

    @Test
    void testResolveCoverImageUrlWithHttpUrlPrefix() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        Article article = articleMapper.findById("art_01");
        article.setCoverImageId("http://example.com/cover.jpg");
        articleMapper.update(article);

        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("http://example.com/cover.jpg", result.get("coverImage"));
    }

    @Test
    void testResolveCoverImageUrlWithMinio() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));

        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("/images/articles/test.jpg", result.get("coverImage"));
    }

    @Test
    void testResolveCoverImageUrlWithNonHttpId() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleMapper.updateCoverImageId("art_01", "art_01_cover.jpg");

        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("/images/articles/test.jpg", result.get("coverImage"));
    }

    @Test
    void testUploadCoverWithException() {
        articleMapper.addArticle(createArticle("art_01", "测试文章", 1, 1));
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("/images/articles/test.jpg");
        when(minioStorage.uploadArticleCover(any(), any(), anyLong(), any()))
                .thenThrow(new RuntimeException("Storage error"));

        RedisOperations<String, String> redisTemplate = mock(RedisOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        when(redisTemplate.keys(any())).thenReturn(java.util.Collections.emptySet());

        ArticleService service = new ArticleService(
                articleMapper, redisTemplate, new ObjectMapper(), minioStorage,
                mock(ArticleRecommendService.class), mock(ArticleVectorSyncService.class),
                "http://dify:8080", "", "test-api-key"
        );

        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> service.uploadCover("art_01", file));
    }

    @Test
    void testStatusNameNull() {
        articleMapper.addArticle(createArticle("art_01", "草稿文章", 1, 1));
        Article article = articleMapper.findById("art_01");
        article.setStatus(null);

        Map<String, Object> adminDetail = articleService.adminDetail("art_01");
        assertEquals("draft", adminDetail.get("status"));
    }

    @Test
    void testStatusNameDraft() {
        articleMapper.addArticle(createArticle("art_01", "草稿文章", 1, 1));
        Map<String, Object> result = articleService.adminDetail("art_01");
        assertEquals("draft", result.get("status"));
    }

    @Test
    void testStatusNamePending() {
        articleMapper.addArticle(createArticle("art_01", "审核文章", 1, 2));
        Map<String, Object> result = articleService.adminDetail("art_01");
        assertEquals("pending", result.get("status"));
    }

    @Test
    void testStatusNamePublished() {
        articleMapper.addArticle(createArticle("art_01", "发布文章", 1, 3));
        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("published", result.get("status"));
    }

    @Test
    void testStatusNameRejected() {
        articleMapper.addArticle(createArticle("art_01", "驳回文章", 1, 4));
        Map<String, Object> result = articleService.adminDetail("art_01");
        assertEquals("rejected", result.get("status"));
    }

    @Test
    void testMapCategoryExercise() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "运动文章");
        body.put("content", "内容");
        body.put("category", "exercise");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testMapCategoryMedication() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "用药文章");
        body.put("content", "内容");
        body.put("category", "medication");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testMapCategoryNumber() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "数字分类文章");
        body.put("content", "内容");
        body.put("category", "3");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testMapCategoryParseException() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "无效分类文章");
        body.put("content", "内容");
        body.put("category", "not_a_number");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testParseTagsNotList() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "文章");
        body.put("content", "内容");
        body.put("tags", "not_a_list");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testDetailRefreshedArticle() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));

        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals("文章", result.get("title"));
        assertTrue(((Number) result.get("viewCount")).intValue() >= 1);
    }

    @Test
    void testUpdateArticleWithTags() {
        articleMapper.addArticle(createArticle("art_01", "旧标题", 1, 1));
        articleMapper.insertTag("tag_01", "art_01", "旧标签");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        body.put("tags", List.of("新标签A", "新标签B"));

        articleService.update("art_01", body);

        List<String> tags = articleMapper.findTagsByArticleId("art_01");
        assertEquals(2, tags.size());
    }

    @Test
    void testReviewArticleStatusNull() {
        Article article = createArticle("art_01", "文章", 1, 1);
        article.setStatus(null);
        articleMapper.addArticle(article);

        assertThrows(BusinessException.class, () -> articleService.review("art_01", "approve", null));
    }

    @Test
    void testReviewInvalidAction() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 2));

        assertThrows(BusinessException.class, () -> articleService.review("art_01", "invalid", null));
    }

    @Test
    void testUploadCoverContentTypeNull() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        byte[] content = new byte[100];
        MultipartFile file = new MockMultipartFile("file", "cover.txt", null, content);
        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testMapCategoryNumberString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "数字分类文章");
        body.put("content", "内容");
        body.put("category", "3");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testMapCategoryChinese() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "中文分类文章");
        body.put("content", "内容");
        body.put("category", "饮食");

        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testToCardNullViewCount() {
        Article article = createArticle("art_01", "文章", 1, 3);
        article.setViewCount(null);
        articleMapper.addArticle(article);

        Map<String, Object> card = (Map<String, Object>) ((List<?>) articleService.list(null, 1, 10).get("articles")).get(0);
        assertEquals(0, card.get("viewCount"));
    }

    @Test
    void testToggleFavoriteFromNull() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));

        Map<String, Object> result = articleService.toggleFavorite("user_01", "art_01");
        assertEquals(true, result.get("favorited"));
    }

    @Test
    void testUpdateArticleWithCategoryId() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("category_id", 2);

        articleService.update("art_01", body);
    }

    @Test
    void testDetailArticleNotFound() {
        assertThrows(BusinessException.class, () -> articleService.detail("not_exists", "user_01"));
    }

    @Test
    void testDetailArticleStatusNull() {
        Article article = createArticle("art_01", "文章", 1, 3);
        article.setStatus(null);
        articleMapper.addArticle(article);
        assertThrows(BusinessException.class, () -> articleService.detail("art_01", "user_01"));
    }

    @Test
    void testDetailArticleNotPublished() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 2));
        assertThrows(BusinessException.class, () -> articleService.detail("art_01", "user_01"));
    }

    @Test
    void testDetailWithNullUserId() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        Map<String, Object> result = articleService.detail("art_01", null);
        assertEquals(false, result.get("favorited"));
    }

    @Test
    void testReviewArticleNotFound() {
        assertThrows(BusinessException.class, () -> articleService.review("not_exists", "approve", null));
    }

    @Test
    void testReviewNotPending() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        assertThrows(BusinessException.class, () -> articleService.review("art_01", "approve", null));
    }

    @Test
    void testReviewRejectWithBlankReason() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 2));
        assertThrows(BusinessException.class, () -> articleService.review("art_01", "reject", ""));
    }

    @Test
    void testReviewRejectWithValidReason() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 2));
        Map<String, Object> result = articleService.review("art_01", "reject", "内容不符合要求");
        assertEquals("rejected", result.get("status"));
    }

    @Test
    void testDetailWithFavoritedTrue() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleMapper.upsertFavorite("fav_01", "user_01", "art_01", 1);
        
        Map<String, Object> result = articleService.detail("art_01", "user_01");
        assertEquals(true, result.get("favorited"));
    }

    @Test
    void testDetailWithFavoritedFalse() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleMapper.upsertFavorite("fav_01", "user_01", "art_01", 0);
        
        Map<String, Object> result = articleService.detail("art_01", "user_01");
        assertEquals(false, result.get("favorited"));
    }

    @Test
    void testUpdateArticleWithNullCategory() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        body.put("category", null);
        
        articleService.update("art_01", body);
    }

    @Test
    void testUpdateArticleWithAllFields() {
        articleMapper.addArticle(createArticle("art_01", "旧标题", 1, 1));
        articleMapper.insertTag("tag_01", "art_01", "旧标签");
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        body.put("content", "新内容");
        body.put("summary", "新摘要");
        body.put("tags", List.of("标签1", "标签2"));
        
        articleService.update("art_01", body);
        
        List<String> tags = articleMapper.findTagsByArticleId("art_01");
        assertEquals(2, tags.size());
    }

    @Test
    void testInvalidateListCacheWithException() {
        when(redisTemplate.keys(any())).thenThrow(new RuntimeException("Redis error"));
        
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        assertDoesNotThrow(() -> articleService.detail("art_01", "user_01"));
    }

    @Test
    void testToggleFavoriteArticleStatusNull() {
        Article article = new Article();
        article.setArticleId("art_01");
        article.setStatus(null);
        articleMapper.addArticle(article);
        
        assertThrows(BusinessException.class, () -> articleService.toggleFavorite("user_01", "art_01"));
    }

    @Test
    void testUpdatePublishedArticle() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        
        articleService.update("art_01", body);
    }

    @Test
    void testUploadCoverFileTooLarge() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        byte[] largeContent = new byte[6 * 1024 * 1024];
        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", largeContent);
        
        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testMapCategoryNumberFormatException() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "测试文章");
        body.put("content", "内容");
        body.put("category", "invalid_non_numeric");
        
        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testMapCategoryNumericString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "测试文章");
        body.put("content", "内容");
        body.put("category", "3");
        
        Map<String, Object> result = articleService.create(body);
        assertNotNull(result.get("articleId"));
    }

    @Test
    void testAdminDetailWithNullTags() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        articleMapper.setReturnNullTagsForArticleId("art_01");
        
        Map<String, Object> result = articleService.adminDetail("art_01");
        assertNotNull(result.get("tags"));
    }

    @Test
    void testInvalidateListCacheWithEmptyKeys() {
        when(redisTemplate.keys(any())).thenReturn(java.util.Set.of());
        
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleService.detail("art_01", "user_01");
    }

    @Test
    void testInvalidateListCacheWithNonNullKeys() {
        java.util.Set<String> keys = java.util.Set.of("article:list:1:1");
        when(redisTemplate.keys(any())).thenReturn(keys);
        
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleService.detail("art_01", "user_01");
        verify(redisTemplate, atLeastOnce()).delete(any(Collection.class));
    }

    @Test
    void testInvalidateListCacheWithNullKeys() {
        when(redisTemplate.keys(any())).thenReturn(null);
        
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleService.detail("art_01", "user_01");
    }

    @Test
    void testDetailWithRefreshedArticle() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        
        Map<String, Object> result = articleService.detail("art_01", "user_01");
        assertNotNull(result);
    }

    @Test
    void testToggleFavoriteCurrentZero() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleMapper.upsertFavorite("fav_01", "user_01", "art_01", 0);
        
        Map<String, Object> result = articleService.toggleFavorite("user_01", "art_01");
        assertEquals(true, result.get("favorited"));
    }

    @Test
    void testUpdateArticleNotPublished() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        
        articleService.update("art_01", body);
    }

    @Test
    void testUploadCoverWithNullFile() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        
        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", null));
    }

    @Test
    void testUploadCoverWithEmptyFile() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        
        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testUploadCoverWithNonImageContentType() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", new byte[]{1, 2, 3});
        
        assertThrows(BusinessException.class, () -> articleService.uploadCover("art_01", file));
    }

    @Test
    void testResolveCoverImageUrlWithHttpsUrl() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleMapper.updateCoverImageId("art_01", "https://example.com/cover.jpg");
        
        Map<String, Object> result = articleService.detail("art_01", "user_01");
        assertNotNull(result);
    }

    @Test
    void testDetailStatusNullPath() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        article.setStatus(null);
        articleMapper.addArticle(article);
        
        assertThrows(BusinessException.class, () -> articleService.detail("art_01", "user_01"));
    }

    @Test
    void testToggleFavoriteStatusNullPath() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        article.setStatus(null);
        articleMapper.addArticle(article);
        
        assertThrows(BusinessException.class, () -> articleService.toggleFavorite("user_01", "art_01"));
    }

    @Test
    void testUpdatePublishedArticleCallsSync() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        
        articleService.update("art_01", body);
    }

    @Test
    void testToAdminDetailNullTags() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 1));
        
        Map<String, Object> result = articleService.adminDetail("art_01");
        assertNotNull(result);
        assertEquals(List.of(), result.get("tags"));
    }

    @Test
    void testDetailWithRefreshedNull() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        articleMapper.addArticle(article);
        
        Stubs.ArticleMapperStub spyMapper = spy(articleMapper);
        when(spyMapper.findById("art_01")).thenReturn(article).thenReturn(null);
        
        ArticleService service = new ArticleService(
                spyMapper,
                redisTemplate,
                new ObjectMapper(),
                mock(MinioStorageService.class),
                mock(ArticleRecommendService.class),
                mock(ArticleVectorSyncService.class),
                "http://dify:8080",
                "",
                "test-api-key"
        );
        
        Map<String, Object> result = service.detail("art_01", null);
        assertEquals("测试文章", result.get("title"));
    }

    @Test
    void testResolveCoverImageUrlWithBlankCoverImageId() {
        Article article = createArticle("art_01", "测试文章", 1, 3);
        article.setCoverImageId("   ");
        articleMapper.addArticle(article);
        
        Map<String, Object> result = articleService.detail("art_01", null);
        assertNotNull(result.get("coverImage"));
    }

    @Test
    void testToggleFavoriteNotPublished() {
        articleMapper.addArticle(createArticle("art_01", "未发布文章", 1, 1));
        
        assertThrows(BusinessException.class, () -> articleService.toggleFavorite("user_01", "art_01"));
    }

    @Test
    void testToggleFavoritePendingReview() {
        articleMapper.addArticle(createArticle("art_01", "审核中文章", 1, 2));
        
        assertThrows(BusinessException.class, () -> articleService.toggleFavorite("user_01", "art_01"));
    }

    @Test
    void testUpdateArticleWithNullStatus() {
        Article article = createArticle("art_01", "文章", 1, 3);
        article.setStatus(null);
        articleMapper.addArticle(article);
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新标题");
        
        articleService.update("art_01", body);
    }

    @Test
    void testCreateArticleWithCategoryNumberString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "测试文章");
        body.put("content", "内容");
        body.put("category", "3");
        
        Map<String, Object> result = articleService.create(body);
        assertNotNull(result);
    }

    @Test
    void testCreateArticleWithCategoryInvalidString() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "测试文章");
        body.put("content", "内容");
        body.put("category", "invalid");
        
        Map<String, Object> result = articleService.create(body);
        assertNotNull(result);
    }

    @Test
    void testUploadCoverWithIoException() throws Exception {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.uploadArticleCover(any(), any(), anyLong(), any()))
                .thenThrow(new RuntimeException("IO error"));

        ObjectMapper objectMapper = new ObjectMapper();
        ArticleService service = new ArticleService(
                articleMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(ArticleRecommendService.class),
                mock(ArticleVectorSyncService.class),
                "http://dify:8080",
                "",
                "test-api-key"
        );

        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});
        
        assertThrows(BusinessException.class, () -> service.uploadCover("art_01", file));
    }

    @Test
    void testGetCachedListWithParseException() throws Exception {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn("invalid json");
        doThrow(new com.fasterxml.jackson.core.JsonParseException(null, "invalid json"))
                .when(objectMapper).readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class));

        ArticleService service = new ArticleService(
                articleMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(ArticleRecommendService.class),
                mock(ArticleVectorSyncService.class),
                "http://dify:8080",
                "",
                "test-api-key"
        );

        Map<String, Object> result = service.list(null, 1, 10);
        assertNotNull(result);
    }

    @Test
    void testGetCachedListWithWriteException() throws Exception {
        ObjectMapper objectMapper = spy(new ObjectMapper());
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("write error") {})
                .when(objectMapper).writeValueAsString(any());

        ArticleService service = new ArticleService(
                articleMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(ArticleRecommendService.class),
                mock(ArticleVectorSyncService.class),
                "http://dify:8080",
                "",
                "test-api-key"
        );

        Map<String, Object> result = service.list(null, 1, 10);
        assertNotNull(result);
    }

    @Test
    void testUploadCoverWithNullContentType() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        MinioStorageService minioStorage = mock(MinioStorageService.class);

        ArticleService service = new ArticleService(
                articleMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(ArticleRecommendService.class),
                mock(ArticleVectorSyncService.class),
                "http://dify:8080",
                "",
                "test-api-key"
        );

        MultipartFile file = new MockMultipartFile("file", "cover.jpg", null, new byte[]{1, 2, 3});
        
        assertThrows(BusinessException.class, () -> service.uploadCover("art_01", file));
    }

    @Test
    void testUploadCoverSuccess() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        MinioStorageService minioStorage = mock(MinioStorageService.class);
        when(minioStorage.buildArticleCoverUrl(any())).thenReturn("/images/articles/art_01.jpg");

        ArticleService service = new ArticleService(
                articleMapper,
                redisTemplate,
                objectMapper,
                minioStorage,
                mock(ArticleRecommendService.class),
                mock(ArticleVectorSyncService.class),
                "http://dify:8080",
                "",
                "test-api-key"
        );

        MultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});
        
        Map<String, Object> result = service.uploadCover("art_01", file);
        assertNotNull(result);
        assertNotNull(result.get("coverImage"));
    }

    @Test
    void testMapCategoryWithInvalidNumber() throws Exception {
        Method mapCategoryMethod = ArticleService.class.getDeclaredMethod("mapCategory", String.class);
        mapCategoryMethod.setAccessible(true);
        
        int result = (int) mapCategoryMethod.invoke(articleService, "invalid");
        assertEquals(1, result);
    }

    @Test
    void testMapCategoryWithValidNumber() throws Exception {
        Method mapCategoryMethod = ArticleService.class.getDeclaredMethod("mapCategory", String.class);
        mapCategoryMethod.setAccessible(true);
        
        int result = (int) mapCategoryMethod.invoke(articleService, "99");
        assertEquals(99, result);
    }

    @Test
    void testAdminDetailWithNonNullTags() {
        articleMapper.addArticle(createArticle("art_01", "文章", 1, 3));
        articleMapper.insertTag("tag_001", "art_01", "tag1");
        articleMapper.insertTag("tag_002", "art_01", "tag2");
        
        Map<String, Object> result = articleService.adminDetail("art_01");
        assertNotNull(result);
    }

    private Article createArticle(String id, String title, int category, int status) {
        return Stubs.createArticle(id, title, category, status);
    }
}
