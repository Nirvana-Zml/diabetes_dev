package com.diabetes.health.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.health.dto.RiskAssessRequest;
import com.diabetes.health.service.RiskAssessmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping("/assess")
    public ApiResponse<Map<String, Object>> assess(@CurrentUserId String userId,
                                                   @Valid @RequestBody RiskAssessRequest request) {
        try {
            return ApiResponse.ok(riskAssessmentService.assess(userId, request));
        } catch (BusinessException e) {
            riskAssessmentService.notifyAssessmentFailed(userId, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            riskAssessmentService.notifyAssessmentFailed(userId, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(riskAssessmentService.getDifyWorkflowSpec());
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(@CurrentUserId String userId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(riskAssessmentService.getHistory(userId, page, size));
    }

    @GetMapping("/{assessmentId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String assessmentId) {
        Map<String, Object> detail = riskAssessmentService.getDetail(assessmentId);
        if (detail == null) {
            throw new BusinessException(404, "评估记录不存在");
        }
        return ApiResponse.ok(detail);
    }
}
