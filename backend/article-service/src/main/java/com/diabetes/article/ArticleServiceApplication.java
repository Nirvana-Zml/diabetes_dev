package com.diabetes.article;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.config.RecommendProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.diabetes")
@MapperScan("com.diabetes.article.mapper")
@EnableConfigurationProperties({RecommendProperties.class, MilvusProperties.class})
public class ArticleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArticleServiceApplication.class, args);
    }
}
