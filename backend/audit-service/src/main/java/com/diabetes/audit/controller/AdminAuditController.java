package com.diabetes.audit.controller;

import com.diabetes.audit.config.AdminAuthInterceptor;
import com.diabetes.audit.dto.BatchDeleteAuditLogsRequest;
import com.diabetes.audit.service.AuditLogService;
import com.diabetes.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/admin/audit/logs", "/api/v2/admin/audit/logs"})
public class AdminAuditController {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AuditLogService auditLogService;

    public AdminAuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String userId,
                                                 @RequestParam(required = false) String action,
                                                 @RequestParam(required = false) String actions,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer result,
                                                 @RequestParam(required = false) String startTime,
                                                 @RequestParam(required = false) String endTime,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditLogService.adminList(
                userId, action, parseActions(actions), keyword, result, startTime, endTime, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String userId,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String actions,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer result,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime,
                                         HttpServletRequest request) {
        String csv = auditLogService.exportCsv(
                userId, action, parseActions(actions), keyword, result, startTime, endTime);
        String adminId = currentAdminId(request);
        if (adminId != null) {
            auditLogService.createExportAudit(adminId, resolveClientIp(request), request.getHeader("User-Agent"));
        }
        String filename = "audit_logs_" + LocalDateTime.now().format(FILE_TIME) + ".csv";
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/actions")
    public ApiResponse<Map<String, Object>> actions() {
        return ApiResponse.ok(Map.of("actions", auditLogService.listActions()));
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(auditLogService.adminOverview(days));
    }

    @GetMapping("/{logId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String logId) {
        return ApiResponse.ok(auditLogService.adminDetail(logId));
    }

    @DeleteMapping("/{logId}")
    public ApiResponse<String> delete(@PathVariable String logId, HttpServletRequest request) {
        auditLogService.delete(
                logId,
                currentAdminId(request),
                resolveClientIp(request),
                request.getHeader("User-Agent"));
        return ApiResponse.ok("删除成功", "删除成功");
    }

    @DeleteMapping
    public ApiResponse<Map<String, Object>> batchDelete(@Valid @RequestBody BatchDeleteAuditLogsRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.ok(auditLogService.batchDelete(
                body.logIds(),
                currentAdminId(request),
                resolveClientIp(request),
                request.getHeader("User-Agent")));
    }

    private String currentAdminId(HttpServletRequest request) {
        Object adminId = request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID);
        return adminId == null ? null : adminId.toString();
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

    private List<String> parseActions(String actions) {
        if (actions == null || actions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(actions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
