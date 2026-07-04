package com.diabetes.article.config;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuthInterceptorTest {

    private AdminAuthInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private String secret = "a1b2c3d4e5f6g7h8a1b2c3d4e5f6g7h8a1b2c3d4e5f6g7h8";

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(secret);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void testPreHandleNoAuthHeader() {
        when(request.getHeader("Authorization")).thenReturn(null);
        
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, null));
    }

    @Test
    void testPreHandleInvalidAuthFormat() {
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");
        
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, null));
    }

    @Test
    void testPreHandleValidAdminToken() {
        String token = JwtUtil.generateToken(secret, "admin_01", "admin", 3600L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        
        assertTrue(interceptor.preHandle(request, response, null));
        verify(request, times(1)).setAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID, "admin_01");
    }

    @Test
    void testPreHandleNonAdminRole() {
        String token = JwtUtil.generateToken(secret, "user_01", "user", 3600L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, null));
    }

    @Test
    void testPreHandleInvalidToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");
        
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, null));
    }

    @Test
    void testPreHandleTokenWithoutRoleClaim() {
        String token = JwtUtil.generateToken(secret, "user_01", "", 3600L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, null));
    }
}
