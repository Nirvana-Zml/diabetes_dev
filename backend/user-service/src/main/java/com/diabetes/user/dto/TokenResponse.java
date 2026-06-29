package com.diabetes.user.dto;

public record TokenResponse(
        String access_token,
        String refresh_token,
        String user_id,
        String username,
        String role
) {
}
