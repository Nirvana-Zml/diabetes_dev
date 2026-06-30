package com.diabetes.user.dto;

public record ExportTaskResponse(
        String task_id,
        String status,
        String message,
        String download_url,
        String file_name,
        String expires_at
) {}
