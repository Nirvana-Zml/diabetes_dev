package com.diabetes.user.config;

import com.diabetes.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DifyInternalInterceptorTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private DifyInternalInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DifyInternalInterceptor("secret-internal-key");
    }

    @Test
    void preHandle_missingKey() {
        when(request.getHeader("X-Dify-Key")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(403, ex.getCode());
    }

    @Test
    void preHandle_wrongKey() {
        when(request.getHeader("X-Dify-Key")).thenReturn("wrong");
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void preHandle_validKey() {
        when(request.getHeader("X-Dify-Key")).thenReturn("secret-internal-key");
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }
}
