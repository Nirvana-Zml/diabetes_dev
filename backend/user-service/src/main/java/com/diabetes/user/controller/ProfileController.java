package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.config.JwtAuthInterceptor;
import com.diabetes.user.dto.*;
import com.diabetes.user.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心管理（设计说明书 3.6）
 * 健康档案/就诊记录/方案由 health、consultation、plan 微服务提供，前端分别调用。
 */
@RestController
@RequestMapping("/api/v1/user")
public class ProfileController {

    private final UserProfileService userProfileService;

    public ProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
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
        userProfileService.changePassword(currentUserId(request), body);
        return ApiResponse.ok("密码修改成功", null);
    }

    /** 隐私与通知设置 */
    @PutMapping("/privacy")
    public ApiResponse<UserProfileResponse> updatePrivacy(HttpServletRequest request,
                                                          @Valid @RequestBody PrivacySettingsRequest body) {
        return ApiResponse.ok(userProfileService.updatePrivacy(currentUserId(request), body));
    }

    private String currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        if (userId == null || userId.toString().isBlank()) {
            throw new com.diabetes.common.exception.BusinessException(401, "未登录或 Token 无效");
        }
        return userId.toString();
    }
}
