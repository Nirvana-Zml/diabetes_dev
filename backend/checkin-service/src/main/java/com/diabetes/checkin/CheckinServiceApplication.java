package com.diabetes.checkin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.diabetes")
@MapperScan("com.diabetes.checkin.mapper")
public class CheckinServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckinServiceApplication.class, args);
    }
}
