package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.user.dto.*;
import com.diabetes.user.entity.Admin;
import com.diabetes.user.mapper.AdminMapper;
import com.diabetes.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuditServiceClient auditServiceClient;

    @Mock
    private AdminMapper adminMapper;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AuthController authController;

    @Test
    void login() {
        TokenResponse token = new TokenResponse("a", "r", "u_1", "alice", "user");
        when(authService.login(any())).thenReturn(token);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        ApiResponse<TokenResponse> response = authController.login(
                new LoginRequest("alice", "password"), httpServletRequest);

        assertEquals(200, response.code());
        assertEquals(token, response.data());
        verify(auditServiceClient).log(eq("u_1"), eq("user.login"), eq("alice"), any(), eq("127.0.0.1"), eq("JUnit"), eq(1));
    }

    @Test
    void loginAsAdminUsesAdminAction() {
        TokenResponse token = new TokenResponse("a", "r", "admin_1", "admin", "admin");
        when(authService.login(any())).thenReturn(token);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");

        authController.login(new LoginRequest("admin", "password"), httpServletRequest);

        verify(auditServiceClient).log(eq("admin_1"), eq("admin.login"), eq("admin"), any(), eq("203.0.113.1"), eq("JUnit"), eq(1));
    }

    @Test
    void loginFailureForAdminUsesAdminAction() {
        when(authService.login(any())).thenThrow(new BusinessException(401, "用户名或密码错误"));
        when(adminMapper.findByUsername("admin")).thenReturn(new Admin());
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThrows(BusinessException.class, () ->
                authController.login(new LoginRequest("admin", "bad"), httpServletRequest));

        verify(auditServiceClient).log(eq("admin"), eq("admin.login"), eq("admin"), any(), eq("127.0.0.1"), eq("JUnit"), eq(0));
    }

    @Test
    void loginFailureForUserUsesUserAction() {
        when(authService.login(any())).thenThrow(new BusinessException(401, "用户名或密码错误"));
        when(adminMapper.findByUsername("alice")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThrows(BusinessException.class, () ->
                authController.login(new LoginRequest("alice", "bad"), httpServletRequest));

        verify(auditServiceClient).log(eq("alice"), eq("user.login"), eq("alice"), any(), eq("127.0.0.1"), eq("JUnit"), eq(0));
    }

    @Test
    void loginFailureWithNon401DoesNotAudit() {
        when(authService.login(any())).thenThrow(new BusinessException(403, "账号已禁用"));

        assertThrows(BusinessException.class, () ->
                authController.login(new LoginRequest("alice", "bad"), httpServletRequest));

        verifyNoInteractions(auditServiceClient);
    }

    @Test
    void register() {
        when(authService.register(any())).thenReturn("u_new");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("  ");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");

        ApiResponse<Void> response = authController.register(
                new RegisterRequest("alice", "13800138000", "password123"), httpServletRequest);

        verify(authService).register(any());
        verify(auditServiceClient).log(eq("u_new"), eq("user.register"), eq("alice"), any(), eq("10.0.0.5"), eq("JUnit"), eq(1));
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
        when(authService.resetPassword("alice", "123456", "newpass123")).thenReturn("u_1");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("198.51.100.7");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("JUnit");

        Map<String, String> body = Map.of(
                "account", "alice",
                "code", "123456",
                "new_password", "newpass123");
        ApiResponse<Void> response = authController.resetPassword(body, httpServletRequest);

        verify(authService).resetPassword("alice", "123456", "newpass123");
        verify(auditServiceClient).log(eq("u_1"), eq("user.password.reset"), eq("alice"), any(), eq("198.51.100.7"), eq("JUnit"), eq(1));
        assertEquals("密码重置成功", response.message());
    }
}
