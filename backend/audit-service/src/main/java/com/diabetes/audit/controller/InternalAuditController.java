package com.diabetes.audit.controller;

import com.diabetes.audit.dto.CreateAuditLogRequest;
import com.diabetes.audit.service.AuditLogService;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/internal/audit/logs", "/api/v2/internal/audit/logs"})
public class InternalAuditController {

    private final AuditLogService auditLogService;
    private final String internalKey;

    public InternalAuditController(AuditLogService auditLogService,
                                   @Value("${dify-internal.key}") String internalKey) {
        this.auditLogService = auditLogService;
        this.internalKey = internalKey;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestHeader(value = "X-Dify-Key", required = false) String difyKey,
                                                   @Valid @RequestBody CreateAuditLogRequest request,
                                                   HttpServletRequest httpRequest) {
        verifyInternalKey(difyKey);
        CreateAuditLogRequest enriched = enrichRequest(request, httpRequest);
        return ApiResponse.ok(auditLogService.create(enriched));
    }

    private void verifyInternalKey(String difyKey) {
        if (internalKey == null || internalKey.isBlank()) {
            return;
        }
        if (difyKey == null || !internalKey.equals(difyKey)) {
            throw new BusinessException(403, "内部接口密钥无效");
        }
    }

    private CreateAuditLogRequest enrichRequest(CreateAuditLogRequest request, HttpServletRequest httpRequest) {
        String ip = request.ipAddress();
        if (ip == null || ip.isBlank()) {
            ip = resolveClientIp(httpRequest);
        }
        String userAgent = request.userAgent();
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = httpRequest.getHeader("User-Agent");
        }
        return new CreateAuditLogRequest(
                request.userId(),
                request.action(),
                request.resource(),
                request.detail(),
                ip,
                userAgent,
                request.result()
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
