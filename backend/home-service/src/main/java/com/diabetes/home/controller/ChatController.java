package com.diabetes.home.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dto.ChatQaRequest;
import com.diabetes.home.dto.VoiceTranscriptionResult;
import com.diabetes.home.service.AIChatService;
import com.diabetes.home.service.VoiceSttService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final AIChatService aiChatService;
    private final VoiceSttService voiceSttService;
    private final QaChatProperties qaChatProperties;

    public ChatController(AIChatService aiChatService,
                          VoiceSttService voiceSttService,
                          QaChatProperties qaChatProperties) {
        this.aiChatService = aiChatService;
        this.voiceSttService = voiceSttService;
        this.qaChatProperties = qaChatProperties;
    }

    @GetMapping("/dify-workflow-spec")
    public ApiResponse<Map<String, Object>> difyWorkflowSpec() {
        return ApiResponse.ok(aiChatService.getDifyWorkflowSpec());
    }

    @GetMapping("/stt/spec")
    public ApiResponse<Map<String, Object>> sttSpec() {
        return ApiResponse.ok(voiceSttService.getSttSpec());
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

    /**
     * AI 科普助手语音识别 — 阿里云 Fun-ASR 直连。
     */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> voiceToText(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language,
            @CurrentUserId String userId) {
        VoiceTranscriptionResult result = voiceSttService.transcribe(audio, resolveUserId(userId), language);
        return ApiResponse.ok(Map.of(
                "text", result.text(),
                "language", result.language()
        ));
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
