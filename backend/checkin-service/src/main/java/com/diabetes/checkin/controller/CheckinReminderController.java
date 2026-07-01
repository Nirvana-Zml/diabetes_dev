package com.diabetes.checkin.controller;

import com.diabetes.checkin.dto.ReminderRulesSaveRequest;
import com.diabetes.checkin.dto.ReminderSnoozeRequest;
import com.diabetes.checkin.service.CheckinReminderService;
import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 打卡提醒 API，见 docs/打卡提醒模块产品设计说明书.md §6
 */
@RestController
@RequestMapping("/api/v1/checkin/reminders")
public class CheckinReminderController {

    private final CheckinReminderService reminderService;

    public CheckinReminderController(CheckinReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> rules(@CurrentUserId String userId) {
        return ApiResponse.ok(reminderService.getRules(userId));
    }

    @PutMapping("/rules")
    public ApiResponse<List<Map<String, Object>>> saveRules(@CurrentUserId String userId,
                                                            @Valid @RequestBody ReminderRulesSaveRequest request) {
        return ApiResponse.ok(reminderService.saveRules(userId, request));
    }

    @GetMapping("/defaults")
    public ApiResponse<Map<String, Object>> defaults() {
        return ApiResponse.ok(reminderService.getDefaults());
    }

    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> pending(@CurrentUserId String userId) {
        return ApiResponse.ok(reminderService.getPending(userId));
    }

    @PostMapping("/logs/{logId}/ack")
    public ApiResponse<Void> ack(@CurrentUserId String userId, @PathVariable String logId) {
        reminderService.ack(userId, logId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logs/{logId}/snooze")
    public ApiResponse<Void> snooze(@CurrentUserId String userId,
                                    @PathVariable String logId,
                                    @Valid @RequestBody(required = false) ReminderSnoozeRequest request) {
        int minutes = request != null ? request.getMinutes() : 15;
        reminderService.snooze(userId, logId, minutes);
        return ApiResponse.ok(null);
    }

    @PostMapping("/logs/{logId}/click")
    public ApiResponse<Void> click(@CurrentUserId String userId, @PathVariable String logId) {
        reminderService.markClicked(userId, logId);
        return ApiResponse.ok(null);
    }
}
