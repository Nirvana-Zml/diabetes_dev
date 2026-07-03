package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.service.AdminStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(adminStatsService.getOverview());
    }

    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> trends(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(adminStatsService.getTrends(days));
    }

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> users(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminStatsService.listUsers(page, size));
    }

    @GetMapping("/users/{subjectId}/brief")
    public ApiResponse<Map<String, Object>> userBrief(@PathVariable String subjectId) {
        return ApiResponse.ok(adminStatsService.getSubjectBrief(subjectId));
    }
}
