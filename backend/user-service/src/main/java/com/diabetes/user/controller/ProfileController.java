package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import com.diabetes.user.config.JwtAuthInterceptor;
import com.diabetes.user.dto.*;
import com.diabetes.user.service.DataExportService;
import com.diabetes.user.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个人中心管理（设计说明书 3.6）
 * 健康档案/就诊记录/方案由 health、consultation、plan 微服务提供，前端分别调用。
 */
@RestController
@RequestMapping("/api/v1/user")
public class ProfileController {

    private final UserProfileService userProfileService;
    private final DataExportService dataExportService;
    private final AuditServiceClient auditServiceClient;

    public ProfileController(UserProfileService userProfileService,
                             DataExportService dataExportService,
                             AuditServiceClient auditServiceClient) {
        this.userProfileService = userProfileService;
        this.dataExportService = dataExportService;
        this.auditServiceClient = auditServiceClient;
    }

    /** 个人中心概览：头像、昵称、积分等 */
    @GetMapping("/overview")
    public ApiResponse<UserOverviewResponse> overview(HttpServletRequest request) {
        String userId = currentUserId(request);
        return ApiResponse.ok(userProfileService.getOverview(userId));
    }

    /** 获取个人信息 */
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(HttpServletRequest request) {
        return ApiResponse.ok(userProfileService.getProfile(currentUserId(request)));
    }

    /** 上传头像（MinIO profile bucket，对象名 {userId}.jpg） */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarUploadResponse> uploadAvatar(HttpServletRequest request,
                                                            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(userProfileService.uploadAvatar(currentUserId(request), file));
    }

    /** 更新个人信息 */
    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(HttpServletRequest request,
                                                          @Valid @RequestBody UpdateProfileRequest body) {
        return ApiResponse.ok(userProfileService.updateProfile(currentUserId(request), body));
    }

    /** 账户安全：修改密码 */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(HttpServletRequest request,
                                           @Valid @RequestBody ChangePasswordRequest body) {
        String userId = currentUserId(request);
        userProfileService.changePassword(userId, body);
        auditServiceClient.log(
                userId,
                "user.password.change",
                userId,
                Map.of(),
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                1
        );
        return ApiResponse.ok("密码修改成功", null);
    }

    /** 账户安全：绑定邮箱（需验证码） */
    @PutMapping("/account/email")
    public ApiResponse<UserProfileResponse> bindEmail(HttpServletRequest request,
                                                      @Valid @RequestBody BindEmailRequest body) {
        return ApiResponse.ok("邮箱绑定成功", userProfileService.bindEmail(currentUserId(request), body));
    }

    /** 账户安全：绑定手机号（需验证码） */
    @PutMapping("/account/phone")
    public ApiResponse<UserProfileResponse> bindPhone(HttpServletRequest request,
                                                      @Valid @RequestBody BindPhoneRequest body) {
        return ApiResponse.ok("手机号绑定成功", userProfileService.bindPhone(currentUserId(request), body));
    }

    /** 隐私与通知设置 */
    @PutMapping("/privacy")
    public ApiResponse<UserProfileResponse> updatePrivacy(HttpServletRequest request,
                                                          @Valid @RequestBody PrivacySettingsRequest body) {
        return ApiResponse.ok(userProfileService.updatePrivacy(currentUserId(request), body));
    }

    /** 提交数据导出任务 */
    @PostMapping("/export")
    public ApiResponse<ExportTaskResponse> exportData(HttpServletRequest request,
                                                      @Valid @RequestBody ExportDataRequest body) {
        String userId = currentUserId(request);
        ExportTaskResponse task = dataExportService.submitExport(userId, body);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("types", body.types());
        detail.put("format", body.format());
        detail.put("taskId", task.task_id());
        auditServiceClient.log(
                userId,
                "data.export",
                task.task_id(),
                detail,
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                1
        );
        return ApiResponse.ok("导出成功", task);
    }

    /** 查询导出任务状态 */
    @GetMapping("/export/{taskId}")
    public ApiResponse<ExportTaskResponse> exportTask(HttpServletRequest request,
                                                        @PathVariable String taskId) {
        return ApiResponse.ok(dataExportService.getTask(currentUserId(request), taskId));
    }

    private String currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        if (userId == null || userId.toString().isBlank()) {
            throw new com.diabetes.common.exception.BusinessException(401, "未登录或 Token 无效");
        }
        return userId.toString();
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
