package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.dto.*;
import com.diabetes.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void login() {
        TokenResponse token = new TokenResponse("a", "r", "u_1", "alice", "user");
        when(authService.login(any())).thenReturn(token);

        ApiResponse<TokenResponse> response = authController.login(new LoginRequest("alice", "password"));

        assertEquals(200, response.code());
        assertEquals(token, response.data());
    }

    @Test
    void register() {
        ApiResponse<Void> response = authController.register(
                new RegisterRequest("alice", "13800138000", "password123"));

        verify(authService).register(any());
        assertEquals(200, response.code());
        assertEquals("注册成功", response.message());
    }

    @Test
    void sendCode() {
        Map<String, String> body = Map.of(
                "account", "13800138000",
                "type", "phone",
                "purpose", "bind");
        ApiResponse<Void> response = authController.sendCode(body);

        verify(authService).sendVerifyCode("13800138000", "phone", "bind");
        assertEquals("验证码已发送", response.message());
    }

    @Test
    void resetPassword() {
        Map<String, String> body = Map.of(
                "account", "alice",
                "code", "123456",
                "new_password", "newpass123");
        ApiResponse<Void> response = authController.resetPassword(body);

        verify(authService).resetPassword("alice", "123456", "newpass123");
        assertEquals("密码重置成功", response.message());
    }
}
