package com.diabetes.checkin.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.checkin.service.CheckinMgmtService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkin-management")
public class CheckinMgmtController {

    private final CheckinMgmtService checkinMgmtService;

    public CheckinMgmtController(CheckinMgmtService checkinMgmtService) {
        this.checkinMgmtService = checkinMgmtService;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@CurrentUserId String userId,
                                               @RequestParam String startDate,
                                               @RequestParam String endDate) {
        return ApiResponse.ok(checkinMgmtService.getStats(userId, parseDate(startDate), parseDate(endDate)));
    }

    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> trends(@CurrentUserId String userId,
                                                 @RequestParam String startDate,
                                                 @RequestParam String endDate) {
        return ApiResponse.ok(checkinMgmtService.getTrends(userId, parseDate(startDate), parseDate(endDate)));
    }

    @GetMapping("/ai-summary")
    public ApiResponse<Map<String, Object>> aiSummary(@CurrentUserId String userId,
                                                    @RequestParam(required = false) String startDate,
                                                    @RequestParam(required = false) String endDate) {
        LocalDate end = endDate == null ? LocalDate.now() : parseDate(endDate);
        LocalDate start = startDate == null ? end.minusDays(29) : parseDate(startDate);
        return ApiResponse.ok(checkinMgmtService.getAiSummary(userId, start, end));
    }

    /**
     * 返回 Dify 打卡行为分析工作流的入参/出参 JSON 契约，便于配置工作流开始/输出节点。
     */
    @GetMapping("/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(checkinMgmtService.getDifyWorkflowSpec());
    }

    @PostMapping("/export")
    public ApiResponse<Map<String, Object>> export(@CurrentUserId String userId,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.ok(checkinMgmtService.exportReport(
                userId,
                parseDate(body.get("startDate")),
                parseDate(body.get("endDate")),
                body.getOrDefault("format", "pdf")));
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "日期参数错误");
        }
    }
}
