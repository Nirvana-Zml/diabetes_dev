package com.diabetes.consultation.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebMvcConfig 配置测试")
class WebMvcConfigTest {

    @Mock
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Mock
    private InterceptorRegistry interceptorRegistry;

    @Mock
    private InterceptorRegistration interceptorRegistration;

    @Captor
    private ArgumentCaptor<String> pathPatternCaptor;

    @Test
    @DisplayName("构造方法 - 成功创建配置实例")
    void constructor() {
        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor);
        assertNotNull(config);
    }

    @Test
    @DisplayName("addInterceptors - 注册拦截器并配置路径")
    void addInterceptors() {
        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor);

        when(interceptorRegistry.addInterceptor(jwtAuthInterceptor))
                .thenReturn(interceptorRegistration);
        when(interceptorRegistration.addPathPatterns(any(String[].class)))
                .thenReturn(interceptorRegistration);
        when(interceptorRegistration.excludePathPatterns(any(String[].class)))
                .thenReturn(interceptorRegistration);

        config.addInterceptors(interceptorRegistry);

        verify(interceptorRegistry).addInterceptor(jwtAuthInterceptor);

        verify(interceptorRegistration).addPathPatterns(
                "/api/v1/ai-doctors/**",
                "/api/v1/consultations/**",
                "/api/v2/ai-doctors/**",
                "/api/v2/consultations/**");

        verify(interceptorRegistration).excludePathPatterns(
                "/api/v1/internal/**",
                "/api/v2/internal/**",
                "/api/v1/ai-doctors",
                "/api/v1/ai-doctors/departments",
                "/api/v2/ai-doctors",
                "/api/v2/ai-doctors/departments");
    }

    @Test
    @DisplayName("addInterceptors - 验证拦截路径数量")
    void addInterceptors_pathCount() {
        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor);

        when(interceptorRegistry.addInterceptor(jwtAuthInterceptor))
                .thenReturn(interceptorRegistration);
        when(interceptorRegistration.addPathPatterns(any(String[].class)))
                .thenReturn(interceptorRegistration);

        config.addInterceptors(interceptorRegistry);

        ArgumentCaptor<String[]> addPathsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(interceptorRegistration).addPathPatterns(addPathsCaptor.capture());

        assertEquals(4, addPathsCaptor.getValue().length);
    }

    @Test
    @DisplayName("addInterceptors - 验证排除路径数量")
    void addInterceptors_excludePathCount() {
        WebMvcConfig config = new WebMvcConfig(jwtAuthInterceptor);

        when(interceptorRegistry.addInterceptor(jwtAuthInterceptor))
                .thenReturn(interceptorRegistration);
        when(interceptorRegistration.addPathPatterns(any(String[].class)))
                .thenReturn(interceptorRegistration);
        when(interceptorRegistration.excludePathPatterns(any(String[].class)))
                .thenReturn(interceptorRegistration);

        config.addInterceptors(interceptorRegistry);

        ArgumentCaptor<String[]> excludePathsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(interceptorRegistration).excludePathPatterns(excludePathsCaptor.capture());

        assertEquals(6, excludePathsCaptor.getValue().length);
    }
}