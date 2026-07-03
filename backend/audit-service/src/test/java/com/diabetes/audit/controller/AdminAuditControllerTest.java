package com.diabetes.audit.controller;

import com.diabetes.audit.config.AdminAuthInterceptor;
import com.diabetes.audit.dto.BatchDeleteAuditLogsRequest;
import com.diabetes.audit.service.AuditLogService;
import com.diabetes.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AdminAuditController controller;

    @Test
    void deleteFallsBackToRemoteAddrWhenRealIpNull() {
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.2.2.2");

        controller.delete("aud_y", request);

        verify(auditLogService).delete("aud_y", "adm_001", "10.2.2.2", null);
    }

    @Test
    void deleteFallsBackToRemoteAddrWhenRealIpBlank() {
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("10.1.1.1");

        controller.delete("aud_blank_real_ip", request);

        verify(auditLogService).delete("aud_blank_real_ip", "adm_001", "10.1.1.1", null);
    }

    @Test
    void listTreatsBlankActionsAsEmpty() {
        Map<String, Object> list = Map.of("logs", List.of(), "total", 0, "page", 1, "size", 20);
        when(auditLogService.adminList(isNull(), isNull(), eq(List.of()), isNull(), isNull(), isNull(), isNull(),
                eq(1), eq(20))).thenReturn(list);

        assertEquals(list, controller.list(null, null, "  ", null, null, null, null, 1, 20).data());
    }

    @Test
    void deleteUsesRealIpWhenForwardedHeaderBlank() {
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.8");

        controller.delete("aud_x", request);

        verify(auditLogService).delete("aud_x", "adm_001", "198.51.100.8", null);
    }

    @Test
    void listParsesActionsParameter() {
        Map<String, Object> list = Map.of("logs", List.of(), "total", 0, "page", 1, "size", 20);
        when(auditLogService.adminList(isNull(), isNull(), eq(List.of("user.login", "audit.delete")),
                isNull(), isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(list);

        assertEquals(list, controller.list(null, null, " user.login , audit.delete , ",
                null, null, null, null, 1, 20).data());
    }

    @Test
    void exportReturnsCsvAttachmentAndRecordsAudit() {
        when(auditLogService.exportCsv(isNull(), isNull(), eq(List.of("user.login")),
                isNull(), isNull(), isNull(), isNull())).thenReturn("csv-body");
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(request.getHeader("User-Agent")).thenReturn("AdminBrowser");

        ResponseEntity<byte[]> response = controller.export(
                null, null, "user.login", null, null, null, null, request);

        assertArrayEquals("csv-body".getBytes(StandardCharsets.UTF_8), response.getBody());
        assertEquals(MediaType.parseMediaType("text/csv;charset=UTF-8"), response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("audit_logs_"));
        verify(auditLogService).createExportAudit("adm_001", "203.0.113.1", "AdminBrowser");
    }

    @Test
    void exportSkipsAuditWhenAdminMissing() {
        when(auditLogService.exportCsv(isNull(), isNull(), eq(List.of()), isNull(), isNull(), isNull(), isNull()))
                .thenReturn("csv");
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn(null);

        controller.export(null, null, null, null, null, null, null, request);

        verify(auditLogService, never()).createExportAudit(any(), any(), any());
    }

    @Test
    void listAndActions() {
        Map<String, Object> list = Map.of("logs", List.of(), "total", 0, "page", 1, "size", 20);
        when(auditLogService.adminList(null, null, List.of(), null, null, null, null, 1, 20)).thenReturn(list);
        assertEquals(list, controller.list(null, null, null, null, null, null, null, 1, 20).data());

        Map<String, Object> overview = Map.of("today", Map.of("total", 1, "failed", 0, "success", 1));
        when(auditLogService.adminOverview(7)).thenReturn(overview);
        assertEquals(overview, controller.overview(7).data());

        when(auditLogService.listActions()).thenReturn(List.of("user.login", "audit.delete"));
        assertEquals(List.of("user.login", "audit.delete"), controller.actions().data().get("actions"));
    }

    @Test
    void detailDeleteAndBatchDelete() {
        Map<String, Object> detail = Map.of("logId", "aud_1");
        when(auditLogService.adminDetail("aud_1")).thenReturn(detail);
        assertEquals(detail, controller.detail("aud_1").data());

        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("AdminBrowser");

        ApiResponse<String> deleteResponse = controller.delete("aud_1", request);
        assertEquals("删除成功", deleteResponse.message());
        verify(auditLogService).delete("aud_1", "adm_001", "203.0.113.1", "AdminBrowser");

        when(auditLogService.batchDelete(List.of("aud_1", "aud_2"), "adm_001", "203.0.113.1", "AdminBrowser"))
                .thenReturn(Map.of("deleted", 2));
        ApiResponse<Map<String, Object>> batchResponse = controller.batchDelete(
                new BatchDeleteAuditLogsRequest(List.of("aud_1", "aud_2")), request);
        assertEquals(2, batchResponse.data().get("deleted"));
    }

    @Test
    void deleteUsesRealIpWhenForwardedHeaderMissing() {
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.8");
        when(request.getHeader("User-Agent")).thenReturn(null);

        controller.delete("aud_2", request);

        verify(auditLogService).delete("aud_2", null, "198.51.100.8", null);
    }

    @Test
    void deleteFallsBackToRemoteAddr() {
        when(request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID)).thenReturn("adm_001");
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.delete("aud_3", request);

        verify(auditLogService).delete("aud_3", "adm_001", "127.0.0.1", null);
    }
}
