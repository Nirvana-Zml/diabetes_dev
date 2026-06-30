package com.diabetes.plan.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.plan.service.PlanService;
import com.diabetes.plan.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/plan")
public class PlanController {

    private final PlanService planService;
    private final UserProfileService userProfileService;

    public PlanController(PlanService planService, UserProfileService userProfileService) {
        this.planService = planService;
        this.userProfileService = userProfileService;
    }

    /**
     * 校验失败时返回 JSON（400），成功时返回 SSE 流。
     * 不可在 produces=text/event-stream 的方法上直接抛 BusinessException，否则 GlobalExceptionHandler 无法写 JSON。
     */
    @PostMapping(value = "/generate")
    public Object generate(@CurrentUserId String userId) {
        Map<String, Object> profile = userProfileService.buildUserProfile(userId);
        try {
            userProfileService.validateProfileForPlan(profile);
        } catch (BusinessException e) {
            HttpStatus status = e.getCode() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.fail(e.getCode(), e.getMessage()));
        }
        return planService.generatePlanStream(userId, profile);
    }

    /**
     * 返回 Dify 方案生成工作流的入参/出参 JSON 契约，便于配置工作流开始/输出节点。
     */
    @GetMapping("/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(planService.getDifyWorkflowSpec());
    }

    @GetMapping("/latest")
    public ApiResponse<Map<String, Object>> latest(@CurrentUserId String userId) {
        Map<String, Object> plan = planService.getLatest(userId);
        if (plan == null) {
            throw new BusinessException(404, "无方案记录");
        }
        return ApiResponse.ok(plan);
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(@CurrentUserId String userId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(planService.getHistory(userId, page, size));
    }

    @PostMapping("/{planId}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@CurrentUserId String userId,
                                                     @PathVariable String planId) {
        return ApiResponse.ok(planService.toggleFavorite(userId, planId));
    }

    @GetMapping("/{planId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String planId) {
        Map<String, Object> plan = planService.getDetail(planId);
        if (plan == null) {
            throw new BusinessException(404, "方案不存在");
        }
        return ApiResponse.ok(plan);
    }
}
