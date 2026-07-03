package com.diabetes.checkin.controller;

import com.diabetes.checkin.service.CheckinReminderService;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/reminders")
public class InternalReminderController {

    private final CheckinReminderService reminderService;
    private final String difyInternalKey;

    public InternalReminderController(CheckinReminderService reminderService,
                                      @Value("${dify-internal.key:}") String difyInternalKey) {
        this.reminderService = reminderService;
        this.difyInternalKey = difyInternalKey;
    }

    @PostMapping("/system-adjust")
    public ApiResponse<Void> systemAdjust(@RequestBody Map<String, Object> body,
                                          @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        String userId = stringValue(body.get("user_id"), body.get("userId"));
        String interventionId = stringValue(body.get("intervention_id"), body.get("interventionId"));
        if (userId.isBlank()) {
            throw new BusinessException(400, "user_id 不能为空");
        }
        LocalDateTime expiresAt = null;
        Object expiresRaw = body.get("expires_at") != null ? body.get("expires_at") : body.get("expiresAt");
        if (expiresRaw != null && !String.valueOf(expiresRaw).isBlank()) {
            expiresAt = LocalDateTime.parse(String.valueOf(expiresRaw).replace(" ", "T"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adjustments = (List<Map<String, Object>>) body.get("adjustments");
        reminderService.applySystemAdjust(userId, interventionId, adjustments, expiresAt);
        return ApiResponse.ok(null);
    }

    private String stringValue(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private void validateDifyKey(String key) {
        if (difyInternalKey != null && !difyInternalKey.isBlank()
                && (key == null || !difyInternalKey.equals(key))) {
            throw new BusinessException(401, "Dify 内部密钥无效");
        }
    }
}
