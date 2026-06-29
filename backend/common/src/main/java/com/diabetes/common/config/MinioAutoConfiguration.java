package com.diabetes.common.config;

import com.diabetes.common.storage.MinioProperties;
import com.diabetes.common.storage.MinioStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "io.minio.MinioClient")
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {

    @Bean
    public MinioStorageService minioStorageService(MinioProperties properties) {
        return new MinioStorageService(properties);
    }
}
