package com.diabetes.user.service;

import com.diabetes.common.client.CheckinServiceClient;
import com.diabetes.common.util.IdGenerator;
import com.diabetes.user.dto.CreateMessageRequest;
import com.diabetes.user.entity.HealthInterventionLog;
import com.diabetes.user.mapper.HealthInterventionLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HealthInterventionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(HealthInterventionOrchestrator.class);

    private final HealthTrendAnalysisService trendAnalysisService;
    private final InterventionPolicyEngine policyEngine;
    private final UserMessageService userMessageService;
    private final HealthInterventionLogMapper interventionLogMapper;
    private final CheckinServiceClient checkinServiceClient;
    private final ObjectMapper objectMapper;
    private final String difyInternalKey;
    private final int cooldownHours;
    private final int dailyAlertMax;

    public HealthInterventionOrchestrator(HealthTrendAnalysisService trendAnalysisService,
                                          InterventionPolicyEngine policyEngine,
                                          UserMessageService userMessageService,
                                          HealthInterventionLogMapper interventionLogMapper,
                                          CheckinServiceClient checkinServiceClient,
                                          ObjectMapper objectMapper,
                                          @Value("${dify-internal.key:}") String difyInternalKey,
                                          @Value("${intervention.cooldown-hours:4}") int cooldownHours,
                                          @Value("${intervention.daily-alert-max:2}") int dailyAlertMax) {
        this.trendAnalysisService = trendAnalysisService;
        this.policyEngine = policyEngine;
        this.userMessageService = userMessageService;
        this.interventionLogMapper = interventionLogMapper;
        this.checkinServiceClient = checkinServiceClient;
        this.objectMapper = objectMapper;
        this.difyInternalKey = difyInternalKey;
        this.cooldownHours = cooldownHours;
        this.dailyAlertMax = dailyAlertMax;
    }

    @Async
    public void evaluateAsync(String userId, String trigger, Map<String, Object> context) {
        try {
            evaluate(userId, trigger, context);
        } catch (Exception e) {
            log.warn("干预评估失败 userId={} trigger={} error={}", userId, trigger, e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> evaluate(String userId, String trigger, Map<String, Object> context) {
        if (userId == null || userId.isBlank()) {
            return Map.of("skipped", true, "reason", "empty_user");
        }
        Map<String, Object> ctx = context == null ? Map.of() : context;
        interventionLogMapper.expireResolved(userId, LocalDateTime.now());

        if (isInCooldown(userId)) {
            return Map.of("skipped", true, "reason", "cooldown");
        }

        List<Map<String, Object>> history = trendAnalysisService.fetchHistory(userId, 30);
        Map<String, Object> latest = history.isEmpty() ? Map.of() : history.get(0);
        Map<String, Object> difyTrend = Map.of();
        if (history.size() >= 2) {
            difyTrend = trendAnalysisService.callDifyTrendOnly(userId, 30);
        }

        Map<String, Object> plan = policyEngine.buildPlan(userId, trigger, history, latest, difyTrend, ctx);
        if (plan == null) {
            resolveIfImproved(userId, latest);
            return Map.of("skipped", true, "reason", "normal");
        }

        String severity = String.valueOf(plan.get("severity"));
        if (!"warning".equals(severity) && !"critical".equals(severity)) {
            return Map.of("skipped", true, "reason", "low_severity");
        }

        if ("warning".equals(severity) && interventionLogMapper.countAlertsToday(userId) >= dailyAlertMax) {
            return Map.of("skipped", true, "reason", "daily_limit");
        }

        String planId = IdGenerator.nextId("ivp_");
        plan.put("planId", planId);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        HealthInterventionLog logEntity = new HealthInterventionLog();
        logEntity.setPlanId(planId);
        logEntity.setUserId(userId);
        logEntity.setTriggerType(trigger);
        logEntity.setSeverity(severity);
        logEntity.setRiskLevel(String.valueOf(plan.get("riskLevel")));
        logEntity.setSummary(String.valueOf(plan.get("summary")));
        logEntity.setEvidence(toJson(plan.get("evidence")));
        logEntity.setActions(toJson(plan.get("actions")));
        logEntity.setStatus("active");
        logEntity.setExpiresAt(expiresAt);
        interventionLogMapper.insert(logEntity);

        dispatchMessage(userId, planId, plan);
        dispatchReminderAdjust(userId, planId, plan, expiresAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("severity", severity);
        result.put("summary", plan.get("summary"));
        return result;
    }

    public Map<String, Object> getActiveAlert(String userId) {
        HealthInterventionLog active = interventionLogMapper.findActiveByUserId(userId);
        if (active == null) {
            return Map.of("active", false, "has_alert", false);
        }
        String message = resolveAlertMessage(userId, active.getSummary());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", true);
        result.put("has_alert", true);
        result.put("severity", active.getSeverity());
        result.put("level", "critical".equals(active.getSeverity()) ? "error" : "warning");
        result.put("title", "critical".equals(active.getSeverity()) ? "健康指标异常" : "健康指标需关注");
        result.put("message", message);
        result.put("plan_id", active.getPlanId());
        result.put("planId", active.getPlanId());
        result.put("link_path", "/user-center");
        result.put("link_query", Map.of("section", "health-alert"));
        result.put("created_at", active.getCreatedAt());
        parseSuggestion(active, result);
        return result;
    }

    @Transactional
    public void acknowledge(String userId, String planId) {
        HealthInterventionLog logEntity = interventionLogMapper.findByPlanId(planId);
        if (logEntity == null || !userId.equals(logEntity.getUserId())) {
            return;
        }
        userMessageService.markReadByBiz(userId, "health_alert", planId);
    }

    private void dispatchMessage(String userId, String planId, Map<String, Object> plan) {
        String severity = String.valueOf(plan.get("severity"));
        String title = "critical".equals(severity) ? "健康指标异常" : "健康指标需关注";
        CreateMessageRequest request = new CreateMessageRequest(
                userId,
                "health_alert",
                "completed",
                title,
                String.valueOf(plan.get("summary")),
                planId,
                "/user-center",
                Map.of("section", "health-alert"),
                Map.of(
                        "severity", severity,
                        "plan_id", planId,
                        "suggestion", String.valueOf(plan.get("suggestion"))
                )
        );
        var created = userMessageService.createHealthAlertMessage(request);
        if (created != null) {
            interventionLogMapper.updateMessageId(planId, created.messageId());
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchReminderAdjust(String userId, String planId, Map<String, Object> plan,
                                        LocalDateTime expiresAt) {
        Object actionsObj = plan.get("actions");
        if (!(actionsObj instanceof List<?> actions)) {
            return;
        }
        List<Map<String, Object>> adjustments = null;
        for (Object action : actions) {
            if (action instanceof Map<?, ?> map && "reminder_adjust".equals(String.valueOf(map.get("type")))) {
                Object adj = map.get("adjustments");
                if (adj instanceof List<?> list) {
                    adjustments = (List<Map<String, Object>>) (List<?>) list;
                }
            }
        }
        if (adjustments == null || adjustments.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user_id", userId);
        body.put("userId", userId);
        body.put("intervention_id", planId);
        body.put("interventionId", planId);
        body.put("adjustments", adjustments);
        body.put("expires_at", expiresAt.toString());
        body.put("expiresAt", expiresAt.toString());
        checkinServiceClient.applySystemReminderAdjust(difyInternalKey, body);
    }

    private void resolveIfImproved(String userId, Map<String, Object> latest) {
        HealthInterventionLog active = interventionLogMapper.findActiveByUserId(userId);
        if (active == null) {
            return;
        }
        double fasting = numberValue(latest.get("fastingGlucose"), latest.get("fasting_glucose"));
        if (fasting > 0 && fasting < 6.1) {
            interventionLogMapper.resolveByPlanId(active.getPlanId());
        }
    }

    private boolean isInCooldown(String userId) {
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(cooldownHours, 1));
        return interventionLogMapper.countSince(userId, since) > 0;
    }

    private String resolveAlertMessage(String userId, String storedSummary) {
        if (!HealthTrendSummaryHelper.isUnavailablePlaceholder(storedSummary)) {
            return storedSummary;
        }
        try {
            Map<String, Object> trend = trendAnalysisService.analyze(userId, 30, false);
            String freshSummary = trend.get("summary") == null ? "" : String.valueOf(trend.get("summary"));
            if (!freshSummary.isBlank() && !HealthTrendSummaryHelper.isUnavailablePlaceholder(freshSummary)) {
                return freshSummary.length() > 300 ? freshSummary.substring(0, 300) : freshSummary;
            }
        } catch (Exception e) {
            log.debug("刷新预警摘要失败 userId={} error={}", userId, e.getMessage());
        }
        List<Map<String, Object>> history = trendAnalysisService.fetchHistory(userId, 30);
        return HealthTrendSummaryHelper.resolveSummary(storedSummary, history);
    }

    private void parseSuggestion(HealthInterventionLog active, Map<String, Object> result) {
        try {
            if (active.getEvidence() != null) {
                // no-op
            }
            List<Map<String, Object>> actions = objectMapper.readValue(
                    active.getActions(), new TypeReference<>() {});
            for (Map<String, Object> action : actions) {
                if ("message".equals(action.get("type"))) {
                    // suggestion from message extra handled elsewhere
                }
            }
        } catch (Exception ignored) {
            // optional
        }
        List<Map<String, Object>> history = trendAnalysisService.fetchHistory(active.getUserId(), 30);
        if (HealthTrendSummaryHelper.isUnavailablePlaceholder(active.getSummary())) {
            result.put("suggestion", LocalHealthTrendAnalyzer.analyze(history).suggestion());
            return;
        }
        result.put("suggestion", "建议复查相关指标，必要时咨询内分泌科医生。");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private double numberValue(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return 0;
    }
}
