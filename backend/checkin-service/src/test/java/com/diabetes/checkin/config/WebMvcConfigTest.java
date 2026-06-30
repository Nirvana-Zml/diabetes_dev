package com.diabetes.checkin.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebMvcConfigTest {

    @Test
    void addInterceptors_registersJwtAndExcludesInternalApi() {
        JwtAuthInterceptor interceptor = mock(JwtAuthInterceptor.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(interceptor)).thenReturn(registration);
        when(registration.addPathPatterns(any(String[].class))).thenReturn(registration);
        when(registration.excludePathPatterns(any(String[].class))).thenReturn(registration);

        new WebMvcConfig(interceptor).addInterceptors(registry);

        ArgumentCaptor<String[]> include = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<String[]> exclude = ArgumentCaptor.forClass(String[].class);
        verify(registration).addPathPatterns(include.capture());
        verify(registration).excludePathPatterns(exclude.capture());
        assertArrayEquals(new String[]{"/api/v1/checkin/**", "/api/v1/checkin-management/**"}, include.getValue());
        assertArrayEquals(new String[]{"/api/v1/internal/**"}, exclude.getValue());
    }
}
