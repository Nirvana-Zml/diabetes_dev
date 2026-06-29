package com.diabetes.checkin.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.checkin.service.CheckinService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkin")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(@CurrentUserId String userId) {
        return ApiResponse.ok(checkinService.getTodayStatus(userId));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@CurrentUserId String userId,
                                                @RequestParam(defaultValue = "monthly") String range) {
        return ApiResponse.ok(checkinService.getStats(userId, range));
    }

    @GetMapping("/achievements")
    public ApiResponse<Map<String, Object>> achievements(@CurrentUserId String userId) {
        return ApiResponse.ok(Map.of("achievements", checkinService.getAchievements(userId)));
    }
}
