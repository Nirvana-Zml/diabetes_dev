package com.diabetes.article.controller;

import com.diabetes.article.service.ArticleService;
import com.diabetes.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminArticleControllerTest {

    private AdminArticleController controller;
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        controller = new AdminArticleController(articleService, null);
    }

    @Test
    void testList() {
        when(articleService.adminList(null, null, 1, 10)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.list(null, null, 1, 10);
        assertEquals(200, response.code());
    }

    @Test
    void testDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", "测试文章");
        when(articleService.adminDetail("art_01")).thenReturn(detail);

        ApiResponse<Map<String, Object>> response = controller.detail("art_01");
        assertEquals(200, response.code());
        assertEquals("测试文章", response.data().get("title"));
    }

    @Test
    void testCreate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "新文章");
        when(articleService.create(body)).thenReturn(Map.of("articleId", "art_01"));

        ApiResponse<Map<String, Object>> response = controller.create(body, new MockHttpServletRequest());
        assertEquals(200, response.code());
        assertEquals("art_01", response.data().get("articleId"));
    }

    @Test
    void testUpdate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "更新标题");
        when(articleService.update("art_01", body)).thenReturn(Map.of("articleId", "art_01"));

        ApiResponse<Map<String, Object>> response = controller.update("art_01", body, new MockHttpServletRequest());
        assertEquals(200, response.code());
    }

    @Test
    void testUploadCover() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(articleService.uploadCover("art_01", file)).thenReturn(Map.of("url", "/images/articles/art_01.jpg"));

        ApiResponse<Map<String, Object>> response = controller.uploadCover("art_01", file, new MockHttpServletRequest());
        assertEquals(200, response.code());
    }

    @Test
    void testDelete() {
        doNothing().when(articleService).delete("art_01");

        ApiResponse<String> response = controller.delete("art_01", new MockHttpServletRequest());
        assertEquals(200, response.code());
        assertEquals("删除成功", response.data());
    }

    @Test
    void testSubmit() {
        when(articleService.submitReview("art_01")).thenReturn(Map.of("status", "pending"));

        ApiResponse<Map<String, Object>> response = controller.submit("art_01", new MockHttpServletRequest());
        assertEquals(200, response.code());
    }

    @Test
    void testReviewApprove() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", "approve");
        when(articleService.review("art_01", "approve", null)).thenReturn(Map.of("status", "published"));

        ApiResponse<Map<String, Object>> response = controller.review("art_01", body, new MockHttpServletRequest());
        assertEquals(200, response.code());
    }

    @Test
    void testReviewReject() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", "reject");
        body.put("reason", "内容不符合要求");
        when(articleService.review("art_01", "reject", "内容不符合要求")).thenReturn(Map.of("status", "rejected"));

        ApiResponse<Map<String, Object>> response = controller.review("art_01", body, new MockHttpServletRequest());
        assertEquals(200, response.code());
    }

    @Test
    void testPendingReview() {
        when(articleService.pendingReview(1, 10)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.pendingReview(1, 10);
        assertEquals(200, response.code());
    }

    @Test
    void testAiDraftConfig() {
        when(articleService.aiDraftConfig()).thenReturn(Map.of());

        ApiResponse<Map<String, Object>> response = controller.aiDraftConfig();
        assertEquals(200, response.code());
    }
}
