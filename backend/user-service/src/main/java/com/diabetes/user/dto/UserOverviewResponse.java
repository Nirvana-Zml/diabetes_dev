package com.diabetes.user.dto;

public record UserOverviewResponse(
        String user_id,
        String username,
        String nickname,
        String avatar_url,
        Integer points,
        String phone
) {
}
