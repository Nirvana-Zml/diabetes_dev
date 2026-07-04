package com.diabetes.article.controller;

import com.diabetes.article.service.ArticleService;
import com.diabetes.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleControllerTest {

    private ArticleController controller;
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        controller = new ArticleController(articleService);
    }

    @Test
    void testList() {
        when(articleService.list(null, 1, 10)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.list(null, 1, 10);
        assertEquals(200, response.code());
        assertNotNull(response.data());
    }

    @Test
    void testSearch() {
        when(articleService.search("关键词", 1, 10)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.search("关键词", 1, 10);
        assertEquals(200, response.code());
    }

    @Test
    void testDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("title", "测试文章");
        when(articleService.detail("art_01", "user_01")).thenReturn(detail);

        ApiResponse<Map<String, Object>> response = controller.detail("art_01", createMockRequest("user_01"));
        assertEquals(200, response.code());
        assertEquals("测试文章", response.data().get("title"));
    }

    @Test
    void testFavorite() {
        when(articleService.toggleFavorite("user_01", "art_01")).thenReturn(Map.of("favorited", true));

        ApiResponse<Map<String, Object>> response = controller.favorite("user_01", "art_01");
        assertEquals(200, response.code());
        assertTrue((Boolean) response.data().get("favorited"));
    }

    @Test
    void testFavorites() {
        when(articleService.favorites("user_01", 1, 10)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.favorites("user_01", 1, 10);
        assertEquals(200, response.code());
    }

    @Test
    void testRecommend() {
        when(articleService.recommend("user_01", 1, 10, null)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.recommend(1, 10, null, createMockRequest("user_01"));
        assertEquals(200, response.code());
    }

    @Test
    void testRelated() {
        when(articleService.related("art_01", "user_01", 5)).thenReturn(Map.of("articles", java.util.List.of()));

        ApiResponse<Map<String, Object>> response = controller.related("art_01", 5, createMockRequest("user_01"));
        assertEquals(200, response.code());
    }

    @Test
    void testReadEvent() {
        ApiResponse<Map<String, Object>> response = controller.readEvent("user_01", "art_01", null);
        assertEquals(200, response.code());
        assertTrue((Boolean) response.data().get("recorded"));
    }

    @Test
    void testReadEventWithBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durationSec", 120);
        body.put("source", "homepage");

        ApiResponse<Map<String, Object>> response = controller.readEvent("user_01", "art_01", body);
        assertEquals(200, response.code());
    }

    @Test
    void testReadEventWithDurationSec() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("duration_sec", 60);

        ApiResponse<Map<String, Object>> response = controller.readEvent("user_01", "art_01", body);
        assertEquals(200, response.code());
    }

    @Test
    void testReadEventWithNonNumberDuration() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durationSec", "not_a_number");
        body.put("source", "detail");

        ApiResponse<Map<String, Object>> response = controller.readEvent("user_01", "art_01", body);
        assertEquals(200, response.code());
    }

    @Test
    void testDifyRecommendWorkflowSpec() {
        when(articleService.getDifyRecommendWorkflowSpec()).thenReturn(Map.of());

        ApiResponse<Map<String, Object>> response = controller.difyRecommendWorkflowSpec();
        assertEquals(200, response.code());
    }

    private jakarta.servlet.http.HttpServletRequest createMockRequest(String userId) {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getAttribute("optionalUserId")).thenReturn(userId);
        return request;
    }
}
