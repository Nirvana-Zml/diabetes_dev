package com.diabetes.home.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OptionalJwtInterceptor optionalJwtInterceptor;

    public WebMvcConfig(OptionalJwtInterceptor optionalJwtInterceptor) {
        this.optionalJwtInterceptor = optionalJwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(optionalJwtInterceptor)
                .addPathPatterns("/api/v1/chat/**");
    }
}
