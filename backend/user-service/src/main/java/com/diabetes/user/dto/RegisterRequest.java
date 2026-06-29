package com.diabetes.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Size(min = 3, max = 50, message = "用户名长度 3-50 个字符")
        @Pattern(regexp = "^[\\w\\u4e00-\\u9fa5-]+$", message = "用户名格式不正确")
        String username,

        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
        String phone,

        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 32, message = "密码长度 6-32 个字符")
        String password
) {
}
