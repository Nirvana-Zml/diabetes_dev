package com.diabetes.health.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.health.dto.UpdateHealthRecordRequest;
import com.diabetes.health.service.HealthRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health-records")
public class HealthRecordController {

    private final HealthRecordService healthRecordService;

    public HealthRecordController(HealthRecordService healthRecordService) {
        this.healthRecordService = healthRecordService;
    }

    @GetMapping("/latest")
    public ApiResponse<Map<String, Object>> latest(@CurrentUserId String userId) {
        return ApiResponse.ok(healthRecordService.getLatest(userId));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@CurrentUserId String userId,
                                                   @Valid @RequestBody UpdateHealthRecordRequest request) {
        return ApiResponse.ok(healthRecordService.save(userId, request));
    }
}
