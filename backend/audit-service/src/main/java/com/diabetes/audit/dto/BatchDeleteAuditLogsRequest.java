package com.diabetes.audit.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchDeleteAuditLogsRequest(
        @NotEmpty List<String> logIds
) {
}
