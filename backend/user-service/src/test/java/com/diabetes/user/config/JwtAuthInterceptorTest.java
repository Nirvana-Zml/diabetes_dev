package com.diabetes.user.config;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthInterceptorTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-must-be-long-enough-32bytes";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtAuthInterceptor(JWT_SECRET);
    }

    @Test
    void preHandle_noAuthorization() {
        when(request.getHeader("Authorization")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(401, ex.getCode());
    }

    @Test
    void preHandle_invalidBearerPrefix() {
        when(request.getHeader("Authorization")).thenReturn("Token abc");
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void preHandle_validToken() {
        String token = JwtUtil.generateToken(JWT_SECRET, "u_1", "user", 3600);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute(JwtAuthInterceptor.ATTR_USER_ID, "u_1");
    }

    @Test
    void preHandle_invalidToken() {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(401, ex.getCode());
        assertEquals("Token 已过期或无效", ex.getMessage());
    }
}
