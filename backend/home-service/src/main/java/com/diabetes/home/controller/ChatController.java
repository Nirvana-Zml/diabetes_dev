package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dto.ChatQaRequest;
import com.diabetes.home.service.AIChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final AIChatService aiChatService;
    private final QaChatProperties qaChatProperties;

    public ChatController(AIChatService aiChatService, QaChatProperties qaChatProperties) {
        this.aiChatService = aiChatService;
        this.qaChatProperties = qaChatProperties;
    }

    @GetMapping("/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(aiChatService.getDifyWorkflowSpec());
    }

    /**
     * AI 科普问答 — Milvus 预检索 + Dify Chatbot SSE 代理。
     */
    @PostMapping(value = "/qa", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object chatQa(@RequestBody ChatQaRequest request, @CurrentUserId String userId) {
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            return badRequest("query 不能为空");
        }
        if (query.length() > qaChatProperties.getQueryMaxLength()) {
            return badRequest("query 超过 " + qaChatProperties.getQueryMaxLength() + " 字符");
        }
        String uid = resolveUserId(userId);
        return aiChatService.processQuestion(query.trim(), request.getConversationId(), uid);
    }

    @GetMapping("/history/{conversationId}")
    public ApiResponse<Map<String, Object>> chatHistory(@PathVariable String conversationId) {
        return ApiResponse.ok(Map.of(
                "messages", Collections.emptyList(),
                "conversationId", conversationId
        ));
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail(400, message));
    }

    private static String resolveUserId(String userId) {
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        return "guest_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
