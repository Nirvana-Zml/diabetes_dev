package com.diabetes.article.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.Mockito.*;

class WebMvcConfigTest {

    @Test
    void testAddInterceptors() {
        JwtAuthInterceptor jwtAuthInterceptor = mock(JwtAuthInterceptor.class);
        OptionalJwtInterceptor optionalJwtInterceptor = mock(OptionalJwtInterceptor.class);
        AdminAuthInterceptor adminAuthInterceptor = mock(AdminAuthInterceptor.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);

        when(registry.addInterceptor(any())).thenReturn(registration);
        when(registration.addPathPatterns(any(String[].class))).thenReturn(registration);
        when(registration.excludePathPatterns(any(String[].class))).thenReturn(registration);

        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor, optionalJwtInterceptor, adminAuthInterceptor);
        config.addInterceptors(registry);

        verify(registry, times(1)).addInterceptor(jwtAuthInterceptor);
        verify(registry, times(1)).addInterceptor(optionalJwtInterceptor);
        verify(registry, times(1)).addInterceptor(adminAuthInterceptor);
    }
}