package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.user.service.HealthInterventionOrchestrator;
import com.diabetes.user.service.HealthTrendAnalysisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class HealthTrendController {

    private final HealthTrendAnalysisService trendAnalysisService;
    private final HealthInterventionOrchestrator interventionOrchestrator;
    private final String difyBaseUrl;

    public HealthTrendController(HealthTrendAnalysisService trendAnalysisService,
                                 HealthInterventionOrchestrator interventionOrchestrator,
                                 @Value("${dify.base-url:http://localhost}") String difyBaseUrl) {
        this.trendAnalysisService = trendAnalysisService;
        this.interventionOrchestrator = interventionOrchestrator;
        this.difyBaseUrl = difyBaseUrl;
    }

    @GetMapping("/health-trend")
    public ApiResponse<Map<String, Object>> healthTrend(@CurrentUserId String userId,
                                                        @RequestParam(defaultValue = "30") int limit,
                                                        @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> data = trendAnalysisService.analyze(userId, limit, force);
        if (force) {
            interventionOrchestrator.evaluateAsync(userId, "manual_refresh", Map.of());
        }
        return ApiResponse.ok(data);
    }

    @GetMapping("/health-alert")
    public ApiResponse<Map<String, Object>> healthAlert(@CurrentUserId String userId) {
        return ApiResponse.ok(interventionOrchestrator.getActiveAlert(userId));
    }

    @PostMapping("/interventions/{planId}/ack")
    public ApiResponse<Void> acknowledge(@CurrentUserId String userId, @PathVariable String planId) {
        interventionOrchestrator.acknowledge(userId, planId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/health-trend/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(trendAnalysisService.getWorkflowSpec(difyBaseUrl));
    }
}
