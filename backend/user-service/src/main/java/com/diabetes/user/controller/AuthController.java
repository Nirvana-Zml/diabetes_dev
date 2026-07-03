package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.user.dto.*;
import com.diabetes.user.entity.Admin;
import com.diabetes.user.mapper.AdminMapper;
import com.diabetes.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final AuditServiceClient auditServiceClient;
    private final AdminMapper adminMapper;

    public AuthController(AuthService authService,
                          AuditServiceClient auditServiceClient,
                          AdminMapper adminMapper) {
        this.authService = authService;
        this.auditServiceClient = auditServiceClient;
        this.adminMapper = adminMapper;
    }

    @PostMapping("/auth/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        String username = request.username().trim();
        try {
            TokenResponse token = authService.login(request);
            String action = "admin".equals(token.role()) ? "admin.login" : "user.login";
            auditServiceClient.log(
                    token.user_id(),
                    action,
                    token.username(),
                    Map.of("role", token.role()),
                    resolveClientIp(httpRequest),
                    httpRequest.getHeader("User-Agent"),
                    1
            );
            return ApiResponse.ok(token);
        } catch (BusinessException e) {
            if (e.getCode() == 401) {
                auditServiceClient.log(
                        username,
                        resolveFailedLoginAction(username),
                        username,
                        Map.of("reason", e.getMessage()),
                        resolveClientIp(httpRequest),
                        httpRequest.getHeader("User-Agent"),
                        0
                );
            }
            throw e;
        }
    }

    @PostMapping("/auth/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletRequest httpRequest) {
        String userId = authService.register(request);
        auditServiceClient.log(
                userId,
                "user.register",
                request.username().trim(),
                Map.of("phone", request.phone()),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                1
        );
        return ApiResponse.ok("注册成功", null);
    }

    @PostMapping("/auth/send-code")
    public ApiResponse<Void> sendCode(@RequestBody Map<String, String> body) {
        authService.sendVerifyCode(
                body.get("account"),
                body.getOrDefault("type", "phone"),
                body.getOrDefault("purpose", "bind"));
        return ApiResponse.ok("验证码已发送", null);
    }

    @PostMapping("/auth/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, String> body,
                                           HttpServletRequest httpRequest) {
        String userId = authService.resetPassword(
                body.get("account"), body.get("code"), body.get("new_password"));
        auditServiceClient.log(
                userId,
                "user.password.reset",
                body.get("account"),
                Map.of(),
                resolveClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"),
                1
        );
        return ApiResponse.ok("密码重置成功", null);
    }

    private String resolveFailedLoginAction(String username) {
        Admin admin = adminMapper.findByUsername(username);
        return admin != null ? "admin.login" : "user.login";
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
