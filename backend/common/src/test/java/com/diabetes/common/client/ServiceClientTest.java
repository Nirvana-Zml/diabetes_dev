package com.diabetes.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        assertTrue(client.getLatestRiskAssessment("u_001", "key").isEmpty());
        assertTrue(client.getLatestRiskAssessment("bad-code", "key").isEmpty());
        assertTrue(client.getLatestRiskAssessment("missing-data", "key").isEmpty());
        assertTrue(client.getLatestRiskAssessment("array-data", "key").isEmpty());
        assertTrue(new HealthServiceClient("http://127.0.0.1:1", objectMapper)
                .getLatestHealthProfile("u_001", "key").isEmpty());
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
    @DisplayName("占位客户端返回空数据结构")
    void shouldReturnPlaceholderData() {
        ConsultationServiceClient consultation = new ConsultationServiceClient();
        HomeServiceClient home = new HomeServiceClient();

        assertTrue(consultation.listAiDoctors("内分泌", "糖尿病").isEmpty());
        assertEquals(Optional.empty(), consultation.getActiveSession("u_001"));
        assertTrue(home.getHomeContent().banners().isEmpty());
        assertTrue(home.getHomeContent().categories().isEmpty());
        assertTrue(home.getHomeContent().videos().isEmpty());
        assertTrue(home.getRecommend(1, 10).articles().isEmpty());
        assertEquals(0, home.getRecommend(1, 10).total());
        assertEquals("d1", new ConsultationServiceClient.AiDoctorPlaceholder("d1", "医生", "内分泌").doctorId());
        assertEquals("s1", new ConsultationServiceClient.SessionPlaceholder("s1", "d1", "active").sessionId());
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
