package com.diabetes.home.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OptionalJwtInterceptor optionalJwtInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(OptionalJwtInterceptor optionalJwtInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor) {
        this.optionalJwtInterceptor = optionalJwtInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(optionalJwtInterceptor)
                .addPathPatterns("/api/v1/chat/**");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/videos", "/api/v1/admin/videos/**");
    }
}
