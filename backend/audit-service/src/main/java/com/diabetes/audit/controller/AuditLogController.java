package com.diabetes.audit.controller;

import com.diabetes.audit.dto.CreateAuditLogRequest;
import com.diabetes.audit.entity.AuditLog;
import com.diabetes.audit.service.AuditLogService;
import com.diabetes.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping("/logs")
    public ApiResponse<AuditLog> create(@Valid @RequestBody CreateAuditLogRequest request) {
        return ApiResponse.ok(auditLogService.create(request));
    }

    @GetMapping("/logs/user/{userId}")
    public ApiResponse<List<AuditLog>> list(@PathVariable String userId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditLogService.listByUser(userId, page, size));
    }
}
