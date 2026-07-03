package com.diabetes.user.dto;

import java.util.Map;

public record InterventionEvaluateRequest(
        String userId,
        String trigger,
        Map<String, Object> context
) {
}
