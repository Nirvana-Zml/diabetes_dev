package com.diabetes.user.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private JwtAuthInterceptor jwtAuthInterceptor;
    @Mock
    private DifyInternalInterceptor difyInternalInterceptor;
    @Mock
    private InterceptorRegistry registry;
    @Mock
    private InterceptorRegistration jwtRegistration;
    @Mock
    private InterceptorRegistration difyRegistration;

    @Test
    void addInterceptors_registersBoth() {
        when(registry.addInterceptor(jwtAuthInterceptor)).thenReturn(jwtRegistration);
        when(registry.addInterceptor(difyInternalInterceptor)).thenReturn(difyRegistration);
        when(jwtRegistration.addPathPatterns(any(String[].class))).thenReturn(jwtRegistration);
        when(difyRegistration.addPathPatterns(any(String[].class))).thenReturn(difyRegistration);

        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor, difyInternalInterceptor);
        config.addInterceptors(registry);

        verify(registry).addInterceptor(jwtAuthInterceptor);
        verify(registry).addInterceptor(difyInternalInterceptor);

        ArgumentCaptor<String[]> jwtPaths = ArgumentCaptor.forClass(String[].class);
        verify(jwtRegistration).addPathPatterns(jwtPaths.capture());
        assertArrayEquals(new String[]{"/api/v1/user/**"}, jwtPaths.getValue());

        ArgumentCaptor<String[]> difyPaths = ArgumentCaptor.forClass(String[].class);
        verify(difyRegistration).addPathPatterns(difyPaths.capture());
        assertArrayEquals(new String[]{"/api/v1/internal/**"}, difyPaths.getValue());
    }

    @Test
    void passwordEncoder_returnsBCrypt() {
        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor, difyInternalInterceptor);
        PasswordEncoder encoder = config.passwordEncoder();
        assertNotNull(encoder);
        assertTrue(encoder.matches("secret", encoder.encode("secret")));
    }
}
