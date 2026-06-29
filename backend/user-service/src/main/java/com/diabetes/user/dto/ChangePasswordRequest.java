package com.diabetes.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码")
        String old_password,

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "密码长度 6-32 个字符")
        String new_password
) {
}
