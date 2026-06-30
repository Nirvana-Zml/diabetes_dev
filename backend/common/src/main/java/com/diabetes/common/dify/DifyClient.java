package com.diabetes.common.dify;

import com.diabetes.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;

@Component
public class DifyClient {

    private static final Logger log = LoggerFactory.getLogger(DifyClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final String baseUrl;

    public DifyClient(@Value("${dify.base-url:http://localhost}") String baseUrl,
                      @Value("${dify.timeout-seconds:120}") long timeoutSeconds,
                      ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.webClient = WebClient.builder().baseUrl(this.baseUrl).build();
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * 调用 Dify Workflow Run API（POST /v1/workflows/run）
     */
    public JsonNode runWorkflowBlocking(String apiKey, String userId, Map<String, Object> inputs) {
        return runWorkflowBlocking(apiKey, userId, inputs, "blocking");
    }

    public JsonNode runWorkflowBlocking(String apiKey, String userId, Map<String, Object> inputs, String responseMode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("response_mode", responseMode == null || responseMode.isBlank() ? "blocking" : responseMode);
        body.put("user", userId == null || userId.isBlank() ? "anonymous" : userId);
        body.set("inputs", toWorkflowInputsNode(inputs != null ? inputs : Map.of()));

        String url = baseUrl + "/v1/workflows/run";
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            log.info("调用 Dify 工作流: url={}, user={}, response_mode={}, body={}",
                    url, body.get("user").asText(), body.get("response_mode").asText(), bodyJson);
        } catch (Exception e) {
            log.info("调用 Dify 工作流: url={}, user={}, response_mode={}",
                    url, body.get("user").asText(), body.get("response_mode").asText());
        }

        try {
            String response = webClient.post()
                    .uri("/v1/workflows/run")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout);
            log.info("Dify 工作流响应: {}", response);
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            log.error("Dify 工作流 HTTP 错误 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(500, "Dify 工作流调用失败(" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Dify 工作流调用异常: {}", e.getMessage(), e);
            throw new BusinessException(500, "Dify 工作流调用失败: " + e.getMessage());
        }
    }

    /** Dify Webhook 触发器（POST /triggers/webhook/{id}） */
    public JsonNode triggerWebhook(String webhookPath, Map<String, Object> payload) {
        String path = webhookPath.startsWith("/") ? webhookPath : "/" + webhookPath;
        String url = baseUrl + path;
        log.info("调用 Dify Webhook: url={}", url);

        try {
            String response = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout);
            log.info("Dify Webhook 响应: {}", response == null || response.isBlank() ? "(empty)" : response);
            if (response == null || response.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response);
        } catch (WebClientResponseException e) {
            log.error("Dify Webhook HTTP 错误 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(500, "Dify Webhook 调用失败(" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Dify Webhook 调用异常: {}", e.getMessage(), e);
            throw new BusinessException(500, "Dify Webhook 调用失败: " + e.getMessage());
        }
    }

    /**
     * Dify Workflow 流式 SSE（POST /v1/workflows/run，response_mode=streaming）。
     * 使用 {@link ServerSentEvent} 解析，避免 bodyToFlux(String) 在 UTF-8 多字节边界拆包导致 JSON 解析失败。
     */
    public Flux<ServerSentEvent<String>> runWorkflowStreaming(String apiKey, String userId, Map<String, Object> inputs) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("response_mode", "streaming");
        body.put("user", userId == null || userId.isBlank() ? "anonymous" : userId);
        body.set("inputs", toWorkflowInputsNode(inputs != null ? inputs : Map.of()));

        return webClient.post()
                .uri("/v1/workflows/run")
                .header("Authorization", "Bearer " + apiKey)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});
    }

    public JsonNode runChatStreamingAsBlocking(String apiKey, String userId, String query,
                                               String conversationId, Map<String, Object> inputs) {
        ObjectNode body = buildChatBody(userId, query, conversationId, inputs, "blocking");

        try {
            String response = webClient.post()
                    .uri("/v1/chat-messages")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new BusinessException(500, "Dify 对话调用失败: " + e.getMessage());
        }
    }

    /**
     * Dify Chatbot 流式 SSE（POST /v1/chat-messages，response_mode=streaming）。
     * 返回原始 SSE 文本行流，由调用方解析 data: {...} 事件。
     */
    public Flux<String> runChatStreaming(String apiKey, String userId, String query,
                                         String conversationId, Map<String, Object> inputs) {
        ObjectNode body = buildChatBody(userId, query, conversationId, inputs, "streaming");
        return webClient.post()
                .uri("/v1/chat-messages")
                .header("Authorization", "Bearer " + apiKey)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class);
    }

    private JsonNode toWorkflowInputsNode(Map<String, Object> inputs) {
        ObjectNode node = objectMapper.createObjectNode();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String key = entry.getKey();
            if (value instanceof String) {
                node.put(key, (String) value);
            } else if (value instanceof Boolean) {
                node.put(key, (Boolean) value);
            } else if (value instanceof Integer) {
                node.put(key, (Integer) value);
            } else if (value instanceof Long) {
                node.put(key, (Long) value);
            } else if (value instanceof Number) {
                node.put(key, ((Number) value).doubleValue());
            } else if (value instanceof Map || value instanceof Collection) {
                // Dify 开始节点为 object/array 类型时需传 JSON 对象，不能写成字符串
                node.set(key, objectMapper.valueToTree(value));
            } else {
                try {
                    node.put(key, objectMapper.writeValueAsString(value));
                } catch (Exception e) {
                    log.warn("Dify inputs 字段 {} 序列化失败: {}", key, e.getMessage());
                }
            }
        }
        return node;
    }

    private ObjectNode buildChatBody(String userId, String query, String conversationId,
                                     Map<String, Object> inputs, String responseMode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query);
        body.put("response_mode", responseMode);
        body.put("user", userId == null || userId.isBlank() ? "guest" : userId);
        if (conversationId != null && !conversationId.isBlank()) {
            body.put("conversation_id", conversationId);
        }
        body.set("inputs", objectMapper.valueToTree(inputs != null ? inputs : Map.of()));
        return body;
    }
}
