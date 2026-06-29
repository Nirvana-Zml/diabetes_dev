package com.diabetes.consultation.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.consultation.service.ConsultationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * v1 接口与 v2 行为一致。
 */
@RestController
@RequestMapping("/api/v1")
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping("/ai-doctors")
    public ApiResponse<List<Map<String, Object>>> listAiDoctors(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(consultationService.listDoctors(department, keyword, status));
    }

    @PostMapping("/consultations")
    public ApiResponse<Map<String, Object>> createSession(@CurrentUserId String userId,
                                                          @RequestBody Map<String, Object> body) {
        String doctorId = firstString(body, "aiDoctorId", "ai_doctor_id", "doctorId", "doctor_id");
        return ApiResponse.ok(consultationService.createSession(userId, doctorId));
    }

    @GetMapping("/consultations")
    public ApiResponse<Map<String, Object>> listSessions(@CurrentUserId String userId,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(consultationService.listSessions(userId, status, page, size));
    }

    @PostMapping("/consultations/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(@CurrentUserId String userId,
                                                        @PathVariable String sessionId,
                                                        @RequestBody Map<String, Object> body) {
        String content = firstString(body, "content");
        String imageUrl = firstString(body, "imageUrl", "image_url");
        return ApiResponse.ok(consultationService.sendMessage(userId, sessionId, content, imageUrl));
    }

    @GetMapping("/consultations/{sessionId}/messages")
    public ApiResponse<Map<String, Object>> listMessages(@CurrentUserId String userId,
                                                           @PathVariable String sessionId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(consultationService.listMessages(userId, sessionId, page, size));
    }

    @PostMapping("/consultations/{sessionId}/close")
    public ApiResponse<Void> closeSession(@CurrentUserId String userId,
                                          @PathVariable String sessionId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Integer rating = body == null ? null : intValue(body.get("rating"));
        String feedback = body == null ? null : firstString(body, "comment", "feedback");
        consultationService.closeSession(userId, sessionId, rating, feedback);
        return ApiResponse.ok(null);
    }

    @PostMapping("/consultations/{sessionId}/evaluate")
    public ApiResponse<Void> evaluate(@CurrentUserId String userId,
                                      @PathVariable String sessionId,
                                      @RequestBody Map<String, Object> body) {
        Integer rating = intValue(body.get("rating"));
        String feedback = firstString(body, "comment", "feedback");
        consultationService.closeSession(userId, sessionId, rating, feedback);
        return ApiResponse.ok(null);
    }

    @GetMapping("/consultations/{sessionId}/ai-suggestion")
    public ApiResponse<Map<String, Object>> aiSuggestion(@CurrentUserId String userId,
                                                          @PathVariable String sessionId) {
        return ApiResponse.ok(consultationService.getAiSuggestion(userId, sessionId));
    }

    @GetMapping("/consultations/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(consultationService.getDifyWorkflowSpec());
    }

    private String firstString(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
