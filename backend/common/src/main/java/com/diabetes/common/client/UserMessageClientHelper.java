package com.diabetes.common.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息中心内部写入辅助（user-service POST /api/v1/internal/messages）。
 */
public final class UserMessageClientHelper {

    private UserMessageClientHelper() {
    }

    public static void notifyRiskCompleted(UserServiceClient client, String difyKey,
                                           String userId, String assessmentId,
                                           String levelLabel, int score) {
        Map<String, Object> linkQuery = Map.of("assessment_id", assessmentId);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("risk_level", levelLabel);
        extra.put("risk_score", score);
        client.createMessage(difyKey, baseBody(userId, "risk_assess", "completed",
                "风险评估报告已生成",
                levelLabel + " · " + score + " 分",
                assessmentId, "/health-evaluation", linkQuery, extra));
    }

    public static void notifyRiskFailed(UserServiceClient client, String difyKey,
                                        String userId, String errorSummary) {
        String summary = truncate(errorSummary, 80);
        Map<String, Object> extra = Map.of("error_summary", summary);
        client.createMessage(difyKey, baseBody(userId, "risk_assess", "failed",
                "风险评估失败",
                "风险评估失败：" + summary,
                "risk_fail_" + System.currentTimeMillis(),
                "/health-evaluation", Map.of(), extra));
    }

    public static void notifyPlanCompleted(UserServiceClient client, String difyKey,
                                           String userId, String planId) {
        client.createMessage(difyKey, baseBody(userId, "plan_generate", "completed",
                "健康方案已就绪",
                "您的个性化健康方案已就绪",
                planId, "/living-plans", Map.of(), Map.of()));
    }

    public static void notifyPlanFailed(UserServiceClient client, String difyKey,
                                        String userId, String errorSummary) {
        String summary = truncate(errorSummary, 80);
        Map<String, Object> extra = Map.of("error_summary", summary);
        client.createMessage(difyKey, baseBody(userId, "plan_generate", "failed",
                "方案生成失败",
                "方案生成失败：" + summary,
                "plan_fail_" + System.currentTimeMillis(),
                "/living-plans", Map.of(), extra));
    }

    public static void notifyConsultReply(UserServiceClient client, String difyKey,
                                          String userId, String sessionId,
                                          String doctorName) {
        Map<String, Object> linkQuery = Map.of("session_id", sessionId);
        Map<String, Object> extra = Map.of("doctor_name", doctorName);
        client.createMessage(difyKey, baseBody(userId, "consult_reply", "completed",
                "医生已回复",
                doctorName + " 回复了您的咨询",
                sessionId, "/consultation/chat", linkQuery, extra));
    }

    public static void notifyCheckinAnalysisCompleted(UserServiceClient client, String difyKey,
                                                      String userId, String start, String end) {
        String bizId = start + "_" + end;
        client.createMessage(difyKey, baseBody(userId, "checkin_analysis", "completed",
                "打卡分析已更新",
                "打卡行为分析报告已更新",
                bizId, "/checkin-analysis", Map.of(), Map.of()));
    }

    public static void notifyCheckinAnalysisFailed(UserServiceClient client, String difyKey,
                                                   String userId, String start, String end,
                                                   String errorSummary) {
        String summary = truncate(errorSummary, 80);
        String bizId = start + "_" + end;
        Map<String, Object> extra = Map.of("error_summary", summary);
        client.createMessage(difyKey, baseBody(userId, "checkin_analysis", "failed",
                "打卡分析失败",
                "分析失败：" + summary,
                bizId, "/checkin-analysis", Map.of(), extra));
    }

    private static Map<String, Object> baseBody(String userId, String messageType, String status,
                                                String title, String summary, String bizId,
                                                String linkPath, Map<String, Object> linkQuery,
                                                Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("messageType", messageType);
        body.put("status", status);
        body.put("title", title);
        body.put("summary", summary);
        body.put("bizId", bizId);
        body.put("linkPath", linkPath);
        body.put("linkQuery", linkQuery == null ? Map.of() : linkQuery);
        body.put("extra", extra == null ? Map.of() : extra);
        return body;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "请稍后重试";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
