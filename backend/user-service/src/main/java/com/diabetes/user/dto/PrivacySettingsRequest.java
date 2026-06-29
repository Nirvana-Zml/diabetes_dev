package com.diabetes.user.dto;

import java.util.Map;

public record PrivacySettingsRequest(
        Map<String, Object> settings
) {
}
