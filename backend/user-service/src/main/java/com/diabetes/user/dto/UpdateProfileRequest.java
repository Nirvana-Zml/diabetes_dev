package com.diabetes.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "昵称最多 100 个字符")
        String nickname,

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
        String phone,

        @Size(max = 100, message = "邮箱最多 100 个字符")
        String email,

        @Size(max = 500, message = "头像 URL 过长")
        String avatar_url,

        Integer gender,

        String birth_date
) {
}
