package com.diabetes.user.dto;

import java.util.List;

public record UserMessageListResponse(
        int total,
        int unreadCount,
        List<UserMessageResponse> list
) {
}
