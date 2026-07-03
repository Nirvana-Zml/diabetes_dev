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
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

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

    /**
     * 上传文件至 Dify（POST /v1/files/upload），返回完整响应（含 id、mime_type）。
     */
    public JsonNode uploadFile(String apiKey, String userId, byte[] fileBytes, String filename, String mimeType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String uid = userId == null || userId.isBlank() ? "anonymous" : userId;
        String safeName = filename == null || filename.isBlank() ? "voice.webm" : filename;
        String contentType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        }).contentType(MediaType.parseMediaType(contentType));
        builder.part("user", uid);

        try {
            String response = webClient.post()
                    .uri("/v1/files/upload")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(timeout);
            log.info("Dify 文件上传响应: {}", response);
            JsonNode node = objectMapper.readTree(response);
            String id = node.path("id").asText("");
            if (id.isBlank()) {
                throw new BusinessException(500, "Dify 文件上传未返回文件 ID");
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Dify 文件上传 HTTP 错误 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(500, "Dify 文件上传失败(" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Dify 文件上传异常: {}", e.getMessage(), e);
            throw new BusinessException(500, "Dify 文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 调用带顶层 files 数组的 Dify Workflow（如 STT 工作流 userinput.files）。
     */
    public JsonNode runWorkflowBlockingWithFiles(String apiKey, String userId, Map<String, Object> inputs,
                                                 List<Map<String, Object>> files, String responseMode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("response_mode", responseMode == null || responseMode.isBlank() ? "blocking" : responseMode);
        body.put("user", userId == null || userId.isBlank() ? "anonymous" : userId);
        body.set("inputs", toWorkflowInputsNode(inputs != null ? inputs : Map.of()));
        body.set("files", objectMapper.valueToTree(files != null ? files : List.of()));

        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            log.info("调用 Dify 工作流(含文件): user={}, body={}", body.get("user").asText(), bodyJson);
        } catch (Exception e) {
            log.info("调用 Dify 工作流(含文件): user={}", body.get("user").asText());
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
            throw toWorkflowBusinessException(e);
        } catch (Exception e) {
            log.error("Dify 工作流调用异常: {}", e.getMessage(), e);
            throw new BusinessException(500, "Dify 工作流调用失败: " + e.getMessage());
        }
    }

    private BusinessException toWorkflowBusinessException(WebClientResponseException e) {
        int code = e.getStatusCode().is4xxClientError() ? 400 : 500;
        String message = extractDifyErrorMessage(e.getResponseBodyAsString());
        if (message.isBlank()) {
            message = "Dify 工作流调用失败(" + e.getStatusCode() + ")";
        }
        return new BusinessException(code, message);
    }

    private String extractDifyErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = node.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
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
