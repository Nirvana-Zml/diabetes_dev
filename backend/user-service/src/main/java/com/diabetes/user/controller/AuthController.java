package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.config.JwtAuthInterceptor;
import com.diabetes.user.dto.*;
import com.diabetes.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/auth/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
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
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("account"), body.get("code"), body.get("new_password"));
        return ApiResponse.ok("密码重置成功", null);
    }
}
