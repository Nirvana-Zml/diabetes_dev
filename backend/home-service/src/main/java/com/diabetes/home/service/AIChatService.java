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
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

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
        Map<String, Object> inputs = DifyQaChatContract.buildInputs(knowledgeContext);

        log.info("科普问答: user={}, convId={}, queryLen={}, hits={}",
                uid, conversationId, query.length(), chunks.size());

        AtomicReference<String> convRef = new AtomicReference<>(conversationId);
        AtomicReference<Boolean> endSent = new AtomicReference<>(false);
        StringBuilder lineBuffer = new StringBuilder();

        Flux<String> stream = difyClient.runChatStreaming(apiKey, uid, query, conversationId, inputs);
        stream.subscribe(
                chunk -> handleStreamChunk(emitter, chunk, lineBuffer, convRef, sources, endSent),
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

    private void handleStreamChunk(SseEmitter emitter, String chunk, StringBuilder lineBuffer,
                                   AtomicReference<String> convRef, List<String> sources,
                                   AtomicReference<Boolean> endSent) {
        lineBuffer.append(chunk);
        int newline;
        while ((newline = lineBuffer.indexOf("\n")) >= 0) {
            String line = lineBuffer.substring(0, newline).trim();
            lineBuffer.delete(0, newline + 1);
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("data:")) {
                line = line.substring(5).trim();
            }
            if (line.isEmpty() || "[DONE]".equals(line)) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(line);
                dispatchDifyEvent(emitter, node, convRef, sources, endSent);
            } catch (Exception e) {
                log.debug("忽略无法解析的 SSE 行: {}", line);
            }
        }
    }

    private void dispatchDifyEvent(SseEmitter emitter, JsonNode node,
                                   AtomicReference<String> convRef, List<String> sources,
                                   AtomicReference<Boolean> endSent) throws IOException {
        String event = node.path("event").asText("");
        if (node.has("conversation_id") && !node.path("conversation_id").asText("").isBlank()) {
            convRef.set(node.path("conversation_id").asText());
        }

        if ("message".equals(event) || ("agent_message".equals(event) && node.has("answer"))) {
            String answer = node.path("answer").asText("");
            if (!answer.isEmpty()) {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("type", "text");
                payload.put("content", answer);
                payload.put("conversationId", convRef.get() == null ? "" : convRef.get());
                emitter.send(SseEmitter.event().name("message").data(payload.toString()));
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
