package com.diabetes.home.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({KnowledgeMilvusProperties.class, QaChatProperties.class})
public class HomeServiceConfig {
}
