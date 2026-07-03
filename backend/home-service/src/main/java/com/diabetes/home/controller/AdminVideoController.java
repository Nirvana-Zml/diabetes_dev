package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import com.diabetes.home.config.AdminAuthInterceptor;
import com.diabetes.home.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/videos")
public class AdminVideoController {

    private final VideoService videoService;
    private final AuditServiceClient auditServiceClient;

    public AdminVideoController(VideoService videoService,
                                AuditServiceClient auditServiceClient) {
        this.videoService = videoService;
        this.auditServiceClient = auditServiceClient;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(videoService.adminList(keyword, page, size));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String videoId) {
        return ApiResponse.ok(videoService.adminDetail(videoId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        Map<String, Object> result = videoService.create(body);
        logVideoAudit(request, String.valueOf(result.get("videoId")), "video.create", detailWithTitle(body, result));
        return ApiResponse.ok(result);
    }

    @PutMapping("/{videoId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String videoId,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        Map<String, Object> result = videoService.update(videoId, body);
        logVideoAudit(request, videoId, "video.update", detailWithTitle(body, result));
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{videoId}")
    public ApiResponse<String> delete(@PathVariable String videoId, HttpServletRequest request) {
        videoService.delete(videoId);
        logVideoAudit(request, videoId, "video.delete", Map.of());
        return ApiResponse.ok("删除成功", "删除成功");
    }

    @PostMapping(value = "/{videoId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadCover(@PathVariable String videoId,
                                                        @RequestPart("file") MultipartFile file,
                                                        HttpServletRequest request) {
        Map<String, Object> result = videoService.uploadCover(videoId, file);
        logVideoAudit(request, videoId, "video.cover.upload", Map.of("fileName", file.getOriginalFilename()));
        return ApiResponse.ok(result);
    }

    @PostMapping(value = "/{videoId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadFile(@PathVariable String videoId,
                                                       @RequestPart("file") MultipartFile file,
                                                       HttpServletRequest request) {
        Map<String, Object> result = videoService.uploadVideoFile(videoId, file);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fileName", file.getOriginalFilename());
        if (result.get("duration") != null) {
            detail.put("duration", result.get("duration"));
        }
        logVideoAudit(request, videoId, "video.file.upload", detail);
        return ApiResponse.ok(result);
    }

    private Map<String, Object> detailWithTitle(Map<String, Object> body, Map<String, Object> result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Object title = body.get("title");
        if (title != null) {
            detail.put("title", title);
        }
        if (result.get("duration") != null) {
            detail.put("duration", result.get("duration"));
        }
        return detail;
    }

    private void logVideoAudit(HttpServletRequest request,
                             String videoId,
                             String action,
                             Map<String, Object> detail) {
        String adminId = currentAdminId(request);
        if (adminId == null) {
            return;
        }
        auditServiceClient.log(
                adminId,
                action,
                videoId,
                detail,
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                1
        );
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
