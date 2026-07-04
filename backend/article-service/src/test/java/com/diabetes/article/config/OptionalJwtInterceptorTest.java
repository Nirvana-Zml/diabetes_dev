package com.diabetes.article.config;

import com.diabetes.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalJwtInterceptorTest {

    private OptionalJwtInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private String secret = "a1b2c3d4e5f6g7h8a1b2c3d4e5f6g7h8a1b2c3d4e5f6g7h8";

    @BeforeEach
    void setUp() {
        interceptor = new OptionalJwtInterceptor(secret);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void testPreHandleNoAuthHeader() {
        when(request.getHeader("Authorization")).thenReturn(null);
        
        assertTrue(interceptor.preHandle(request, response, null));
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void testPreHandleInvalidAuthFormat() {
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");
        
        assertTrue(interceptor.preHandle(request, response, null));
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void testPreHandleValidToken() {
        String token = JwtUtil.generateToken(secret, "user_01", "user", 3600L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        
        assertTrue(interceptor.preHandle(request, response, null));
        verify(request, times(1)).setAttribute(OptionalJwtInterceptor.ATTR_USER_ID, "user_01");
    }

    @Test
    void testPreHandleInvalidToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");
        
        assertTrue(interceptor.preHandle(request, response, null));
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void testPreHandleExpiredToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired.token");
        
        assertTrue(interceptor.preHandle(request, response, null));
        verify(request, never()).setAttribute(anyString(), any());
    }
}
