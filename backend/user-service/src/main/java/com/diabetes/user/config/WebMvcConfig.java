package com.diabetes.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final DifyInternalInterceptor difyInternalInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor,
                        DifyInternalInterceptor difyInternalInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.difyInternalInterceptor = difyInternalInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/v1/user/**");

        registry.addInterceptor(difyInternalInterceptor)
                .addPathPatterns("/api/v1/internal/**");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/stats", "/api/v1/admin/stats/**");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
