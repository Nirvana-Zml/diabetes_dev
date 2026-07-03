package com.diabetes.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ClientHelperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("AuditServiceClient 写入审计日志")
    void shouldPostAuditLog() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedKey = new AtomicReference<>();
        startServer(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/audit/logs")) {
                capturedKey.set(exchange.getRequestHeaders().getFirst("X-Dify-Key"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"ok\":true}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        AuditServiceClient client = new AuditServiceClient(baseUrl(), "audit-key");
        Map<String, Object> detail = Map.of("field", "value");
        client.log("u_001", "LOGIN", "user", detail, "127.0.0.1", "Mozilla/5.0", 1);

        JsonNode body = objectMapper.readTree(capturedBody.get());
        assertEquals("audit-key", capturedKey.get());
        assertEquals("u_001", body.get("userId").asText());
        assertEquals("LOGIN", body.get("action").asText());
        assertEquals("user", body.get("resource").asText());
        assertEquals(1, body.get("result").asInt());
        assertEquals("value", body.get("detail").get("field").asText());
        assertEquals("127.0.0.1", body.get("ipAddress").asText());
        assertEquals("Mozilla/5.0", body.get("userAgent").asText());
    }

    @Test
    @DisplayName("AuditServiceClient 跳过空可选字段且失败不抛异常")
    void shouldSkipOptionalAuditFieldsAndIgnoreFailures() {
        AuditServiceClient client = new AuditServiceClient("http://127.0.0.1:1", null);
        assertDoesNotThrow(() -> client.log("u_001", "LOGOUT", "user", Map.of(),
                "  ", "  ", 0));
        assertDoesNotThrow(() -> client.log("u_001", "LOGOUT", "user", null,
                null, null, 0));
    }

    @Test
    @DisplayName("InterventionClientHelper 忽略无效参数")
    void shouldIgnoreInvalidInterventionParams() {
        UserServiceClient client = new UserServiceClient("http://127.0.0.1:1", objectMapper);
        assertDoesNotThrow(() -> InterventionClientHelper.triggerEvaluate(null, "key", "u1", "t", Map.of()));
        assertDoesNotThrow(() -> InterventionClientHelper.triggerEvaluate(client, "key", null, "t", Map.of()));
        assertDoesNotThrow(() -> InterventionClientHelper.triggerEvaluate(client, "key", "  ", "t", Map.of()));
    }

    @Test
    @DisplayName("InterventionClientHelper 触发评估")
    void shouldTriggerInterventionEvaluate() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        startServer(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/interventions/evaluate")) {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"ok\":true}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        UserServiceClient client = new UserServiceClient(baseUrl(), objectMapper);
        InterventionClientHelper.triggerEvaluate(client, "dify-key", "u_001", null, null);

        JsonNode body = objectMapper.readTree(capturedBody.get());
        assertEquals("u_001", body.get("userId").asText());
        assertEquals("manual_refresh", body.get("trigger").asText());
        assertTrue(body.get("context").isObject());
        assertTrue(body.get("context").isEmpty());

        InterventionClientHelper.triggerEvaluate(client, "dify-key", "u_002", "plan_ready",
                Map.of("planId", "p1"));
        JsonNode body2 = objectMapper.readTree(capturedBody.get());
        assertEquals("plan_ready", body2.get("trigger").asText());
        assertEquals("p1", body2.get("context").get("planId").asText());
    }

    @Test
    @DisplayName("UserMessageClientHelper 发送各类用户消息")
    void shouldNotifyUserMessages() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        startServer(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/messages")) {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, 200, "{\"ok\":true}");
            } else {
                respond(exchange, 404, "{}");
            }
        });
        UserServiceClient client = new UserServiceClient(baseUrl(), objectMapper);

        UserMessageClientHelper.notifyRiskCompleted(client, "key", "u1", "a1", "中风险", 72);
        assertMessageType(capturedBody.get(), "risk_assess", "completed", "风险评估报告已生成");

        UserMessageClientHelper.notifyRiskFailed(client, "key", "u1", "  ");
        JsonNode riskFailed = objectMapper.readTree(capturedBody.get());
        assertEquals("risk_assess", riskFailed.get("messageType").asText());
        assertEquals("failed", riskFailed.get("status").asText());
        assertTrue(riskFailed.get("summary").asText().contains("请稍后重试"));

        UserMessageClientHelper.notifyRiskFailed(client, "key", "u1", "x".repeat(120));
        JsonNode longFailed = objectMapper.readTree(capturedBody.get());
        assertEquals(80, longFailed.get("extra").get("error_summary").asText().length());

        UserMessageClientHelper.notifyPlanCompleted(client, "key", "u1", "p1");
        assertMessageType(capturedBody.get(), "plan_generate", "completed", "健康方案已就绪");

        UserMessageClientHelper.notifyPlanFailed(client, "key", "u1", "timeout");
        assertMessageType(capturedBody.get(), "plan_generate", "failed", "方案生成失败");

        UserMessageClientHelper.notifyConsultReply(client, "key", "u1", "s1", "张医生");
        JsonNode consult = objectMapper.readTree(capturedBody.get());
        assertEquals("consult_reply", consult.get("messageType").asText());
        assertEquals("s1", consult.get("linkQuery").get("session_id").asText());
        assertEquals("张医生", consult.get("extra").get("doctor_name").asText());

        UserMessageClientHelper.notifyCheckinAnalysisCompleted(client, "key", "u1", "2026-01-01", "2026-01-07");
        assertEquals("checkin_analysis", objectMapper.readTree(capturedBody.get()).get("messageType").asText());

        UserMessageClientHelper.notifyCheckinAnalysisFailed(client, "key", "u1", "2026-01-01", "2026-01-07", "err");
        assertEquals("failed", objectMapper.readTree(capturedBody.get()).get("status").asText());

        UserMessageClientHelper.notifyHealthAlert(client, "key", "u1", "p1", "high",
                "血糖偏高", "空腹血糖 8.2", "请控制饮食");
        JsonNode alert = objectMapper.readTree(capturedBody.get());
        assertEquals("health_alert", alert.get("messageType").asText());
        assertEquals("请控制饮食", alert.get("extra").get("suggestion").asText());

        UserMessageClientHelper.notifyHealthAlert(client, "key", "u1", "p1", "low",
                "提醒", "摘要", "  ");
        JsonNode alertNoSuggestion = objectMapper.readTree(capturedBody.get());
        assertFalse(alertNoSuggestion.get("extra").has("suggestion"));

        UserMessageClientHelper.notifyHealthAlert(client, "key", "u1", "p1", "low",
                "提醒", "摘要", null);
        assertFalse(objectMapper.readTree(capturedBody.get()).get("extra").has("suggestion"));
    }

    @Test
    @DisplayName("UserMessageClientHelper baseBody 处理 null 集合")
    void shouldDefaultNullCollectionsInBaseBody() throws Exception {
        Method baseBody = UserMessageClientHelper.class.getDeclaredMethod("baseBody",
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, Map.class, Map.class);
        baseBody.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) baseBody.invoke(null,
                "u1", "type", "status", "title", "summary", "biz",
                "/path", null, null);

        assertTrue(body.get("linkQuery") instanceof Map);
        assertTrue(((Map<?, ?>) body.get("linkQuery")).isEmpty());
        assertTrue(body.get("extra") instanceof Map);
        assertTrue(((Map<?, ?>) body.get("extra")).isEmpty());
    }

    @Test
    @DisplayName("UserMessageClientHelper truncate 处理边界值")
    void shouldTruncateMessageErrorSummary() throws Exception {
        Method truncate = UserMessageClientHelper.class.getDeclaredMethod("truncate", String.class, int.class);
        truncate.setAccessible(true);

        assertEquals("请稍后重试", truncate.invoke(null, null, 80));
        assertEquals("请稍后重试", truncate.invoke(null, "  ", 80));
        assertEquals("short", truncate.invoke(null, " short ", 80));
        assertEquals("a".repeat(80), truncate.invoke(null, "a".repeat(120), 80));
    }

    private void assertMessageType(String json, String messageType, String status, String title) throws Exception {
        JsonNode body = objectMapper.readTree(json);
        assertEquals(messageType, body.get("messageType").asText());
        assertEquals(status, body.get("status").asText());
        assertEquals(title, body.get("title").asText());
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
