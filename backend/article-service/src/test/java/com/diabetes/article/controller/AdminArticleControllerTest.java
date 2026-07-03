package com.diabetes.article.controller;

import com.diabetes.article.config.AdminAuthInterceptor;
import com.diabetes.article.service.ArticleService;
import com.diabetes.common.client.AuditServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminArticleControllerTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private AuditServiceClient auditServiceClient;

    @Mock
    private HttpServletRequest request;

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
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals(created, controller.create(body, request).data());
        verify(auditServiceClient).log(eq("adm_001"), eq("article.create"), eq("art_new"), any(), eq("127.0.0.1"), isNull(), eq(1));

        Map<String, Object> updated = Map.of("articleId", "art_1", "status", "draft");
        when(articleService.update("art_1", body)).thenReturn(updated);
        assertEquals(updated, controller.update("art_1", body, request).data());
        verify(auditServiceClient).log(eq("adm_001"), eq("article.update"), eq("art_1"), any(), eq("127.0.0.1"), isNull(), eq(1));

        doNothing().when(articleService).delete("art_1");
        var deleteResult = controller.delete("art_1", request);
        assertEquals(200, deleteResult.code());
        verify(articleService).delete("art_1");
        verify(auditServiceClient).log(eq("adm_001"), eq("article.delete"), eq("art_1"), any(), eq("127.0.0.1"), isNull(), eq(1));

        Map<String, Object> submitted = Map.of("articleId", "art_1", "status", "pending");
        when(articleService.submitReview("art_1")).thenReturn(submitted);
        assertEquals(submitted, controller.submit("art_1", request).data());
        verify(auditServiceClient).log(eq("adm_001"), eq("article.review"), eq("art_1"), any(), eq("127.0.0.1"), isNull(), eq(1));

        Map<String, Object> approved = Map.of("articleId", "art_1", "status", "published");
        when(articleService.review("art_1", "approve", null)).thenReturn(approved);
        assertEquals(approved, controller.review("art_1", Map.of("action", "approve"), request).data());
        verify(auditServiceClient).log(eq("adm_001"), eq("article.publish"), eq("art_1"), any(), eq("127.0.0.1"), isNull(), eq(1));
    }

    @Test
    void uploadCoverPendingReviewAndAiDraftConfig() {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Map<String, Object> upload = Map.of("coverImage", "http://minio/cover.jpg?v=1");
        when(articleService.uploadCover("art_1", file)).thenReturn(upload);
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals(upload, controller.uploadCover("art_1", file, request).data());
        verify(auditServiceClient).log(eq("adm_001"), eq("article.cover.upload"), eq("art_1"), any(), eq("127.0.0.1"), isNull(), eq(1));

        Map<String, Object> pending = Map.of("articles", java.util.List.of(), "total", 0);
        when(articleService.pendingReview(1, 10)).thenReturn(pending);
        assertEquals(pending, controller.pendingReview(1, 10).data());

        Map<String, Object> config = Map.of("workflowUrl", "http://dify/run");
        when(articleService.aiDraftConfig()).thenReturn(config);
        assertEquals(config, controller.aiDraftConfig().data());
    }

    @Test
    void reviewRejectWithReasonUsesArticleReviewAuditAction() {
        Map<String, Object> rejected = Map.of("articleId", "art_1", "status", "rejected");
        when(articleService.review("art_1", "reject", "内容不合规")).thenReturn(rejected);
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.2");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        assertEquals(rejected, controller.review("art_1",
                Map.of("action", "reject", "reason", "内容不合规"), request).data());

        verify(auditServiceClient).log(
                eq("adm_001"),
                eq("article.review"),
                eq("art_1"),
                argThat(detail -> "reject".equals(detail.get("reviewAction"))
                        && "rejected".equals(detail.get("status"))
                        && "内容不合规".equals(detail.get("reason"))),
                eq("203.0.113.1"),
                eq("JUnit"),
                eq(1));
    }

    @Test
    void resolveClientIpPrefersRealIpWhenForwardedHeaderMissing() {
        Map<String, Object> body = Map.of("content", "内容");
        Map<String, Object> created = Map.of("articleId", "art_new");
        when(articleService.create(body)).thenReturn(created);
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(request.getHeader("X-Real-IP")).thenReturn(" 10.0.0.8 ");

        assertEquals(created, controller.create(body, request).data());

        verify(auditServiceClient).log(
                eq("adm_001"),
                eq("article.create"),
                eq("art_new"),
                eq(Map.of()),
                eq("10.0.0.8"),
                isNull(),
                eq(1));
    }

    @Test
    void resolveClientIpFallsBackToRemoteAddrWhenProxyHeadersBlank() {
        Map<String, Object> body = Map.of("title", "标题");
        Map<String, Object> created = Map.of("articleId", "art_new", "status", "draft");
        when(articleService.create(body)).thenReturn(created);
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        assertEquals(created, controller.create(body, request).data());

        verify(auditServiceClient).log(
                eq("adm_001"),
                eq("article.create"),
                eq("art_new"),
                any(),
                eq("192.168.1.10"),
                isNull(),
                eq(1));
    }

    @Test
    void auditLoggingSkippedWhenAdminIdMissing() {
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn(null);

        Map<String, Object> body = Map.of("content", "内容");
        Map<String, Object> created = Map.of("articleId", "art_new");
        when(articleService.create(body)).thenReturn(created);
        assertEquals(created, controller.create(body, request).data());

        Map<String, Object> updated = Map.of("articleId", "art_1");
        when(articleService.update("art_1", body)).thenReturn(updated);
        assertEquals(updated, controller.update("art_1", body, request).data());

        doNothing().when(articleService).delete("art_1");
        assertEquals(200, controller.delete("art_1", request).code());

        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[]{1});
        when(articleService.uploadCover("art_1", file)).thenReturn(Map.of("coverImage", "url"));
        assertNotNull(controller.uploadCover("art_1", file, request).data());

        when(articleService.submitReview("art_1")).thenReturn(Map.of("status", "pending"));
        assertNotNull(controller.submit("art_1", request).data());

        when(articleService.review("art_1", "reject", null))
                .thenReturn(Map.of("status", "rejected"));
        assertNotNull(controller.review("art_1", Map.of("action", "reject"), request).data());

        verifyNoInteractions(auditServiceClient);
    }
}
