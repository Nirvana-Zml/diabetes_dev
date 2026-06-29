package com.diabetes.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入用户名")
        @Size(min = 3, max = 50, message = "用户名长度 3-50 个字符")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 32, message = "密码长度 6-32 个字符")
        String password
) {
}
