package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.dto.UserOverviewResponse;
import com.diabetes.user.dto.UserProfileResponse;
import com.diabetes.user.service.UserProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 Dify 工作流 HTTP 工具调用的内部接口（Header: X-Dify-Key）
 */
@RestController
@RequestMapping("/api/v1/internal/user")
public class InternalUserController {

    private final UserProfileService userProfileService;

    public InternalUserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> profile(@PathVariable String userId) {
        return ApiResponse.ok(userProfileService.getProfile(userId));
    }

    @GetMapping("/{userId}/overview")
    public ApiResponse<UserOverviewResponse> overview(@PathVariable String userId) {
        return ApiResponse.ok(userProfileService.getOverview(userId));
    }
}
