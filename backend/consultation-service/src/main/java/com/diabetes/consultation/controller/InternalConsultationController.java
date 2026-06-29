package com.diabetes.consultation.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.consultation.service.ConsultationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/internal/consultation", "/api/v2/internal/consultation"})
public class InternalConsultationController {

    private final ConsultationService consultationService;

    public InternalConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping("/user/{userId}/active-session")
    public ApiResponse<Map<String, Object>> activeSession(@PathVariable String userId) {
        return ApiResponse.ok(consultationService.getActiveSession(userId));
    }
}
