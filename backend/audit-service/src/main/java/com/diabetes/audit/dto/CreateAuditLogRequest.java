package com.diabetes.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateAuditLogRequest(
        @NotBlank @Size(max = 32) String userId,
        @NotBlank @Size(max = 100) String action,
        @NotBlank @Size(max = 200) String resource,
        Map<String, Object> detail,
        @Size(max = 50) String ipAddress,
        @Size(max = 500) String userAgent,
        @NotNull Integer result
) {
}
