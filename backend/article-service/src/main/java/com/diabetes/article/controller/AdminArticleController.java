package com.diabetes.article.controller;

import com.diabetes.article.config.AdminAuthInterceptor;
import com.diabetes.article.service.ArticleService;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {

    private final ArticleService articleService;
    private final AuditServiceClient auditServiceClient;

    public AdminArticleController(ArticleService articleService,
                                  AuditServiceClient auditServiceClient) {
        this.articleService = articleService;
        this.auditServiceClient = auditServiceClient;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.adminList(status, keyword, page, size));
    }

    @GetMapping("/{articleId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String articleId) {
        return ApiResponse.ok(articleService.adminDetail(articleId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        Map<String, Object> result = articleService.create(body);
        logArticleAudit(request, String.valueOf(result.get("articleId")), "article.create",
                detailWithTitle(body, result));
        return ApiResponse.ok(result);
    }

    @PutMapping("/{articleId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String articleId,
                                                 @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        Map<String, Object> result = articleService.update(articleId, body);
        logArticleAudit(request, articleId, "article.update", detailWithTitle(body, result));
        return ApiResponse.ok(result);
    }

    /** 上传封面图（MinIO article bucket，对象名 {articleId}.jpg） */
    @PostMapping(value = "/{articleId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadCover(@PathVariable String articleId,
                                                        @RequestPart("file") MultipartFile file,
                                                        HttpServletRequest request) {
        Map<String, Object> result = articleService.uploadCover(articleId, file);
        logArticleAudit(request, articleId, "article.cover.upload",
                Map.of("fileName", file.getOriginalFilename()));
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{articleId}")
    public ApiResponse<String> delete(@PathVariable String articleId, HttpServletRequest request) {
        articleService.delete(articleId);
        logArticleAudit(request, articleId, "article.delete", Map.of());
        return ApiResponse.ok("删除成功", "删除成功");
    }

    @PutMapping("/{articleId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String articleId,
                                                   HttpServletRequest request) {
        Map<String, Object> result = articleService.submitReview(articleId);
        logArticleAudit(request, articleId, "article.review", Map.of("phase", "submit", "status", result.get("status")));
        return ApiResponse.ok(result);
    }

    @PutMapping("/{articleId}/review")
    public ApiResponse<Map<String, Object>> review(@PathVariable String articleId,
                                                 @RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        String action = body.get("action");
        Map<String, Object> result = articleService.review(articleId, action, body.get("reason"));
        String auditAction = "approve".equals(action) ? "article.publish" : "article.review";
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reviewAction", action);
        detail.put("status", result.get("status"));
        if (body.get("reason") != null) {
            detail.put("reason", body.get("reason"));
        }
        logArticleAudit(request, articleId, auditAction, detail);
        return ApiResponse.ok(result);
    }

    @GetMapping("/pending-review")
    public ApiResponse<Map<String, Object>> pendingReview(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(articleService.pendingReview(page, size));
    }

    @GetMapping("/ai-draft/config")
    public ApiResponse<Map<String, Object>> aiDraftConfig() {
        return ApiResponse.ok(articleService.aiDraftConfig());
    }

    private void logArticleAudit(HttpServletRequest request,
                               String articleId,
                               String action,
                               Map<String, Object> detail) {
        String adminId = currentAdminId(request);
        if (adminId == null) {
            return;
        }
        auditServiceClient.log(
                adminId,
                action,
                articleId,
                detail,
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                1
        );
    }

    private Map<String, Object> detailWithTitle(Map<String, Object> body, Map<String, Object> result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Object title = body.get("title");
        if (title != null) {
            detail.put("title", title);
        }
        if (result.get("status") != null) {
            detail.put("status", result.get("status"));
        }
        return detail;
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
}
