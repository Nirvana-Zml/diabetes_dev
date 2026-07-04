package com.diabetes.user.service;

import com.diabetes.common.client.CheckinServiceClient;
import com.diabetes.user.dto.CreateMessageRequest;
import com.diabetes.user.dto.UserMessageResponse;
import com.diabetes.user.entity.HealthInterventionLog;
import com.diabetes.user.mapper.HealthInterventionLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthInterventionOrchestratorTest {

    private HealthTrendAnalysisService trendAnalysisService;
    private InterventionPolicyEngine policyEngine;
    private UserMessageService userMessageService;
    private HealthInterventionLogMapper interventionLogMapper;
    private CheckinServiceClient checkinServiceClient;
    private ObjectMapper objectMapper;
    private HealthInterventionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        trendAnalysisService = mock(HealthTrendAnalysisService.class);
        policyEngine = mock(InterventionPolicyEngine.class);
        userMessageService = mock(UserMessageService.class);
        interventionLogMapper = mock(HealthInterventionLogMapper.class);
        checkinServiceClient = mock(CheckinServiceClient.class);
        objectMapper = new ObjectMapper();
        orchestrator = new HealthInterventionOrchestrator(
                trendAnalysisService, policyEngine, userMessageService,
                interventionLogMapper, checkinServiceClient, objectMapper,
                "internal-key", 4, 2);
    }

    @Test
    void evaluate_usesEmptyLatestWhenHistoryEmpty() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of());
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), eq(Map.of()), anyMap(), anyMap()))
                .thenReturn(null);

        Map<String, Object> result = orchestrator.evaluate("u1", "trigger", null);

        assertEquals("normal", result.get("reason"));
    }

    @Test
    void evaluate_resolveIfImprovedIgnoresInvalidGlucose() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", "bad")));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(null);
        HealthInterventionLog active = new HealthInterventionLog();
        active.setPlanId("ivp_old");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(interventionLogMapper, never()).resolveByPlanId(anyString());
    }

    @Test
    void getActiveAlert_readsEvidenceAndMessageAction() throws Exception {
        HealthInterventionLog active = activeLog("warning", "需关注");
        active.setEvidence("[]");
        active.setActions(objectMapper.writeValueAsString(List.of(
                Map.of("type", "message", "title", "提醒")
        )));
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertNotNull(result.get("suggestion"));
    }

    @Test
    void evaluate_skipsReminderAdjustWhenActionNotMap() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 12.0)));
        Map<String, Object> plan = criticalPlan();
        plan.put("actions", List.of("bad", Map.of("type", "reminder_adjust", "adjustments", "bad")));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(plan);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(checkinServiceClient, never()).applySystemReminderAdjust(anyString(), anyMap());
    }

    @Test
    void getActiveAlert_hitsMessageActionBranch() throws Exception {
        HealthInterventionLog active = activeLog("warning", "需关注");
        active.setActions(objectMapper.writeValueAsString(List.of(
                Map.of("type", "message", "title", "请复查"),
                Map.of("type", "other")
        )));
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        orchestrator.getActiveAlert("u1");
    }

    @Test
    void evaluateAsync_runsEvaluateSuccessfully() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(1);

        assertDoesNotThrow(() -> orchestrator.evaluateAsync("u1", "trigger", Map.of()));
    }

    @Test
    void evaluate_skipsReminderAdjustWhenAdjustmentsEmpty() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 12.0)));
        Map<String, Object> plan = criticalPlan();
        plan.put("actions", List.of(Map.of("type", "reminder_adjust", "adjustments", List.of())));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(plan);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(checkinServiceClient, never()).applySystemReminderAdjust(anyString(), anyMap());
    }

    @Test
    void evaluate_resolveIfImprovedSkipsWhenNoActiveAlert() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 5.5)));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(null);
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(null);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(interventionLogMapper, never()).resolveByPlanId(anyString());
    }

    @Test
    void evaluate_resolveIfImprovedSkipsWhenGlucoseStillHigh() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 7.0)));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(null);
        HealthInterventionLog active = new HealthInterventionLog();
        active.setPlanId("ivp_old");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(interventionLogMapper, never()).resolveByPlanId(anyString());
    }

    @Test
    void evaluate_skipsEmptyUser() {
        Map<String, Object> result = orchestrator.evaluate(null, "trigger", Map.of());
        assertEquals(true, result.get("skipped"));
        assertEquals("empty_user", result.get("reason"));

        result = orchestrator.evaluate("  ", "trigger", null);
        assertEquals("empty_user", result.get("reason"));
        verifyNoInteractions(interventionLogMapper);
    }

    @Test
    void evaluate_skipsDuringCooldown() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(1);

        Map<String, Object> result = orchestrator.evaluate("u1", "health_record_saved", Map.of());

        assertEquals(true, result.get("skipped"));
        assertEquals("cooldown", result.get("reason"));
        verify(interventionLogMapper).expireResolved(eq("u1"), any(LocalDateTime.class));
        verifyNoInteractions(policyEngine);
    }

    @Test
    void evaluate_skipsWhenPlanNormalAndResolvesImproved() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(
                Map.of("fastingGlucose", 5.5)));
        when(policyEngine.buildPlan(eq("u1"), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(null);

        HealthInterventionLog active = new HealthInterventionLog();
        active.setPlanId("ivp_old");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        Map<String, Object> result = orchestrator.evaluate("u1", "health_record_saved", Map.of());

        assertEquals("normal", result.get("reason"));
        verify(interventionLogMapper).resolveByPlanId("ivp_old");
    }

    @Test
    void evaluate_skipsLowSeverity() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 6.0)));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(Map.of("severity", "info", "summary", "info only"));

        Map<String, Object> result = orchestrator.evaluate("u1", "trigger", Map.of());

        assertEquals("low_severity", result.get("reason"));
    }

    @Test
    void evaluate_skipsDailyLimitForWarning() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 7.5)));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(warningPlan());
        when(interventionLogMapper.countAlertsToday("u1")).thenReturn(2);

        Map<String, Object> result = orchestrator.evaluate("u1", "trigger", Map.of());

        assertEquals("daily_limit", result.get("reason"));
    }

    @Test
    void evaluate_createsWarningIntervention() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        List<Map<String, Object>> history = sampleHistory();
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(history);
        when(trendAnalysisService.callDifyTrendOnly("u1", 30)).thenReturn(Map.of("summary", "trend"));
        when(policyEngine.buildPlan(eq("u1"), eq("health_record_saved"), eq(history), anyMap(), anyMap(), anyMap()))
                .thenReturn(warningPlan());
        when(interventionLogMapper.countAlertsToday("u1")).thenReturn(0);
        UserMessageResponse message = new UserMessageResponse(
                "msg_1", "health_alert", "completed", "Title", "Summary", "ivp_x",
                "/user-center", Map.of(), Map.of(), false, LocalDateTime.now(), LocalDateTime.now());
        when(userMessageService.createHealthAlertMessage(any(CreateMessageRequest.class))).thenReturn(message);

        Map<String, Object> result = orchestrator.evaluate("u1", "health_record_saved", Map.of());

        assertNotNull(result.get("planId"));
        assertEquals("warning", result.get("severity"));
        verify(interventionLogMapper).insert(any(HealthInterventionLog.class));
        verify(interventionLogMapper).updateMessageId(anyString(), eq("msg_1"));
        verify(checkinServiceClient).applySystemReminderAdjust(eq("internal-key"), anyMap());
    }

    @Test
    void evaluate_createsCriticalInterventionWithoutMessageUpdateWhenNotifyDisabled() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 12.0)));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(criticalPlan());
        when(userMessageService.createHealthAlertMessage(any())).thenReturn(null);

        Map<String, Object> result = orchestrator.evaluate("u1", "trigger", Map.of());

        assertEquals("critical", result.get("severity"));
        verify(interventionLogMapper, never()).updateMessageId(anyString(), anyString());
    }

    @Test
    void evaluateAsync_swallowsException() {
        when(interventionLogMapper.countSince(anyString(), any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> orchestrator.evaluateAsync("u1", "trigger", Map.of()));
    }

    @Test
    void getActiveAlert_noActive() {
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(null);

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertEquals(false, result.get("active"));
        assertEquals(false, result.get("has_alert"));
    }

    @Test
    void getActiveAlert_refreshesUnavailablePlaceholderSummary() {
        HealthInterventionLog active = activeLog("warning",
                "已记录7条健康数据，但AI分析暂时不可用，请稍后重试或查看原始数据。");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);
        when(trendAnalysisService.analyze("u1", 30, false)).thenReturn(Map.of(
                "summary", "您的空腹血糖近期持续偏高，建议加强监测。",
                "source", "dify",
                "riskLevel", "warning"));
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(
                Map.of("fastingGlucose", 7.2, "bmi", 21.3, "systolicBp", 128, "diastolicBp", 82)));

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertEquals(true, result.get("has_alert"));
        assertTrue(String.valueOf(result.get("message")).contains("空腹血糖"));
        assertFalse(String.valueOf(result.get("message")).contains("AI分析暂时不可用"));
    }

    @Test
    void getActiveAlert_fallsBackToLocalWhenTrendUnavailable() {
        HealthInterventionLog active = activeLog("warning",
                "已记录7条健康数据，但AI分析暂时不可用，请稍后重试或查看原始数据。");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);
        when(trendAnalysisService.analyze("u1", 30, false)).thenReturn(Map.of(
                "summary", "已记录7条健康数据，但AI分析暂时不可用，请稍后重试或查看原始数据。",
                "source", "local"));
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(
                Map.of("fastingGlucose", 8.0, "bmi", 21.3, "systolicBp", 128, "diastolicBp", 82),
                Map.of("fastingGlucose", 7.3)));

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertTrue(String.valueOf(result.get("message")).contains("根据近"));
        assertFalse(String.valueOf(result.get("message")).contains("AI分析暂时不可用"));
    }

    @Test
    void getActiveAlert_critical() {
        HealthInterventionLog active = activeLog("critical", "血糖异常");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertEquals(true, result.get("active"));
        assertEquals("error", result.get("level"));
        assertEquals("健康指标异常", result.get("title"));
        assertEquals("ivp_1", result.get("plan_id"));
    }

    @Test
    void getActiveAlert_warningWithInvalidActionsJson() {
        HealthInterventionLog active = activeLog("warning", "需关注");
        active.setActions("{bad");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertEquals("warning", result.get("level"));
        assertEquals("建议复查相关指标，必要时咨询内分泌科医生。", result.get("suggestion"));
    }

    @Test
    void getActiveAlert_parsesActionsJson() throws Exception {
        HealthInterventionLog active = activeLog("warning", "需关注");
        active.setActions(objectMapper.writeValueAsString(List.of(
                Map.of("type", "message", "title", "提醒")
        )));
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        Map<String, Object> result = orchestrator.getActiveAlert("u1");

        assertNotNull(result.get("suggestion"));
    }

    @Test
    void acknowledge_skipsWhenMissingOrWrongUser() {
        when(interventionLogMapper.findByPlanId("ivp_1")).thenReturn(null);
        orchestrator.acknowledge("u1", "ivp_1");
        verify(userMessageService, never()).markReadByBiz(anyString(), anyString(), anyString());

        HealthInterventionLog log = activeLog("warning", "x");
        log.setUserId("u2");
        when(interventionLogMapper.findByPlanId("ivp_1")).thenReturn(log);
        orchestrator.acknowledge("u1", "ivp_1");
        verify(userMessageService, never()).markReadByBiz(anyString(), anyString(), anyString());
    }

    @Test
    void acknowledge_marksMessageRead() {
        HealthInterventionLog log = activeLog("warning", "x");
        log.setUserId("u1");
        when(interventionLogMapper.findByPlanId("ivp_1")).thenReturn(log);

        orchestrator.acknowledge("u1", "ivp_1");

        verify(userMessageService).markReadByBiz("u1", "health_alert", "ivp_1");
    }

    @Test
    void evaluate_usesSnakeCaseGlucoseForResolve() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(
                Map.of("fasting_glucose", "5.0")));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(null);
        HealthInterventionLog active = new HealthInterventionLog();
        active.setPlanId("ivp_old");
        when(interventionLogMapper.findActiveByUserId("u1")).thenReturn(active);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(interventionLogMapper).resolveByPlanId("ivp_old");
    }

    @Test
    void evaluate_skipsReminderAdjustWhenActionsNotList() {
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 12.0)));
        Map<String, Object> plan = criticalPlan();
        plan.put("actions", "not-a-list");
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(plan);

        orchestrator.evaluate("u1", "trigger", Map.of());

        verify(checkinServiceClient, never()).applySystemReminderAdjust(anyString(), anyMap());
    }

    @Test
    void evaluate_toJsonFailureUsesFallback() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json fail"));
        HealthInterventionOrchestrator failingOrchestrator = new HealthInterventionOrchestrator(
                trendAnalysisService, policyEngine, userMessageService,
                interventionLogMapper, checkinServiceClient, failingMapper,
                "internal-key", 4, 2);
        when(interventionLogMapper.countSince(eq("u1"), any())).thenReturn(0);
        when(trendAnalysisService.fetchHistory("u1", 30)).thenReturn(List.of(Map.of("fastingGlucose", 12.0)));
        when(policyEngine.buildPlan(anyString(), anyString(), anyList(), anyMap(), anyMap(), anyMap()))
                .thenReturn(criticalPlan());

        Map<String, Object> result = failingOrchestrator.evaluate("u1", "trigger", Map.of());

        assertNotNull(result.get("planId"));
        verify(interventionLogMapper).insert(any(HealthInterventionLog.class));
    }

    private static List<Map<String, Object>> sampleHistory() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("fastingGlucose", 7.2);
        Map<String, Object> older = new LinkedHashMap<>();
        older.put("fastingGlucose", 6.0);
        return List.of(latest, older);
    }

    private static Map<String, Object> warningPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("severity", "warning");
        plan.put("riskLevel", "warning");
        plan.put("summary", "血糖偏高");
        plan.put("suggestion", "建议复查");
        plan.put("evidence", List.of());
        plan.put("actions", List.of(
                Map.of("type", "message"),
                Map.of("type", "reminder_adjust", "adjustments", List.of(
                        Map.of("checkin_type", 4, "action", "add_times", "times", List.of("07:00"))
                ))
        ));
        return plan;
    }

    private static Map<String, Object> criticalPlan() {
        Map<String, Object> plan = warningPlan();
        plan.put("severity", "critical");
        return plan;
    }

    private static HealthInterventionLog activeLog(String severity, String summary) {
        HealthInterventionLog log = new HealthInterventionLog();
        log.setPlanId("ivp_1");
        log.setUserId("u1");
        log.setSeverity(severity);
        log.setSummary(summary);
        log.setCreatedAt(LocalDateTime.of(2026, 7, 2, 10, 0));
        return log;
    }
}
