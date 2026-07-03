package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.dto.InterventionEvaluateRequest;
import com.diabetes.user.service.HealthInterventionOrchestrator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/interventions")
public class InternalInterventionController {

    private final HealthInterventionOrchestrator interventionOrchestrator;

    public InternalInterventionController(HealthInterventionOrchestrator interventionOrchestrator) {
        this.interventionOrchestrator = interventionOrchestrator;
    }

    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@Valid @RequestBody InterventionEvaluateRequest request) {
        interventionOrchestrator.evaluateAsync(
                request.userId(),
                request.trigger(),
                request.context());
        return ApiResponse.ok(Map.of("accepted", true));
    }
}
