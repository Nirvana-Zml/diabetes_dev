package com.diabetes.common.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserMessageClientHelperTest {

    @Test
    @DisplayName("消息辅助类构建各类成功通知")
    void shouldBuildCompletedMessages() {
        UserServiceClient client = mock(UserServiceClient.class);

        UserMessageClientHelper.notifyRiskCompleted(client, "key", "u1", "risk1", "高风险", 88);
        UserMessageClientHelper.notifyPlanCompleted(client, "key", "u1", "plan1");
        UserMessageClientHelper.notifyConsultReply(client, "key", "u1", "session1", "张医生");
        UserMessageClientHelper.notifyCheckinAnalysisCompleted(client, "key", "u1", "2024-01-01", "2024-01-07");

        List<Map<String, Object>> bodies = capturedBodies(client);
        assertMessage(bodies.get(0), "u1", "risk_assess", "completed", "风险评估报告已生成",
                "高风险 · 88 分", "risk1", "/health-evaluation");
        assertEquals(Map.of("assessment_id", "risk1"), bodies.get(0).get("linkQuery"));
        assertEquals(Map.of("risk_level", "高风险", "risk_score", 88), bodies.get(0).get("extra"));

        assertMessage(bodies.get(1), "u1", "plan_generate", "completed", "健康方案已就绪",
                "您的个性化健康方案已就绪", "plan1", "/living-plans");
        assertMessage(bodies.get(2), "u1", "consult_reply", "completed", "医生已回复",
                "张医生 回复了您的咨询", "session1", "/consultation/chat");
        assertEquals(Map.of("session_id", "session1"), bodies.get(2).get("linkQuery"));
        assertEquals(Map.of("doctor_name", "张医生"), bodies.get(2).get("extra"));
        assertMessage(bodies.get(3), "u1", "checkin_analysis", "completed", "打卡分析已更新",
                "打卡行为分析报告已更新", "2024-01-01_2024-01-07", "/checkin-analysis");
    }

    @Test
    @DisplayName("消息辅助类构建失败通知并处理摘要")
    void shouldBuildFailedMessagesWithTruncatedSummary() {
        UserServiceClient client = mock(UserServiceClient.class);
        String longError = "x".repeat(90);

        UserMessageClientHelper.notifyRiskFailed(client, "key", "u1", null);
        UserMessageClientHelper.notifyPlanFailed(client, "key", "u1", "  ");
        UserMessageClientHelper.notifyPlanFailed(client, "key", "u1", "short");
        UserMessageClientHelper.notifyCheckinAnalysisFailed(client, "key", "u1",
                "2024-01-01", "2024-01-07", longError);

        List<Map<String, Object>> bodies = capturedBodies(client);
        assertMessage(bodies.get(0), "u1", "risk_assess", "failed", "风险评估失败",
                "风险评估失败：请稍后重试", null, "/health-evaluation");
        assertTrue(String.valueOf(bodies.get(0).get("bizId")).startsWith("risk_fail_"));
        assertEquals(Map.of("error_summary", "请稍后重试"), bodies.get(0).get("extra"));

        assertMessage(bodies.get(1), "u1", "plan_generate", "failed", "方案生成失败",
                "方案生成失败：请稍后重试", null, "/living-plans");
        assertTrue(String.valueOf(bodies.get(1).get("bizId")).startsWith("plan_fail_"));
        assertEquals(Map.of("error_summary", "请稍后重试"), bodies.get(1).get("extra"));

        assertEquals("方案生成失败：short", bodies.get(2).get("summary"));
        assertEquals(Map.of("error_summary", "short"), bodies.get(2).get("extra"));

        String truncated = "x".repeat(80);
        assertMessage(bodies.get(3), "u1", "checkin_analysis", "failed", "打卡分析失败",
                "分析失败：" + truncated, "2024-01-01_2024-01-07", "/checkin-analysis");
        assertEquals(Map.of("error_summary", truncated), bodies.get(3).get("extra"));
    }

    @Test
    @DisplayName("私有构造器和 baseBody 的 null 分支可覆盖")
    void shouldCoverPrivateConstructorAndNullDefaults() throws Exception {
        Constructor<UserMessageClientHelper> constructor = UserMessageClientHelper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());

        Method baseBody = UserMessageClientHelper.class.getDeclaredMethod("baseBody",
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, Map.class, Map.class);
        baseBody.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) baseBody.invoke(null,
                "u1", "type", "status", "title", "summary", "biz", "/path", null, null);

        assertEquals(Map.of(), body.get("linkQuery"));
        assertEquals(Map.of(), body.get("extra"));
    }

    private static List<Map<String, Object>> capturedBodies(UserServiceClient client) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client, atLeastOnce()).createMessage(eq("key"), captor.capture());
        return captor.getAllValues();
    }

    private static void assertMessage(Map<String, Object> body,
                                      String userId,
                                      String messageType,
                                      String status,
                                      String title,
                                      String summary,
                                      String bizId,
                                      String linkPath) {
        assertEquals(userId, body.get("userId"));
        assertEquals(messageType, body.get("messageType"));
        assertEquals(status, body.get("status"));
        assertEquals(title, body.get("title"));
        assertEquals(summary, body.get("summary"));
        if (bizId != null) {
            assertEquals(bizId, body.get("bizId"));
        }
        assertEquals(linkPath, body.get("linkPath"));
    }
}
