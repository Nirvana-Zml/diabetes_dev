package com.diabetes.audit.controller;

import com.diabetes.audit.dto.CreateAuditLogRequest;
import com.diabetes.audit.service.AuditLogService;
import com.diabetes.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalAuditControllerTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private HttpServletRequest request;

    private InternalAuditController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalAuditController(auditLogService, "secret-key");
    }

    @Test
    void createAcceptsValidInternalRequestAndEnrichesMissingFields() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", Map.of("role", "user"), null, null, 1);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("User-Agent")).thenReturn("Service");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_1"));

        assertEquals("aud_1", controller.create("secret-key", body, request).data().get("logId"));

        ArgumentCaptor<CreateAuditLogRequest> captor = ArgumentCaptor.forClass(CreateAuditLogRequest.class);
        verify(auditLogService).create(captor.capture());
        assertEquals("10.0.0.2", captor.getValue().ipAddress());
        assertEquals("Service", captor.getValue().userAgent());
    }

    @Test
    void createKeepsProvidedIpAndUserAgent() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, "1.2.3.4", "CustomAgent", 1);
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_1"));

        controller.create("secret-key", body, request);

        ArgumentCaptor<CreateAuditLogRequest> captor = ArgumentCaptor.forClass(CreateAuditLogRequest.class);
        verify(auditLogService).create(captor.capture());
        assertEquals("1.2.3.4", captor.getValue().ipAddress());
        assertEquals("CustomAgent", captor.getValue().userAgent());
    }

    @Test
    void createUsesForwardedForHeader() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn(null);
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_1"));

        controller.create("secret-key", body, request);

        ArgumentCaptor<CreateAuditLogRequest> captor = ArgumentCaptor.forClass(CreateAuditLogRequest.class);
        verify(auditLogService).create(captor.capture());
        assertEquals("203.0.113.9", captor.getValue().ipAddress());
    }

    @Test
    void createUsesRealIpHeader() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.3");
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_1"));

        controller.create("secret-key", body, request);

        ArgumentCaptor<CreateAuditLogRequest> captor = ArgumentCaptor.forClass(CreateAuditLogRequest.class);
        verify(auditLogService).create(captor.capture());
        assertEquals("198.51.100.3", captor.getValue().ipAddress());
    }

    @Test
    void createRejectsInvalidInternalKey() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.create("wrong-key", body, request));
        assertEquals(403, ex.getCode());
        verify(auditLogService, never()).create(any());
    }

    @Test
    void createRejectsNullKeyWhenConfigured() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.create(null, body, request));
        assertEquals(403, ex.getCode());
    }

    @Test
    void createAllowsNullConfiguredKey() {
        controller = new InternalAuditController(auditLogService, null);
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_3"));

        assertEquals("aud_3", controller.create(null, body, request).data().get("logId"));
    }

    @Test
    void createFillsBlankIpAndUserAgentFromRequest() {
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, "  ", "  ", 1);
        when(request.getHeader("X-Forwarded-For")).thenReturn("  ");
        when(request.getHeader("X-Real-IP")).thenReturn("");
        when(request.getHeader("User-Agent")).thenReturn("FilledAgent");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_4"));

        controller.create("secret-key", body, request);

        ArgumentCaptor<CreateAuditLogRequest> captor = ArgumentCaptor.forClass(CreateAuditLogRequest.class);
        verify(auditLogService).create(captor.capture());
        assertEquals("10.0.0.5", captor.getValue().ipAddress());
        assertEquals("FilledAgent", captor.getValue().userAgent());
    }

    @Test
    void createAllowsBlankConfiguredKey() {
        controller = new InternalAuditController(auditLogService, "");
        CreateAuditLogRequest body = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);
        when(auditLogService.create(any(CreateAuditLogRequest.class)))
                .thenReturn(Map.of("logId", "aud_2"));

        assertEquals("aud_2", controller.create(null, body, request).data().get("logId"));
    }
}
