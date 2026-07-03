package com.diabetes.audit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.diabetes.audit")
@MapperScan("com.diabetes.audit.mapper")
@Import(com.diabetes.common.config.CommonAutoConfiguration.class)
@org.springframework.boot.autoconfigure.EnableAutoConfiguration(excludeName = {
        "com.diabetes.common.config.MinioAutoConfiguration"
})
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
