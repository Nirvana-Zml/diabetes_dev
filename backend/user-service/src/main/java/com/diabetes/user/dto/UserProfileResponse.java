package com.diabetes.user.dto;

public record UserProfileResponse(
        String user_id,
        String username,
        String phone,
        String email,
        String avatar_url,
        String nickname,
        Integer gender,
        String birth_date,
        Integer points,
        Object privacy_settings,
        String created_at
) {
}
