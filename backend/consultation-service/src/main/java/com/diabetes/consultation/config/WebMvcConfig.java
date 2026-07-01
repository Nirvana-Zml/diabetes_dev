package com.diabetes.consultation.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/ai-doctors/**", "/api/v1/consultations/**",
                        "/api/v2/ai-doctors/**", "/api/v2/consultations/**")
                .excludePathPatterns(
                        "/api/v1/internal/**",
                        "/api/v2/internal/**",
                        "/api/v1/ai-doctors",
                        "/api/v1/ai-doctors/departments",
                        "/api/v2/ai-doctors",
                        "/api/v2/ai-doctors/departments");
    }
}
