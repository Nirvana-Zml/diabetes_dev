package com.diabetes.user.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record UserMessageResponse(
        String messageId,
        String messageType,
        String status,
        String title,
        String summary,
        String bizId,
        String linkPath,
        Map<String, Object> linkQuery,
        Map<String, Object> extra,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
