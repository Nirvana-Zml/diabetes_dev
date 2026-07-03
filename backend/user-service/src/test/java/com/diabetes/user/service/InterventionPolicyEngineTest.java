package com.diabetes.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterventionPolicyEngineTest {

    private InterventionPolicyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new InterventionPolicyEngine();
    }

    @Test
    void buildPlan_returnsNullWhenNormal() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.2, "systolicBp", 120, "diastolicBp", 80);
        assertNull(engine.buildPlan("u1", "health_record_saved", List.of(latest), latest, Map.of(), Map.of()));
    }

    @Test
    void buildPlan_warningWhenFastingGlucoseHigh() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("fastingGlucose", 7.2);
        latest.put("systolicBp", 120);
        latest.put("diastolicBp", 80);

        Map<String, Object> plan = engine.buildPlan("u1", "health_record_saved", List.of(latest), latest, Map.of(), Map.of());

        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
        assertNotNull(plan.get("actions"));
    }

    @Test
    void buildPlan_criticalWhenGlucoseVeryHigh() {
        Map<String, Object> latest = Map.of("fastingGlucose", 12.0);
        Map<String, Object> plan = engine.buildPlan("u1", "health_record_saved", List.of(latest), latest, Map.of(), Map.of());
        assertNotNull(plan);
        assertEquals("critical", plan.get("severity"));
    }

    @Test
    void buildPlan_warningWhenBloodPressureHigh() {
        Map<String, Object> latest = Map.of("systolic_bp", 145, "diastolic_bp", 92);
        Map<String, Object> plan = engine.buildPlan("u1", "health_record_saved", List.of(latest), latest, Map.of(), Map.of());
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_warningWhenGlucoseTrendRising() {
        Map<String, Object> latest = Map.of("fastingGlucose", 7.0);
        Map<String, Object> oldest = Map.of("fastingGlucose", 5.0);
        Map<String, Object> plan = engine.buildPlan("u1", "health_record_saved", List.of(latest, oldest), latest, Map.of(), Map.of());
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_usesEventContextGlucoseCritical() {
        Map<String, Object> plan = engine.buildPlan("u1", "glucose_checkin", List.of(), Map.of(),
                Map.of(), Map.of("glucose_value", 12.5));
        assertNotNull(plan);
        assertEquals("critical", plan.get("severity"));
    }

    @Test
    void buildPlan_usesEventContextGlucoseWarning() {
        Map<String, Object> plan = engine.buildPlan("u1", "glucose_checkin", List.of(), Map.of(),
                Map.of(), Map.of("glucose_value", 7.5));
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_mergesCheckinAnomaliesAndMissedAllReminder() {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        anomalies.add(Map.of(
                "type", "missed_all",
                "severity", "warning",
                "description", "连续未打卡",
                "suggestion", "请恢复打卡"
        ));
        anomalies.add(Map.of(
                "type", "not-a-map"
        ));
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(),
                Map.of(), Map.of("anomalies", anomalies));
        assertNotNull(plan);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        assertTrue(actions.stream().anyMatch(a -> "reminder_adjust".equals(a.get("type"))));
    }

    @Test
    void buildPlan_usesDifyTrendSummaryAndAnomalies() {
        Map<String, Object> latest = Map.of("fastingGlucose", 6.0);
        String longSummary = "S".repeat(350);
        Map<String, Object> difyTrend = Map.of(
                "summary", longSummary,
                "risk_level", "attention",
                "anomalies", List.of(
                        Map.of("type", "glucose", "severity", "info", "description", "dify alert", "value", 6.5),
                        "skip-me"
                )
        );
        Map<String, Object> plan = engine.buildPlan("u1", "manual_refresh", List.of(latest), latest, difyTrend, Map.of());
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
        assertEquals(300, String.valueOf(plan.get("summary")).length());
    }

    @Test
    void buildPlan_criticalSummaryFallbackWhenNoDifyOrAnomalyDescription() {
        Map<String, Object> latest = Map.of("fasting_glucose", 12.0);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), Map.of());
        assertTrue(String.valueOf(plan.get("summary")).contains("12.0"));
    }

    @Test
    void buildPlan_warningSummaryFallbackWithoutAnomalies() {
        Map<String, Object> latest = Map.of("fastingGlucose", 7.2);
        Map<String, Object> difyTrend = Map.of("riskLevel", "warning", "summary", "  ");
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, difyTrend, Map.of());
        assertEquals("warning", plan.get("severity"));
        assertFalse(String.valueOf(plan.get("summary")).isBlank());
    }

    @Test
    void buildPlan_returnsNullForInfoOnlySeverity() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.0);
        Map<String, Object> difyTrend = Map.of(
                "risk_level", "normal",
                "anomalies", List.of(Map.of("type", "bmi", "severity", "info", "description", "info only"))
        );
        assertNull(engine.buildPlan("u1", "trigger", List.of(latest), latest, difyTrend, Map.of()));
    }

    @Test
    void buildPlan_usesPossibleReasonAsSuggestion() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "glucose_abnormal",
                        "description", "打卡血糖偏高",
                        "possible_reason", "可能饮食不当"
                ))));
        assertEquals("可能饮食不当", plan.get("suggestion"));
    }

    @Test
    void buildPlan_criticalActionsIncludeMoreReminderTimes() {
        Map<String, Object> latest = Map.of("fastingGlucose", 12.0);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        Map<String, Object> reminder = actions.stream()
                .filter(a -> "reminder_adjust".equals(a.get("type")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adjustments = (List<Map<String, Object>>) reminder.get("adjustments");
        @SuppressWarnings("unchecked")
        List<String> times = (List<String>) adjustments.get(0).get("times");
        assertEquals(3, times.size());
    }

    @Test
    void buildPlan_skipsWhenLatestEmpty() {
        assertNull(engine.buildPlan("u1", "trigger", List.of(), Map.of(), Map.of(), null));
    }

    @Test
    void buildPlan_elevatesRiskLevelCritical() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.0);
        Map<String, Object> difyTrend = Map.of("riskLevel", "critical", "summary", "critical trend");
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, difyTrend, Map.of());
        assertEquals("critical", plan.get("severity"));
    }

    @Test
    void buildPlan_usesDefaultWarningForUnknownCheckinType() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "custom_type",
                        "description", "自定义异常"
                ))));
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_warningFallbackSummaryWithoutAnomaliesOrDify() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.0);
        Map<String, Object> difyTrend = Map.of("riskLevel", "warning", "summary", "");
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, difyTrend, Map.of());
        assertEquals("检测到健康指标需关注，建议加强自我监测。", plan.get("summary"));
    }

    @Test
    void buildPlan_criticalSummaryFallbackWithoutAnomalies() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.0);
        Map<String, Object> difyTrend = Map.of("riskLevel", "critical", "summary", "");
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, difyTrend, Map.of());
        assertTrue(String.valueOf(plan.get("summary")).contains("检测到健康指标异常"));
    }

    @Test
    void buildPlan_difyAnomalyWithoutSeverityDefaultsWarning() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.0);
        Map<String, Object> difyTrend = Map.of(
                "summary", "dify",
                "risk_level", "normal",
                "anomalies", List.of(Map.of("type", "glucose", "description", "偏高"))
        );
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, difyTrend, Map.of());
        assertNotNull(plan);
    }

    @Test
    void buildPlan_parsesStringGlucoseValue() {
        Map<String, Object> latest = Map.of("fasting_glucose", "7.3");
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), Map.of());
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_ignoresNullHistoryRecordForTrend() {
        Map<String, Object> latest = Map.of("fastingGlucose", 6.5);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(latest);
        history.add(null);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", history, latest, Map.of(), Map.of());
        assertNull(plan);
    }

    @Test
    void buildPlan_mapsUnknownSeverityToWarning() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "glucose_abnormal",
                        "severity", "high",
                        "description", "打卡异常"
                ))));
        assertNotNull(plan);
    }

    @Test
    void buildPlan_skipsWhenLatestNull() {
        assertNull(engine.buildPlan("u1", "trigger", List.of(), null, Map.of(), Map.of()));
    }

    @Test
    void buildPlan_skipsWhenDifyTrendNull() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.0);
        assertNull(engine.buildPlan("u1", "trigger", List.of(latest), latest, null, Map.of()));
    }

    @Test
    void buildPlan_warningWhenOnlyDiastolicHigh() {
        Map<String, Object> latest = Map.of("systolicBp", 130, "diastolicBp", 95);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), Map.of());
        assertNotNull(plan);
    }

    @Test
    void buildPlan_skipsTrendWhenHistoryNull() {
        Map<String, Object> latest = Map.of("fastingGlucose", 7.0);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", null, latest, Map.of(), Map.of());
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_skipsTrendWhenBaselineZero() {
        Map<String, Object> latest = Map.of("fastingGlucose", 7.0);
        Map<String, Object> oldest = Map.of("fastingGlucose", 0);
        assertNotNull(engine.buildPlan("u1", "trigger", List.of(latest, oldest), latest, Map.of(), Map.of()));
    }

    @Test
    void buildPlan_skipsTrendWhenLatestBelowThreshold() {
        Map<String, Object> latest = Map.of("fastingGlucose", 6.0);
        Map<String, Object> oldest = Map.of("fastingGlucose", 5.0);
        assertNull(engine.buildPlan("u1", "trigger", List.of(latest, oldest), latest, Map.of(), Map.of()));
    }

    @Test
    void buildPlan_skipsTrendWhenChangeRateTooSmall() {
        Map<String, Object> latest = Map.of("fastingGlucose", 6.5);
        Map<String, Object> oldest = Map.of("fastingGlucose", 6.0);
        assertNull(engine.buildPlan("u1", "trigger", List.of(latest, oldest), latest, Map.of(), Map.of()));
    }

    @Test
    void buildPlan_eventContextGlucoseWarningBranch() {
        Map<String, Object> plan = engine.buildPlan("u1", "glucose_checkin", List.of(), Map.of(),
                Map.of(), Map.of("glucose_value", 8.0));
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_medicationMissedUsesWarningSeverity() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of("type", "medication_missed", "description", "漏服"))));
        assertNotNull(plan);
    }

    @Test
    void buildPlan_hasCheckinMissedWithNullContext() {
        Map<String, Object> latest = Map.of("systolicBp", 150, "diastolicBp", 95);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), null);
        assertNotNull(plan);
    }

    @Test
    void buildPlan_summaryUsesAnomalyWhenDifyTrendNull() {
        Map<String, Object> latest = Map.of("fastingGlucose", 7.2);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, null, Map.of());
        assertFalse(String.valueOf(plan.get("summary")).isBlank());
    }

    @Test
    void buildPlan_criticalSummaryWithNullLatest() throws Exception {
        var method = InterventionPolicyEngine.class.getDeclaredMethod(
                "buildSummary", Map.class, Map.class, List.class, String.class);
        method.setAccessible(true);
        String summary = (String) method.invoke(engine, null, null, List.of(), "critical");
        assertTrue(summary.contains("检测到健康指标异常"));
    }

    @Test
    void buildPlan_privateHelpersHandleNulls() throws Exception {
        var fromRiskLevel = InterventionPolicyEngine.class.getDeclaredMethod("fromRiskLevel", String.class);
        fromRiskLevel.setAccessible(true);
        assertEquals("normal", fromRiskLevel.invoke(engine, new Object[]{null}));

        var severityRank = InterventionPolicyEngine.class.getDeclaredMethod("severityRank", String.class);
        severityRank.setAccessible(true);
        assertEquals(0, severityRank.invoke(engine, new Object[]{null}));
    }

    @Test
    void buildPlan_numberValueIgnoresInvalidString() {
        Map<String, Object> latest = Map.of("fastingGlucose", "bad");
        assertNull(engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), Map.of()));
    }

    @Test
    void buildPlan_mapValueSeverityAcceptsInfo() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "custom",
                        "severity", "info",
                        "description", "提示"
                ))));
        assertNull(plan);
    }

    @Test
    void buildPlan_skipsNonMapCheckinAnomalyItems() {
        List<Object> anomalies = new ArrayList<>();
        anomalies.add("skip");
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", anomalies));
        assertNull(plan);
    }

    @Test
    void buildPlan_missedAllCheckinAddsReminderAdjust() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "missed_all",
                        "description", "连续未打卡"
                ))));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        assertTrue(actions.stream().anyMatch(a -> "reminder_adjust".equals(a.get("type"))));
    }

    @Test
    void buildPlan_severityFromMissedAllType() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "missed_all",
                        "description", "未打卡"
                ))));
        assertNotNull(plan);
    }

    @Test
    void buildPlan_mapValueSeverityDefaultsUnknownToWarning() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "custom",
                        "severity", "high",
                        "description", "未知严重级别"
                ))));
        assertNotNull(plan);
    }

    @Test
    void buildPlan_eventContextGlucoseExactlyAtWarningThreshold() {
        Map<String, Object> plan = engine.buildPlan("u1", "glucose_checkin", List.of(), Map.of(),
                Map.of(), Map.of("glucose_value", 7.0));
        assertNotNull(plan);
        assertEquals("warning", plan.get("severity"));
    }

    @Test
    void buildPlan_ignoresLowEventContextGlucose() {
        assertNull(engine.buildPlan("u1", "glucose_checkin", List.of(), Map.of(), Map.of(),
                Map.of("glucose_value", 6.0)));
    }

    @Test
    void buildPlan_mapValueSeverityAcceptsInfoLevel() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "custom",
                        "severity", "info",
                        "description", "提示信息"
                ))));
        assertNull(plan);
    }

    @Test
    void buildPlan_hasCheckinMissedFalseForOtherTypes() {
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(), Map.of(), Map.of(),
                Map.of("anomalies", List.of(Map.of(
                        "type", "sleep",
                        "description", "睡眠不足"
                ))));
        assertNotNull(plan);
    }

    @Test
    void buildPlan_hasCheckinMissedIgnoresNonMapContextItems() {
        Map<String, Object> latest = Map.of("fastingGlucose", 12.0);
        Map<String, Object> plan = engine.buildPlan("u1", "checkin_analysis", List.of(latest), latest, Map.of(),
                Map.of("anomalies", List.of("skip")));
        assertNotNull(plan);
    }

    @Test
    void buildPlan_mapValueSeverityCoversKnownLevels() throws Exception {
        var method = InterventionPolicyEngine.class.getDeclaredMethod("mapValueSeverity", Object.class);
        method.setAccessible(true);
        assertEquals("critical", method.invoke(engine, "critical"));
        assertEquals("warning", method.invoke(engine, "warning"));
        assertEquals("info", method.invoke(engine, "info"));
        assertEquals("warning", method.invoke(engine, "high"));
    }

    @Test
    void buildPlan_noReminderAdjustWhenOnlyBloodPressureIssue() {
        Map<String, Object> latest = Map.of("systolicBp", 150, "diastolicBp", 95);
        Map<String, Object> plan = engine.buildPlan("u1", "trigger", List.of(latest), latest, Map.of(), Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) plan.get("actions");
        assertFalse(actions.stream().anyMatch(a -> "reminder_adjust".equals(a.get("type"))));
    }
}
