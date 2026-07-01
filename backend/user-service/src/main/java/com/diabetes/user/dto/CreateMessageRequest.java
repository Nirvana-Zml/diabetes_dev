package com.diabetes.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateMessageRequest(
        @NotBlank String userId,
        @NotBlank String messageType,
        @NotBlank String status,
        @NotBlank String title,
        String summary,
        String bizId,
        @NotBlank String linkPath,
        Map<String, Object> linkQuery,
        Map<String, Object> extra
) {
}
