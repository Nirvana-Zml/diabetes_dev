package com.diabetes.article.controller;

import com.diabetes.article.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminArticleControllerTest {

    @Mock
    private ArticleService articleService;

    @InjectMocks
    private AdminArticleController controller;

    @Test
    void listAndDetail() {
        Map<String, Object> list = Map.of("articles", java.util.List.of(), "total", 0);
        when(articleService.adminList(3, "关键词", 1, 10)).thenReturn(list);
        assertEquals(list, controller.list(3, "关键词", 1, 10).data());

        Map<String, Object> detail = Map.of("articleId", "art_1");
        when(articleService.adminDetail("art_1")).thenReturn(detail);
        assertEquals(detail, controller.detail("art_1").data());
    }

    @Test
    void createUpdateDeleteAndReviewFlow() {
        Map<String, Object> body = Map.of("title", "标题", "content", "内容");
        Map<String, Object> created = Map.of("articleId", "art_new", "status", "draft");
        when(articleService.create(body)).thenReturn(created);
        assertEquals(created, controller.create(body).data());

        Map<String, Object> updated = Map.of("articleId", "art_1", "status", "draft");
        when(articleService.update("art_1", body)).thenReturn(updated);
        assertEquals(updated, controller.update("art_1", body).data());

        doNothing().when(articleService).delete("art_1");
        var deleteResult = controller.delete("art_1");
        assertEquals(200, deleteResult.code());
        verify(articleService).delete("art_1");

        Map<String, Object> submitted = Map.of("articleId", "art_1", "status", "pending");
        when(articleService.submitReview("art_1")).thenReturn(submitted);
        assertEquals(submitted, controller.submit("art_1").data());

        Map<String, Object> approved = Map.of("articleId", "art_1", "status", "published");
        when(articleService.review("art_1", "approve", null)).thenReturn(approved);
        assertEquals(approved, controller.review("art_1", Map.of("action", "approve")).data());
    }

    @Test
    void uploadCoverPendingReviewAndAiDraftConfig() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Map<String, Object> upload = Map.of("coverImage", "http://minio/cover.jpg?v=1");
        when(articleService.uploadCover("art_1", file)).thenReturn(upload);
        assertEquals(upload, controller.uploadCover("art_1", file).data());

        Map<String, Object> pending = Map.of("articles", java.util.List.of(), "total", 0);
        when(articleService.pendingReview(1, 10)).thenReturn(pending);
        assertEquals(pending, controller.pendingReview(1, 10).data());

        Map<String, Object> config = Map.of("workflowUrl", "http://dify/run");
        when(articleService.aiDraftConfig()).thenReturn(config);
        assertEquals(config, controller.aiDraftConfig().data());
    }
}
