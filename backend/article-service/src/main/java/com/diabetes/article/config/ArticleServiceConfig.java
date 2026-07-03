package com.diabetes.article.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DashScopeTtsProperties.class)
public class ArticleServiceConfig {
}
