package com.diabetes.home.service;

import com.diabetes.common.dify.DifyClient;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dify.DifyQaChatContract;
import com.diabetes.home.knowledge.DocumentChunk;
import com.diabetes.home.knowledge.KnowledgeRetrieval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think[^>]*>[\\s\\S]*?</think[^>]*>|<think>[\\s\\S]*?</think>",
            Pattern.CASE_INSENSITIVE);

    private final DifyClient difyClient;
    private final KnowledgeRetrieval knowledgeRetrieval;
    private final QaChatProperties qaChatProperties;
    private final ObjectMapper objectMapper;
    private final String difyBaseUrl;

    public AIChatService(DifyClient difyClient,
                         KnowledgeRetrieval knowledgeRetrieval,
                         QaChatProperties qaChatProperties,
                         ObjectMapper objectMapper,
                         @Value("${dify.base-url:http://localhost}") String difyBaseUrl) {
        this.difyClient = difyClient;
        this.knowledgeRetrieval = knowledgeRetrieval;
        this.qaChatProperties = qaChatProperties;
        this.objectMapper = objectMapper;
        this.difyBaseUrl = difyBaseUrl;
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyQaChatContract.workflowSpec(difyBaseUrl, qaChatProperties.getApiKey());
    }

    public SseEmitter processQuestion(String query, String conversationId, String userId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        String apiKey = qaChatProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            sendError(emitter, "科普问答服务未配置");
            return emitter;
        }

        String uid = userId == null || userId.isBlank() ? "guest" : userId;
        List<DocumentChunk> chunks = knowledgeRetrieval.semanticSearch(query, 5);
        String knowledgeContext = knowledgeRetrieval.buildKnowledgeContext(chunks);
        List<String> sources = knowledgeRetrieval.extractSources(chunks);
        Map<String, Object> inputs = DifyQaChatContract.buildWorkflowInputs(query, knowledgeContext);

        log.info("科普问答: user={}, convId={}, queryLen={}, hits={}",
                uid, conversationId, query.length(), chunks.size());

        AtomicReference<String> convRef = new AtomicReference<>(conversationId);
        AtomicReference<Boolean> endSent = new AtomicReference<>(false);
        AtomicReference<Boolean> textChunkSent = new AtomicReference<>(false);

        Flux<ServerSentEvent<String>> stream = difyClient.runWorkflowStreaming(apiKey, uid, inputs);
        stream.subscribe(
                sse -> {
                    String data = sse.data();
                    if (data == null || data.isBlank()) {
                        return;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        dispatchDifyEvent(emitter, node, convRef, sources, endSent, textChunkSent);
                    } catch (Exception e) {
                        log.debug("忽略无法解析的 Dify SSE 数据: {}", data);
                    }
                },
                err -> {
                    log.error("Dify 科普问答流式失败: {}", err.getMessage());
                    sendError(emitter, "问答服务暂时不可用，请稍后重试");
                },
                () -> {
                    if (!Boolean.TRUE.equals(endSent.get())) {
                        try {
                            sendMessageEnd(emitter, convRef.get(), sources, null);
                        } catch (IOException e) {
                            log.debug("补发 message_end 失败: {}", e.getMessage());
                        }
                    }
                    emitter.complete();
                }
        );
        return emitter;
    }

    private void dispatchDifyEvent(SseEmitter emitter, JsonNode node,
                                   AtomicReference<String> convRef, List<String> sources,
                                   AtomicReference<Boolean> endSent,
                                   AtomicReference<Boolean> textChunkSent) throws IOException {
        String event = node.path("event").asText("");
        if (node.has("conversation_id") && !node.path("conversation_id").asText("").isBlank()) {
            convRef.set(node.path("conversation_id").asText());
        }

        if ("text_chunk".equals(event)) {
            if (!shouldForwardTextChunk(node)) {
                return;
            }
            String text = node.path("data").path(DifyQaChatContract.OUTPUT_TEXT).asText("");
            if (!text.isEmpty()) {
                textChunkSent.set(true);
                sendTextChunk(emitter, convRef.get(), text);
            }
            return;
        }

        if ("node_finished".equals(event)) {
            handleNodeFinished(emitter, node, convRef, textChunkSent);
            return;
        }

        if ("workflow_finished".equals(event)) {
            handleWorkflowFinished(emitter, node, convRef, endSent, textChunkSent);
            return;
        }

        if ("message".equals(event) || ("agent_message".equals(event) && node.has("answer"))) {
            String answer = node.path("answer").asText("");
            if (!answer.isEmpty()) {
                textChunkSent.set(true);
                sendTextChunk(emitter, convRef.get(), answer);
            }
            return;
        }

        if ("message_end".equals(event) || "agent_message_end".equals(event)) {
            JsonNode usage = node.path("metadata").path("usage");
            sendMessageEnd(emitter, convRef.get(), sources, usage.isMissingNode() ? null : usage);
            endSent.set(true);
            return;
        }

        if ("error".equals(event)) {
            sendError(emitter, node.path("message").asText("问答失败"));
        }
    }

    private boolean shouldForwardTextChunk(JsonNode node) {
        JsonNode selector = node.path("data").path("from_variable_selector");
        if (!selector.isArray() || selector.isEmpty()) {
            return true;
        }
        String var = selector.get(selector.size() - 1).asText("");
        return !"valid".equals(var) && !"message".equals(var)
                && !"error_message".equals(var) && !"error_type".equals(var);
    }

    private void handleNodeFinished(SseEmitter emitter, JsonNode node,
                                    AtomicReference<String> convRef,
                                    AtomicReference<Boolean> textChunkSent) throws IOException {
        String nodeType = node.path("data").path("node_type").asText("");
        if (!"llm".equals(nodeType)) {
            return;
        }
        String text = node.path("data").path("outputs").path(DifyQaChatContract.OUTPUT_TEXT).asText("");
        if (!text.isEmpty()) {
            textChunkSent.set(true);
            sendTextChunk(emitter, convRef.get(), text);
        }
    }

    private void handleWorkflowFinished(SseEmitter emitter, JsonNode node,
                                        AtomicReference<String> convRef,
                                        AtomicReference<Boolean> endSent,
                                        AtomicReference<Boolean> textChunkSent) throws IOException {
        JsonNode outputs = node.path("data").path("outputs");
        if (outputs.isMissingNode() || outputs.isNull()) {
            return;
        }

        boolean valid = outputs.path(DifyQaChatContract.OUTPUT_VALID).asBoolean(true);
        if (!valid) {
            String errorMessage = outputs.path(DifyQaChatContract.OUTPUT_ERROR_MESSAGE).asText("");
            if (errorMessage.isBlank() || "null".equalsIgnoreCase(errorMessage)) {
                errorMessage = outputs.path(DifyQaChatContract.OUTPUT_MESSAGE).asText("输入校验失败");
            }
            log.warn("科普问答校验失败: type={}, msg={}",
                    outputs.path(DifyQaChatContract.OUTPUT_ERROR_TYPE).asText(""),
                    errorMessage);
            sendError(emitter, errorMessage);
            endSent.set(true);
            return;
        }

        if (!Boolean.TRUE.equals(textChunkSent.get())) {
            String text = outputs.path(DifyQaChatContract.OUTPUT_TEXT).asText("");
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                textChunkSent.set(true);
                sendTextChunk(emitter, convRef.get(), text);
            }
        }
    }

    private void sendTextChunk(SseEmitter emitter, String conversationId, String content) throws IOException {
        String sanitized = sanitizeModelText(content);
        if (sanitized.isEmpty()) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "text");
        payload.put("content", sanitized);
        payload.put("conversationId", conversationId == null ? "" : conversationId);
        emitter.send(SseEmitter.event().name("message").data(payload.toString()));
    }

    private static String sanitizeModelText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return THINK_BLOCK.matcher(content).replaceAll("").trim();
    }

    private void sendMessageEnd(SseEmitter emitter, String conversationId, List<String> sources,
                                JsonNode usage) throws IOException {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.set("sources", objectMapper.valueToTree(sources));
        if (usage != null && !usage.isMissingNode()) {
            metadata.set("usage", usage);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "end");
        payload.put("conversationId", conversationId == null ? "" : conversationId);
        payload.set("metadata", metadata);
        emitter.send(SseEmitter.event().name("message_end").data(payload.toString()));
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "error");
            payload.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(payload)));
        } catch (IOException e) {
            log.debug("发送 SSE 错误失败: {}", e.getMessage());
        } finally {
            emitter.complete();
        }
    }
}
