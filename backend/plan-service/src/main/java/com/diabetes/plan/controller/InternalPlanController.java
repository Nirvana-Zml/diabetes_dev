package com.diabetes.plan.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.plan.service.PlanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/plan")
public class InternalPlanController {

    private final PlanService planService;
    private final String difyInternalKey;

    public InternalPlanController(PlanService planService,
                                  @Value("${dify-internal.key:}") String difyInternalKey) {
        this.planService = planService;
        this.difyInternalKey = difyInternalKey;
    }

    @GetMapping("/user/{userId}/history")
    public ApiResponse<Map<String, Object>> history(@PathVariable String userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int size,
                                                    @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        return ApiResponse.ok(planService.getHistory(userId, page, size));
    }

    @GetMapping("/user/{userId}/latest")
    public ApiResponse<Map<String, Object>> latest(@PathVariable String userId,
                                                   @RequestHeader(value = "X-Dify-Key", required = false) String key) {
        validateDifyKey(key);
        Map<String, Object> plan = planService.getLatest(userId);
        return ApiResponse.ok(plan == null ? Map.of() : plan);
    }

    private void validateDifyKey(String key) {
        if (difyInternalKey != null && !difyInternalKey.isBlank()
                && (key == null || !difyInternalKey.equals(key))) {
            throw new BusinessException(401, "Dify 内部密钥无效");
        }
    }
}
