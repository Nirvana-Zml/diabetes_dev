package com.diabetes.common.config;

import com.diabetes.common.auth.UserIdArgumentResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@Import(com.diabetes.common.exception.GlobalExceptionHandler.class)
public class CommonAutoConfiguration implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {

    @Bean
    public UserIdArgumentResolver userIdArgumentResolver() {
        return new UserIdArgumentResolver();
    }

    @Override
    public void addArgumentResolvers(java.util.List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userIdArgumentResolver());
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
