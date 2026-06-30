package com.diabetes.common.client;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("UserServiceClient 获取用户资料和概览")
    void shouldFetchUserServiceData() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.contains("missing")) {
                respond(exchange, 200, "{}");
            } else if (path.endsWith("/profile")) {
                respond(exchange, 200, "{\"data\":{\"username\":\"test\"}}");
            } else if (path.endsWith("/overview")) {
                respond(exchange, 200, "{\"data\":null}");
            } else {
                respond(exchange, 200, "{\"data\":{\"ok\":true}}");
            }
        });
        UserServiceClient client = new UserServiceClient(baseUrl(), objectMapper);

        assertEquals("test", client.getUserProfile("u_001", null).get("username"));
        assertTrue(client.getUserOverview("u_001", "key").isEmpty());
        assertTrue(client.getUserProfile("missing", "key").isEmpty());
        assertTrue(new UserServiceClient("http://127.0.0.1:1", objectMapper)
                .getUserProfile("u_001", "key").isEmpty());
    }

    @Test
    @DisplayName("HealthServiceClient 获取健康资料和风险评估")
    void shouldFetchHealthServiceData() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/latest-record")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"height\":170}}");
            } else if (path.endsWith("/risk-history")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"total\":2,\"items\":[{\"id\":\"r1\"}]}}");
            } else if (path.contains("bad-code")) {
                respond(exchange, 200, "{\"code\":500,\"message\":\"bad\",\"data\":{\"risk\":\"high\"}}");
            } else if (path.contains("missing-data")) {
                respond(exchange, 200, "{\"code\":200}");
            } else if (path.contains("array-data")) {
                respond(exchange, 200, "{\"code\":200,\"data\":[]}");
            } else if (path.endsWith("/latest-assessment")) {
                respond(exchange, 200, "{\"code\":200,\"data\":null}");
            } else {
                respond(exchange, 200, "{\"code\":200,\"data\":[]}");
            }
        });
        HealthServiceClient client = new HealthServiceClient(baseUrl(), objectMapper);

        assertEquals(170, client.getLatestHealthProfile("u_001", null).get("height"));
        assertEquals(2, client.getRiskHistory("u_001", "key", 1, 10).get("total"));
        assertTrue(client.getLatestRiskAssessment("u_001", "key").isEmpty());
        assertTrue(client.getLatestRiskAssessment("bad-code", "key").isEmpty());
        assertTrue(client.getLatestRiskAssessment("missing-data", "key").isEmpty());
        assertTrue(client.getLatestRiskAssessment("array-data", "key").isEmpty());
        assertTrue(new HealthServiceClient("http://127.0.0.1:1", objectMapper)
                .getLatestHealthProfile("u_001", "key").isEmpty());
        assertTrue(new HealthServiceClient("http://127.0.0.1:1", objectMapper)
                .getRiskHistory("u_001", "key", 1, 10).isEmpty());
    }

    @Test
    @DisplayName("CheckinServiceClient 获取近期打卡和摘要")
    void shouldFetchCheckinData() throws Exception {
        startServer(exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("days=7")) {
                respond(exchange, 200, "{\"data\":[{\"id\":\"c1\"}]}");
            } else if (query != null && query.contains("days=2")) {
                respond(exchange, 200, "{}");
            } else {
                respond(exchange, 200, "{\"data\":{}}");
            }
        });
        CheckinServiceClient client = new CheckinServiceClient(baseUrl(), objectMapper);

        List<Map<String, Object>> records = client.getRecentCheckins("u_001", null, 7);
        Map<String, Object> summary = client.getCheckinSummary("u_001", "key", 7);

        assertEquals("c1", records.get(0).get("id"));
        assertEquals(7, summary.get("recent_days"));
        assertEquals(records, summary.get("records"));
        assertTrue(client.getRecentCheckins("u_001", "key", 1).isEmpty());
        assertTrue(client.getRecentCheckins("u_001", "key", 2).isEmpty());
        assertTrue(new CheckinServiceClient("http://127.0.0.1:1", objectMapper)
                .getRecentCheckins("u_001", "key", 7).isEmpty());
    }

    @Test
    @DisplayName("ConsultationServiceClient 和 HomeServiceClient 调用内部接口")
    void shouldFetchConsultationAndHomeData() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            if (path.contains("/consultation/user/ok/sessions")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"total\":1,\"sessions\":[{\"id\":\"s1\"}]}}");
            } else if (path.contains("/consultation/user/default-code/sessions")) {
                respond(exchange, 200, "{\"data\":{\"total\":2}}");
            } else if (path.contains("/consultation/user/bad-code/sessions")) {
                respond(exchange, 200, "{\"code\":500,\"data\":{\"total\":1}}");
            } else if (path.contains("/consultation/user/no-data/sessions")) {
                respond(exchange, 200, "{\"code\":200}");
            } else if (path.contains("/consultation/user/array-data/sessions")) {
                respond(exchange, 200, "{\"code\":200,\"data\":[]}");
            } else if (path.contains("/consultation/user/null-data/sessions")) {
                respond(exchange, 200, "{\"code\":200,\"data\":null}");
            } else if (path.endsWith("/knowledge/search") && query != null && query.contains("query=ctx")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"knowledgeContext\":\"context\",\"count\":1}}");
            } else if (path.endsWith("/knowledge/search") && query != null && query.contains("query=snake")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"knowledge_context\":\"snake context\",\"count\":1}}");
            } else if (path.endsWith("/knowledge/search") && query != null && query.contains("query=empty")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"knowledgeContext\":\"\",\"count\":0}}");
            } else if (path.endsWith("/knowledge/search") && query != null && query.contains("query=bad")) {
                respond(exchange, 200, "{\"code\":500,\"message\":\"bad\",\"data\":{}}");
            } else if (path.endsWith("/knowledge/search") && query != null && query.contains("query=long")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"knowledgeContext\":\"\",\"count\":0}}");
            } else if (path.endsWith("/knowledge/search") && query != null && query.contains("query=default-code")) {
                respond(exchange, 200, "{\"data\":{\"knowledgeContext\":\"default\",\"count\":1}}");
            } else {
                respond(exchange, 200, "{}");
            }
        });
        ConsultationServiceClient consultation = new ConsultationServiceClient(baseUrl(), objectMapper);
        HomeServiceClient home = new HomeServiceClient(baseUrl(), objectMapper);

        assertEquals(1, consultation.listSessions("ok", null, 1, 10).get("total"));
        assertEquals(2, consultation.listSessions("default-code", "key", 1, 10).get("total"));
        assertTrue(consultation.listSessions("bad-code", "key", 1, 10).isEmpty());
        assertTrue(consultation.listSessions("no-data", "key", 1, 10).isEmpty());
        assertTrue(consultation.listSessions("array-data", "key", 1, 10).isEmpty());
        assertTrue(consultation.listSessions("null-data", "key", 1, 10).isEmpty());
        assertTrue(new ConsultationServiceClient("http://127.0.0.1:1", objectMapper)
                .listSessions("ok", "key", 1, 10).isEmpty());

        assertEquals("context", home.searchKnowledgeContext("ctx", 3, null));
        assertEquals("snake context", home.searchKnowledgeContext("snake", 3, "key"));
        assertEquals("", home.searchKnowledgeContext(" ", 3, "key"));
        assertEquals("", home.searchKnowledgeContext(null, 3, "key"));
        assertEquals("", home.searchKnowledgeContext("empty", 3, "key"));
        assertEquals("", home.searchKnowledgeContext("bad", 3, "key"));
        assertEquals("", home.searchKnowledgeContext("long".repeat(30), 3, "key"));
        assertEquals("default", home.searchKnowledgeContext("default-code", 3, "key"));
        assertEquals("", new HomeServiceClient("http://127.0.0.1:1", objectMapper)
                .searchKnowledgeContext("ctx", 3, "key"));
    }

    @Test
    @DisplayName("HomeServiceClient truncate 处理 null 和短文本")
    void shouldTruncateKnowledgeQuery() throws Exception {
        Method truncate = HomeServiceClient.class.getDeclaredMethod("truncate", String.class, int.class);
        truncate.setAccessible(true);

        assertEquals("", truncate.invoke(null, null, 80));
        assertEquals("short", truncate.invoke(null, "short", 80));
        assertEquals("a".repeat(80) + "...", truncate.invoke(null, "a".repeat(120), 80));
    }

    @Test
    @DisplayName("PlanServiceClient 获取方案历史和最新方案")
    void shouldFetchPlanServiceData() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.contains("/user/ok/history")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"total\":3,\"items\":[{\"id\":\"p1\"}]}}");
            } else if (path.contains("/user/ok/latest")) {
                respond(exchange, 200, "{\"code\":200,\"data\":{\"id\":\"p-latest\"}}");
            } else if (path.contains("/user/array/latest")) {
                respond(exchange, 200, "{\"code\":200,\"data\":[{\"id\":\"p-array\"}]}");
            } else if (path.contains("/user/bad-code/latest")) {
                respond(exchange, 200, "{\"code\":500,\"message\":\"bad\"}");
            } else if (path.contains("/user/missing-data/latest")) {
                respond(exchange, 200, "{\"code\":200}");
            } else if (path.contains("/user/null-data/latest")) {
                respond(exchange, 200, "{\"code\":200,\"data\":null}");
            } else {
                respond(exchange, 200, "{}");
            }
        });
        PlanServiceClient client = new PlanServiceClient(baseUrl(), objectMapper);

        assertEquals(3, client.getPlanHistory("ok", null, 1, 10).get("total"));
        assertEquals("p-latest", client.getLatestPlan("ok", "key").get("id"));
        assertTrue(client.getLatestPlan("array", "key").containsKey("items"));
        assertTrue(client.getLatestPlan("bad-code", "key").isEmpty());
        assertTrue(client.getLatestPlan("missing-data", "key").isEmpty());
        assertTrue(client.getLatestPlan("null-data", "key").isEmpty());
        assertTrue(new PlanServiceClient("http://127.0.0.1:1", objectMapper)
                .getLatestPlan("ok", "key").isEmpty());
        assertTrue(new PlanServiceClient("http://127.0.0.1:1", objectMapper)
                .getPlanHistory("ok", "key", 1, 10).isEmpty());
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
