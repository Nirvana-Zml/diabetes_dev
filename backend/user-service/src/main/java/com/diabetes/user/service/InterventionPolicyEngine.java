package com.diabetes.user.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 干预策略引擎：合并规则阈值与 Dify 趋势输出，后端最终裁决 severity。
 */
@Service
public class InterventionPolicyEngine {

    private static final double GLUCOSE_WARNING = 7.0;
    private static final double GLUCOSE_CRITICAL = 11.1;
    private static final double GLUCOSE_TREND_MIN = 6.1;
    private static final double GLUCOSE_TREND_RISE_PCT = 15.0;

    public Map<String, Object> buildPlan(String userId, String trigger,
                                         List<Map<String, Object>> history,
                                         Map<String, Object> latestRecord,
                                         Map<String, Object> difyTrend,
                                         Map<String, Object> eventContext) {
        String severity = "normal";
        String riskLevel = "normal";
        List<Map<String, Object>> evidence = new ArrayList<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();

        applyRecordRules(history, latestRecord, evidence, anomalies);
        applyEventContextRules(eventContext, evidence, anomalies);
        if (difyTrend != null && !difyTrend.isEmpty()) {
            applyDifyTrend(difyTrend, evidence, anomalies);
            riskLevel = stringValue(difyTrend.get("riskLevel"), difyTrend.get("risk_level"), "normal");
        }

        severity = maxSeverity(severity, fromAnomalies(anomalies));
        severity = maxSeverity(severity, fromRiskLevel(riskLevel));

        if ("normal".equals(severity) || "info".equals(severity)) {
            return null;
        }

        String summary = buildSummary(difyTrend, latestRecord, anomalies, severity);
        String suggestion = firstSuggestion(anomalies, difyTrend);

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("userId", userId);
        plan.put("trigger", trigger);
        plan.put("severity", severity);
        plan.put("riskLevel", riskLevel);
        plan.put("summary", summary);
        plan.put("suggestion", suggestion);
        plan.put("evidence", evidence);
        plan.put("anomalies", anomalies);
        plan.put("actions", buildActions(severity, anomalies, eventContext));
        return plan;
    }

    private void applyRecordRules(List<Map<String, Object>> history, Map<String, Object> latest,
                                  List<Map<String, Object>> evidence,
                                  List<Map<String, Object>> anomalies) {
        if (latest == null || latest.isEmpty()) {
            return;
        }
        double fasting = numberValue(latest.get("fastingGlucose"), latest.get("fasting_glucose"));
        if (fasting >= GLUCOSE_CRITICAL) {
            addAnomaly(anomalies, "glucose", "critical", fasting,
                    "空腹血糖 " + fasting + " mmol/L 明显偏高，请尽快复查并联系医生",
                    "建议尽快就医复查");
            addEvidence(evidence, "health_records", "fasting_glucose", fasting, GLUCOSE_CRITICAL);
        } else if (fasting >= GLUCOSE_WARNING) {
            addAnomaly(anomalies, "glucose", "warning", fasting,
                    "空腹血糖 " + fasting + " mmol/L 超过正常上限，建议关注",
                    "建议复查空腹血糖");
            addEvidence(evidence, "health_records", "fasting_glucose", fasting, GLUCOSE_WARNING);
        }

        double systolic = numberValue(latest.get("systolicBp"), latest.get("systolic_bp"));
        double diastolic = numberValue(latest.get("diastolicBp"), latest.get("diastolic_bp"));
        if (systolic >= 140 || diastolic >= 90) {
            addAnomaly(anomalies, "bp", "warning", systolic,
                    "血压偏高（" + (int) systolic + "/" + (int) diastolic + " mmHg）",
                    "建议监测血压并咨询医生");
            addEvidence(evidence, "health_records", "systolic_bp", systolic, 140);
        }

        if (history != null && history.size() >= 2) {
            double first = extractGlucose(history.get(history.size() - 1));
            double last = extractGlucose(history.get(0));
            if (first > 0 && last >= GLUCOSE_TREND_MIN) {
                double changeRate = (last - first) / first * 100.0;
                if (changeRate >= GLUCOSE_TREND_RISE_PCT) {
                    addAnomaly(anomalies, "glucose", "warning", last,
                            "近阶段空腹血糖呈上升趋势（升幅约 " + String.format("%.1f", changeRate) + "%）",
                            "建议加强血糖监测");
                    addEvidence(evidence, "health_records", "glucose_trend_change_rate", changeRate, GLUCOSE_TREND_RISE_PCT);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyEventContextRules(Map<String, Object> context, List<Map<String, Object>> evidence,
                                          List<Map<String, Object>> anomalies) {
        if (context == null) {
            return;
        }
        Object glucoseVal = context.get("glucose_value");
        if (glucoseVal != null) {
            double value = numberValue(glucoseVal);
            if (value >= GLUCOSE_CRITICAL) {
                addAnomaly(anomalies, "glucose", "critical", value,
                        "打卡血糖 " + value + " mmol/L 明显偏高",
                        "建议尽快就医");
                addEvidence(evidence, "checkin", "glucose_value", value, GLUCOSE_CRITICAL);
            } else if (value >= GLUCOSE_WARNING) {
                addAnomaly(anomalies, "glucose", "warning", value,
                        "打卡血糖 " + value + " mmol/L 偏高",
                        "建议调整饮食并加强监测");
                addEvidence(evidence, "checkin", "glucose_value", value, GLUCOSE_WARNING);
            }
        }
        Object rawAnomalies = context.get("anomalies");
        if (rawAnomalies instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String type = stringValue(map.get("type"));
                String sev = mapValueSeverity(map.get("severity"));
                if (sev == null) {
                    sev = severityFromCheckinType(type);
                }
                addAnomaly(anomalies, type, sev, numberValue(map.get("value")),
                        stringValue(map.get("description")),
                        stringValue(map.get("suggestion"), map.get("possible_reason")));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyDifyTrend(Map<String, Object> difyTrend, List<Map<String, Object>> evidence,
                                List<Map<String, Object>> anomalies) {
        Object raw = difyTrend.get("anomalies");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String severity = mapValueSeverity(map.get("severity"));
            if (severity == null) {
                severity = "warning";
            }
            addAnomaly(anomalies,
                    stringValue(map.get("type")),
                    severity,
                    numberValue(map.get("value")),
                    stringValue(map.get("description"), map.get("alert")),
                    stringValue(map.get("suggestion")));
            addEvidence(evidence, "dify_trend", stringValue(map.get("type")), numberValue(map.get("value")), 0);
        }
    }

    private List<Map<String, Object>> buildActions(String severity, List<Map<String, Object>> anomalies,
                                                   Map<String, Object> context) {
        List<Map<String, Object>> actions = new ArrayList<>();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "message");
        message.put("messageType", "health_alert");
        message.put("title", "critical".equals(severity) ? "健康指标异常" : "健康指标需关注");
        message.put("linkPath", "/user-center");
        message.put("linkQuery", Map.of("section", "health-alert"));
        actions.add(message);

        List<Map<String, Object>> adjustments = new ArrayList<>();
        if (hasGlucoseIssue(anomalies) || "critical".equals(severity)) {
            Map<String, Object> adj = new LinkedHashMap<>();
            adj.put("checkin_type", 4);
            adj.put("action", "add_times");
            adj.put("times", "critical".equals(severity)
                    ? List.of("07:00", "10:00", "14:00")
                    : List.of("07:00", "10:00"));
            adj.put("duration_days", "critical".equals(severity) ? 14 : 7);
            adj.put("reason", "glucose_trend_rising");
            adjustments.add(adj);
        }
        if (hasCheckinMissed(context)) {
            Map<String, Object> adj = new LinkedHashMap<>();
            adj.put("checkin_type", 4);
            adj.put("action", "apply_defaults");
            adj.put("duration_days", 7);
            adj.put("reason", "checkin_missed_streak");
            adjustments.add(adj);
        }
        if (!adjustments.isEmpty()) {
            Map<String, Object> reminder = new LinkedHashMap<>();
            reminder.put("type", "reminder_adjust");
            reminder.put("adjustments", adjustments);
            actions.add(reminder);
        }
        return actions;
    }

    private boolean hasGlucoseIssue(List<Map<String, Object>> anomalies) {
        return anomalies.stream().anyMatch(a -> "glucose".equals(String.valueOf(a.get("type"))));
    }

    private boolean hasCheckinMissed(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        Object raw = context.get("anomalies");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        return list.stream().anyMatch(item -> item instanceof Map<?, ?> map
                && "missed_all".equals(String.valueOf(map.get("type"))));
    }

    private String buildSummary(Map<String, Object> difyTrend, Map<String, Object> latest,
                                List<Map<String, Object>> anomalies, String severity) {
        if (difyTrend != null) {
            String summary = stringValue(difyTrend.get("summary"));
            if (!summary.isBlank()) {
                return summary.length() > 300 ? summary.substring(0, 300) : summary;
            }
        }
        if (!anomalies.isEmpty()) {
            return String.valueOf(anomalies.get(0).get("description"));
        }
        double fasting = latest == null ? 0 : numberValue(latest.get("fastingGlucose"), latest.get("fasting_glucose"));
        if ("critical".equals(severity)) {
            return "检测到健康指标异常（空腹血糖 " + fasting + " mmol/L），请尽快关注。";
        }
        return "检测到健康指标需关注，建议加强自我监测。";
    }

    private String firstSuggestion(List<Map<String, Object>> anomalies, Map<String, Object> difyTrend) {
        for (Map<String, Object> a : anomalies) {
            String s = stringValue(a.get("suggestion"));
            if (!s.isBlank()) {
                return s;
            }
        }
        return "建议复查相关指标，必要时咨询内分泌科医生。";
    }

    private String fromAnomalies(List<Map<String, Object>> anomalies) {
        String max = "normal";
        for (Map<String, Object> a : anomalies) {
            max = maxSeverity(max, stringValue(a.get("severity")));
        }
        return max;
    }

    private String fromRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "normal";
        }
        return switch (riskLevel.toLowerCase()) {
            case "critical" -> "critical";
            case "warning" -> "warning";
            case "attention" -> "warning";
            default -> "normal";
        };
    }

    private String severityFromCheckinType(String type) {
        if ("glucose_abnormal".equals(type)) {
            return "warning";
        }
        if ("missed_all".equals(type) || "medication_missed".equals(type)) {
            return "warning";
        }
        return "warning";
    }

    private String maxSeverity(String a, String b) {
        return severityRank(b) > severityRank(a) ? b : a;
    }

    private int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toLowerCase()) {
            case "critical" -> 3;
            case "warning" -> 2;
            case "info", "attention" -> 1;
            default -> 0;
        };
    }

    private void addAnomaly(List<Map<String, Object>> anomalies, String type, String severity,
                            double value, String description, String suggestion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("severity", severity);
        item.put("value", value);
        item.put("description", description);
        item.put("suggestion", suggestion);
        anomalies.add(item);
    }

    private void addEvidence(List<Map<String, Object>> evidence, String source, String metric,
                             double value, double threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source", source);
        item.put("metric", metric);
        item.put("value", value);
        if (threshold > 0) {
            item.put("threshold", threshold);
        }
        evidence.add(item);
    }

    private double extractGlucose(Map<String, Object> record) {
        if (record == null) {
            return 0;
        }
        return numberValue(record.get("fastingGlucose"), record.get("fasting_glucose"));
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
                // try next
            }
        }
        return 0;
    }

    private String stringValue(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private String mapValueSeverity(Object severity) {
        if (severity == null) {
            return null;
        }
        String s = String.valueOf(severity).toLowerCase();
        if ("critical".equals(s) || "warning".equals(s) || "info".equals(s)) {
            return s;
        }
        return "warning";
    }
}
