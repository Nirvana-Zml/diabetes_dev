package com.diabetes.health.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Mock
    private InterceptorRegistry registry;

    @Mock
    private InterceptorRegistration registration;

    @Test
    void addInterceptors() {
        when(registry.addInterceptor(jwtAuthInterceptor)).thenReturn(registration);
        when(registration.addPathPatterns(any(String[].class))).thenReturn(registration);
        when(registration.excludePathPatterns(any(String[].class))).thenReturn(registration);

        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor);
        config.addInterceptors(registry);

        verify(registry).addInterceptor(jwtAuthInterceptor);
        verify(registration).addPathPatterns("/api/v1/risk/**", "/api/v1/health-records/**");
        verify(registration).excludePathPatterns("/api/v1/internal/**");
    }
}