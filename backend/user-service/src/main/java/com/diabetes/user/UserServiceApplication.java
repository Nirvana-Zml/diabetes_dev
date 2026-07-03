package com.diabetes.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {
        "com.diabetes.user",
        "com.diabetes.common.client",
        "com.diabetes.common.dify",
        "com.diabetes.common.config"
})
@MapperScan("com.diabetes.user.mapper")
@Import({
        com.diabetes.common.config.CommonAutoConfiguration.class,
        com.diabetes.common.config.MinioAutoConfiguration.class
})
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
