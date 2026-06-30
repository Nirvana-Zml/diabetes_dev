package com.diabetes.plan.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
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

        ArgumentCaptor<String[]> includeCaptor = ArgumentCaptor.forClass(String[].class);
        verify(registration).addPathPatterns(includeCaptor.capture());
        assertArrayEquals(new String[]{"/api/v1/plan/**"}, includeCaptor.getValue());

        ArgumentCaptor<String[]> excludeCaptor = ArgumentCaptor.forClass(String[].class);
        verify(registration).excludePathPatterns(excludeCaptor.capture());
        assertArrayEquals(new String[]{"/api/v1/internal/**"}, excludeCaptor.getValue());
    }
}
