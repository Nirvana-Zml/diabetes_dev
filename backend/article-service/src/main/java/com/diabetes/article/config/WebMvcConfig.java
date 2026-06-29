package com.diabetes.article.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final OptionalJwtInterceptor optionalJwtInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor,
                        OptionalJwtInterceptor optionalJwtInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.optionalJwtInterceptor = optionalJwtInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/api/v1/articles/favorites",
                        "/api/v1/articles/*/favorite",
                        "/api/v1/articles/*/read-event");

        registry.addInterceptor(optionalJwtInterceptor)
                .addPathPatterns(
                        "/api/v1/articles/recommend",
                        "/api/v1/articles/*/related",
                        "/api/v1/articles/*")
                .excludePathPatterns(
                        "/api/v1/articles",
                        "/api/v1/articles/search",
                        "/api/v1/articles/favorites",
                        "/api/v1/articles/*/favorite",
                        "/api/v1/articles/*/read-event");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/articles/**");
    }
}
