package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import com.diabetes.home.config.AdminAuthInterceptor;
import com.diabetes.home.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminVideoControllerTest {

    @Test
    void adminVideoControllerDelegatesToServiceAndLogsAudit() {
        VideoService service = mock(VideoService.class);
        AuditServiceClient auditServiceClient = mock(AuditServiceClient.class);
        AdminVideoController controller = new AdminVideoController(service, auditServiceClient);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID, "adm_001");
        request.setRemoteAddr("127.0.0.1");

        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(service.adminList("k", 1, 2)).thenReturn(Map.of("total", 0));
        when(service.adminDetail("v1")).thenReturn(Map.of("videoId", "v1"));
        when(service.create(Map.of("title", "t"))).thenReturn(Map.of("videoId", "v2"));
        when(service.update("v1", Map.of("title", "t2"))).thenReturn(Map.of("videoId", "v1"));
        when(service.uploadCover("v1", file)).thenReturn(Map.of("coverUrl", "c"));
        when(service.uploadVideoFile("v1", file)).thenReturn(Map.of("videoUrl", "v", "duration", "01:00"));

        assertEquals(0, controller.list("k", 1, 2).data().get("total"));
        assertEquals("v1", controller.detail("v1").data().get("videoId"));
        assertEquals("v2", controller.create(Map.of("title", "t"), request).data().get("videoId"));
        assertEquals("v1", controller.update("v1", Map.of("title", "t2"), request).data().get("videoId"));
        assertEquals("删除成功", controller.delete("v1", request).data());
        assertEquals("c", controller.uploadCover("v1", file, request).data().get("coverUrl"));
        assertEquals("v", controller.uploadFile("v1", file, request).data().get("videoUrl"));
        verify(service).delete("v1");
        verify(auditServiceClient).log(eq("adm_001"), eq("video.create"), eq("v2"), any(), eq("127.0.0.1"), isNull(), eq(1));
        verify(auditServiceClient).log(eq("adm_001"), eq("video.update"), eq("v1"), any(), eq("127.0.0.1"), isNull(), eq(1));
        verify(auditServiceClient).log(eq("adm_001"), eq("video.delete"), eq("v1"), any(), eq("127.0.0.1"), isNull(), eq(1));
        verify(auditServiceClient).log(eq("adm_001"), eq("video.cover.upload"), eq("v1"), any(), eq("127.0.0.1"), isNull(), eq(1));
        verify(auditServiceClient).log(eq("adm_001"), eq("video.file.upload"), eq("v1"), any(), eq("127.0.0.1"), isNull(), eq(1));
    }
}
