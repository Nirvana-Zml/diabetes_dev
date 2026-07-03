package com.diabetes.common.dify;

import com.diabetes.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class DifyClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> requestBodies = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("阻塞工作流调用成功并构建默认请求体")
    void shouldRunWorkflowBlocking() throws Exception {
        startServer(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"answer\":\"ok\"}");
        });
        DifyClient client = new DifyClient(baseUrl() + "/", 5, objectMapper);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("text", "hello");
        inputs.put("flag", true);
        inputs.put("intValue", 1);
        inputs.put("longValue", 2L);
        inputs.put("numberValue", 1.5);
        inputs.put("objectValue", Map.of("a", 1));
        inputs.put("listValue", List.of(1, 2));
        inputs.put("unknownValue", new UnknownValue());
        inputs.put("nullValue", null);

        JsonNode result = client.runWorkflowBlocking("api-key", " ", inputs, " ");

        assertEquals("ok", result.get("answer").asText());
        JsonNode body = objectMapper.readTree(requestBodies.get(0));
        assertEquals("blocking", body.get("response_mode").asText());
        assertEquals("anonymous", body.get("user").asText());
        assertEquals("hello", body.path("inputs").path("text").asText());
        assertTrue(body.path("inputs").path("flag").asBoolean());
        assertEquals(1, body.path("inputs").path("intValue").asInt());
        assertEquals(2L, body.path("inputs").path("longValue").asLong());
        assertEquals(1.5, body.path("inputs").path("numberValue").asDouble());
        assertEquals(1, body.path("inputs").path("objectValue").path("a").asInt());
        assertEquals(2, body.path("inputs").path("listValue").size());
        assertTrue(body.path("inputs").path("unknownValue").asText().contains("value"));
        assertTrue(body.path("inputs").path("nullValue").isMissingNode());

        JsonNode overloadResult = client.runWorkflowBlocking("api-key", null, null);
        JsonNode overloadBody = objectMapper.readTree(requestBodies.get(1));
        assertEquals("ok", overloadResult.get("answer").asText());
        assertEquals("blocking", overloadBody.get("response_mode").asText());
        assertEquals("anonymous", overloadBody.get("user").asText());
        assertTrue(overloadBody.path("inputs").isObject());

        JsonNode nullModeResult = client.runWorkflowBlocking("api-key", "u_001", Map.of(), null);
        JsonNode nullModeBody = objectMapper.readTree(requestBodies.get(2));
        assertEquals("ok", nullModeResult.get("answer").asText());
        assertEquals("blocking", nullModeBody.get("response_mode").asText());

        JsonNode customModeResult = client.runWorkflowBlocking("api-key", "u_001", Map.of(), "streaming");
        JsonNode customModeBody = objectMapper.readTree(requestBodies.get(3));
        assertEquals("ok", customModeResult.get("answer").asText());
        assertEquals("streaming", customModeBody.get("response_mode").asText());
    }

    @Test
    @DisplayName("上传文件并调用带 files 的工作流")
    void shouldUploadFileAndRunWorkflowWithFiles() throws Exception {
        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/v1/files/upload")) {
                respond(exchange, 200, "{\"id\":\"file-123\",\"name\":\"voice.m4a\",\"mime_type\":\"audio/mp4\"}");
                return;
            }
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"data\":{\"status\":\"succeeded\",\"outputs\":{\"usertext\":\"你好\",\"valid\":true}}}");
        });
        DifyClient client = new DifyClient(baseUrl(), 5, objectMapper);

        JsonNode upload = client.uploadFile("api-key", "u1", "audio-bytes".getBytes(StandardCharsets.UTF_8), "voice.m4a", "audio/mp4");
        String fileId = upload.path("id").asText();
        assertEquals("file-123", fileId);

        List<Map<String, Object>> files = List.of(Map.of(
                "variable", "audio",
                "type", "audio",
                "transfer_method", "local_file",
                "upload_file_id", fileId
        ));
        JsonNode result = client.runWorkflowBlockingWithFiles(
                "api-key", "u1", Map.of("language", "zh-CN"), files, "blocking");

        assertEquals("succeeded", result.path("data").path("status").asText());
        assertEquals("你好", result.path("data").path("outputs").path("usertext").asText());
        JsonNode body = objectMapper.readTree(requestBodies.get(0));
        assertEquals("zh-CN", body.path("inputs").path("language").asText());
        assertEquals("audio", body.path("files").get(0).path("variable").asText());
        assertEquals(fileId, body.path("files").get(0).path("upload_file_id").asText());
    }

    @Test
    @DisplayName("工作流忽略无法序列化的自定义输入字段")
    void shouldSkipInputWhenSerializationFails() throws Exception {
        startServer(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"answer\":\"ok\"}");
        });
        DifyClient client = new DifyClient(baseUrl(), 5, new FailingUnknownValueMapper());

        JsonNode result = client.runWorkflowBlocking("api-key", "u_001", Map.of("unknown", new UnknownValue()));

        assertEquals("ok", result.get("answer").asText());
        JsonNode body = objectMapper.readTree(requestBodies.get(0));
        assertTrue(body.path("inputs").path("unknown").isMissingNode());
    }

    @Test
    @DisplayName("阻塞工作流调用处理 HTTP 错误和普通异常")
    void shouldHandleWorkflowErrors() throws Exception {
        startServer(exchange -> respond(exchange, 500, "bad"));
        DifyClient client = new DifyClient(baseUrl(), 5, objectMapper);

        BusinessException httpError = assertThrows(BusinessException.class,
                () -> client.runWorkflowBlocking("api-key", "u_001", Map.of()));
        assertEquals(500, httpError.getCode());
        assertTrue(httpError.getMessage().contains("Dify 工作流调用失败"));

        BusinessException connectError = assertThrows(BusinessException.class,
                () -> new DifyClient("http://127.0.0.1:1", 1, objectMapper)
                        .runWorkflowBlocking("api-key", "u_001", Map.of()));
        assertEquals(500, connectError.getCode());
        assertTrue(connectError.getMessage().startsWith("Dify 工作流调用失败"));
    }

    @Test
    @DisplayName("工作流使用三参数重载并处理日志序列化失败")
    void shouldUseWorkflowOverloadAndFallbackLogging() throws Exception {
        ObjectMapper mapper = spy(new ObjectMapper());
        when(mapper.writeValueAsString(any())).thenThrow(new RuntimeException("write failed"));
        DifyClient client = new DifyClient("http://127.0.0.1:1", 1, mapper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.runWorkflowBlocking("api-key", "u_001", null));

        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().startsWith("Dify 工作流调用失败"));
    }

    @Test
    @DisplayName("Webhook 调用成功、空响应和错误响应")
    void shouldTriggerWebhook() throws Exception {
        startServer(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("/empty")) {
                respond(exchange, 200, "");
            } else if (exchange.getRequestURI().getPath().endsWith("/blank")) {
                respond(exchange, 200, "   ");
            } else if (exchange.getRequestURI().getPath().endsWith("/nocontent")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            } else if (exchange.getRequestURI().getPath().endsWith("/error")) {
                respond(exchange, 400, "bad webhook");
            } else {
                respond(exchange, 200, "{\"ok\":true}");
            }
        });
        DifyClient client = new DifyClient(baseUrl(), 5, objectMapper);

        assertTrue(client.triggerWebhook("hook", Map.of("a", 1)).get("ok").asBoolean());
        assertTrue(client.triggerWebhook("/empty", Map.of()).isObject());
        assertTrue(client.triggerWebhook("/blank", Map.of()).isObject());
        assertTrue(client.triggerWebhook("/nocontent", Map.of()).isObject());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.triggerWebhook("/error", Map.of()));
        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("Dify Webhook 调用失败"));

        BusinessException connectError = assertThrows(BusinessException.class,
                () -> new DifyClient("http://127.0.0.1:1", 1, objectMapper)
                        .triggerWebhook("hook", Map.of()));
        assertEquals(500, connectError.getCode());
        assertTrue(connectError.getMessage().startsWith("Dify Webhook 调用失败"));
    }

    @Test
    @DisplayName("Webhook 响应为 null 时返回空对象")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnEmptyObjectWhenWebhookResponseNull() {
        DifyClient client = new DifyClient("http://localhost", 5, objectMapper);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/hook")).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
        when(bodySpec.bodyValue(Map.of())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.empty());
        org.springframework.test.util.ReflectionTestUtils.setField(client, "webClient", webClient);

        assertTrue(client.triggerWebhook("hook", Map.of()).isObject());
    }

    @Test
    @DisplayName("工作流和对话流式调用返回 Flux")
    void shouldRunStreamingCalls() throws Exception {
        startServer(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (exchange.getRequestURI().getPath().endsWith("/workflows/run")) {
                respond(exchange, 200, "data: line1\n\ndata: line2\n\n", "text/event-stream");
            } else {
                respond(exchange, 200, "line1\nline2", "text/plain");
            }
        });
        DifyClient client = new DifyClient(baseUrl(), 5, objectMapper);

        List<ServerSentEvent<String>> workflow = client.runWorkflowStreaming("api-key", "u_001", Map.of("a", 1))
                .collectList()
                .block();
        List<ServerSentEvent<String>> anonymousWorkflow = client.runWorkflowStreaming("api-key", " ", Map.of())
                .collectList()
                .block();
        List<ServerSentEvent<String>> nullUserWorkflow = client.runWorkflowStreaming("api-key", null, null)
                .collectList()
                .block();
        List<String> chat = client.runChatStreaming("api-key", " ", "hello", "c_001", Map.of("a", 1))
                .collectList()
                .block();
        List<String> chatWithoutConversation = client.runChatStreaming("api-key", "u_001", "hello", null, Map.of())
                .collectList()
                .block();

        assertNotNull(workflow);
        assertNotNull(anonymousWorkflow);
        assertNotNull(nullUserWorkflow);
        assertNotNull(chat);
        assertNotNull(chatWithoutConversation);
        assertEquals(List.of("line1", "line2"), workflow.stream().map(ServerSentEvent::data).toList());
        assertEquals(List.of("line1", "line2"), chat);
        JsonNode chatBody = objectMapper.readTree(requestBodies.get(3));
        JsonNode chatWithoutConversationBody = objectMapper.readTree(requestBodies.get(4));
        assertEquals("guest", chatBody.get("user").asText());
        assertEquals("c_001", chatBody.get("conversation_id").asText());
        assertFalse(chatWithoutConversationBody.has("conversation_id"));
    }

    @Test
    @DisplayName("工作流输入支持布尔 false 和浮点数值")
    void shouldSerializeBooleanFalseAndFloatInputs() throws Exception {
        startServer(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"answer\":\"ok\"}");
        });
        DifyClient client = new DifyClient(baseUrl(), 5, objectMapper);

        JsonNode result = client.runWorkflowBlocking("api-key", "u_001", Map.of(
                "enabled", false,
                "ratio", 0.25f));

        assertEquals("ok", result.get("answer").asText());
        JsonNode body = objectMapper.readTree(requestBodies.get(0));
        assertFalse(body.path("inputs").path("enabled").asBoolean());
        assertEquals(0.25, body.path("inputs").path("ratio").asDouble(), 0.001);
    }

    @Test
    @DisplayName("阻塞对话调用成功和失败")
    void shouldRunChatStreamingAsBlocking() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"answer\":\"ok\"}"));
        DifyClient client = new DifyClient(baseUrl(), 5, objectMapper);

        JsonNode result = client.runChatStreamingAsBlocking("api-key", "u_001", "hello", "", null);
        JsonNode guestResult = client.runChatStreamingAsBlocking("api-key", null, "hello", null, Map.of());

        assertEquals("ok", result.get("answer").asText());
        assertEquals("ok", guestResult.get("answer").asText());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new DifyClient("http://127.0.0.1:1", 1, objectMapper)
                        .runChatStreamingAsBlocking("api-key", "u_001", "hello", null, Map.of()));
        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().startsWith("Dify 对话调用失败"));
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
        respond(exchange, status, body, "application/json");
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    static class UnknownValue {
        public String getValue() {
            return "value";
        }
    }

    static class FailingUnknownValueMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
            if (value instanceof UnknownValue) {
                throw new com.fasterxml.jackson.core.JsonProcessingException("bad unknown") {};
            }
            return super.writeValueAsString(value);
        }
    }
}
