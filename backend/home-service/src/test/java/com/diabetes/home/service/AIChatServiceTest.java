package com.diabetes.home.service;

import com.diabetes.common.dify.DifyClient;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dify.DifyQaChatContract;
import com.diabetes.home.knowledge.DocumentChunk;
import com.diabetes.home.knowledge.KnowledgeRetrieval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AIChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DifyClient difyClient = mock(DifyClient.class);
    private final KnowledgeRetrieval retrieval = mock(KnowledgeRetrieval.class);
    private final QaChatProperties props = new QaChatProperties();

    @Test
    void workflowSpecAndProcessQuestionCoverNoKeySuccessParseFailureAndError() {
        props.setApiKey("key");
        AIChatService service = service();
        assertEquals("http://dify/v1/workflows/run", service.getDifyWorkflowSpec().get("workflowUrl"));

        props.setApiKey("");
        assertNotNull(service.processQuestion("q", null, null));
        props.setApiKey(null);
        assertNotNull(service.processQuestion("q", null, null));

        props.setApiKey("key");
        List<DocumentChunk> chunks = List.of(new DocumentChunk("1", "content", "title", "src", "type", 0.9));
        when(retrieval.semanticSearch("q", 5)).thenReturn(chunks);
        when(retrieval.buildKnowledgeContext(chunks)).thenReturn("ctx");
        when(retrieval.extractSources(chunks)).thenReturn(List.of("title"));
        when(difyClient.runWorkflowStreaming(eq("key"), eq("guest"), anyMap())).thenReturn(Flux.just(
                ServerSentEvent.<String>builder().data(null).build(),
                ServerSentEvent.<String>builder().data("").build(),
                ServerSentEvent.<String>builder().data("not-json").build(),
                ServerSentEvent.<String>builder().data("{\"event\":\"text_chunk\",\"conversation_id\":\"c1\",\"data\":{\"text\":\"hello\"}}").build()
        ));
        assertNotNull(service.processQuestion("q", "c0", " "));

        when(difyClient.runWorkflowStreaming(eq("key"), eq("u1"), anyMap())).thenReturn(Flux.error(new RuntimeException("down")));
        assertNotNull(service.processQuestion("q", null, "u1"));

        when(difyClient.runWorkflowStreaming(eq("key"), eq("u2"), anyMap())).thenReturn(Flux.just(
                ServerSentEvent.<String>builder().data("{\"event\":\"message_end\"}").build()
        ));
        assertNotNull(service.processQuestion("q", null, "u2"));

        when(difyClient.runWorkflowStreaming(eq("key"), eq("guest"), anyMap())).thenReturn(Flux.empty());
        assertNotNull(service.processQuestion("q", null, null));

        try (var ignored = mockConstruction(SseEmitter.class, (mock, context) ->
                doThrow(new IOException("send")).when(mock).send(any(SseEmitter.SseEventBuilder.class)))) {
            when(difyClient.runWorkflowStreaming(eq("key"), eq("u3"), anyMap())).thenReturn(Flux.empty());
            assertNotNull(service.processQuestion("q", null, "u3"));
        }
    }

    @Test
    void privateDispatchHandlesEveryDifyEventBranch() throws Exception {
        AIChatService service = service();
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<String> conv = new AtomicReference<>("c0");
        AtomicReference<Boolean> endSent = new AtomicReference<>(false);
        AtomicReference<Boolean> textSent = new AtomicReference<>(false);
        List<String> sources = List.of("title");

        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"conversation_id\":\"c1\",\"data\":{\"from_variable_selector\":[\"x\",\"valid\"],\"text\":\"skip\"}}", conv, sources, endSent, textSent);
        assertFalse(textSent.get());

        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"conversation_id\":\" \",\"data\":{\"from_variable_selector\":[],\"text\":\"empty selector\"}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"data\":{\"text\":\"\"}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"data\":{\"from_variable_selector\":[\"x\",\"message\"],\"text\":\"skip\"}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"data\":{\"from_variable_selector\":[\"x\",\"text\"],\"text\":\"skip\"}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"data\":{\"from_variable_selector\":[\"x\",\"error_message\"],\"text\":\"skip\"}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"data\":{\"from_variable_selector\":[\"x\",\"error_type\"],\"text\":\"skip\"}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"text_chunk\",\"data\":{\"from_variable_selector\":[\"x\",\"answer\"],\"text\":\"<think>x</think> hi \"}}", conv, sources, endSent, textSent);
        assertTrue(textSent.get());
        assertEquals("c1", conv.get());

        textSent.set(false);
        dispatch(service, emitter, "{\"event\":\"node_finished\",\"data\":{\"node_type\":\"tool\",\"outputs\":{\"text\":\"no\"}}}", conv, sources, endSent, textSent);
        assertFalse(textSent.get());
        dispatch(service, emitter, "{\"event\":\"node_finished\",\"data\":{\"node_type\":\"llm\",\"outputs\":{\"text\":\"\"}}}", conv, sources, endSent, textSent);
        assertFalse(textSent.get());
        dispatch(service, emitter, "{\"event\":\"node_finished\",\"data\":{\"node_type\":\"llm\",\"outputs\":{\"text\":\"node answer\"}}}", conv, sources, endSent, textSent);
        assertTrue(textSent.get());

        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":null}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{}}", conv, sources, endSent, textSent);
        textSent.set(false);
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":false,\"error_message\":\"explicit\",\"message\":\"unused\"}}}", conv, sources, endSent, textSent);
        endSent.set(false);
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":false,\"error_message\":\" \",\"message\":\"bad\",\"error_type\":\"invalid\"}}}", conv, sources, endSent, textSent);
        assertTrue(endSent.get());
        endSent.set(false);
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":false,\"error_message\":\"null\",\"message\":\"fallback\"}}}", conv, sources, endSent, textSent);
        endSent.set(false);
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":true,\"text\":\"ignored\"}}}", conv, sources, endSent, new AtomicReference<>(true));
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":true,\"text\":\"workflow text\"}}}", conv, sources, endSent, new AtomicReference<>(false));
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":true,\"text\":\"\"}}}", conv, sources, endSent, new AtomicReference<>(false));
        dispatch(service, emitter, "{\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"valid\":true,\"text\":\"null\"}}}", conv, sources, endSent, new AtomicReference<>(false));
        dispatch(service, emitter, "{\"event\":\"message\",\"answer\":\"\"}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"message\",\"answer\":\"message answer\"}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"agent_message\",\"answer\":\"agent answer\"}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"agent_message\"}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"message_end\",\"metadata\":{\"usage\":{\"tokens\":1}}}", conv, sources, endSent, textSent);
        assertTrue(endSent.get());
        endSent.set(false);
        dispatch(service, emitter, "{\"event\":\"agent_message_end\",\"metadata\":{}}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"error\",\"message\":\"boom\"}", conv, sources, endSent, textSent);
        dispatch(service, emitter, "{\"event\":\"unknown\"}", conv, sources, endSent, textSent);
        verify(emitter, atLeastOnce()).complete();
        verify(emitter, atLeast(6)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void privateSendHelpersHandleBlankSanitizedTextAndIOException() throws Exception {
        AIChatService service = service();
        SseEmitter emitter = mock(SseEmitter.class);
        Method shouldForward = AIChatService.class.getDeclaredMethod("shouldForwardTextChunk", JsonNode.class);
        shouldForward.setAccessible(true);
        assertFalse((Boolean) shouldForward.invoke(service, objectMapper.readTree("{\"data\":{\"from_variable_selector\":[\"x\",\"valid\"]}}")));
        assertFalse((Boolean) shouldForward.invoke(service, objectMapper.readTree("{\"data\":{\"from_variable_selector\":[\"x\",\"message\"]}}")));
        assertFalse((Boolean) shouldForward.invoke(service, objectMapper.readTree("{\"data\":{\"from_variable_selector\":[\"x\",\"error_message\"]}}")));
        assertFalse((Boolean) shouldForward.invoke(service, objectMapper.readTree("{\"data\":{\"from_variable_selector\":[\"x\",\"error_type\"]}}")));
        assertTrue((Boolean) shouldForward.invoke(service, objectMapper.readTree("{\"data\":{\"from_variable_selector\":[\"x\",\"answer\"]}}")));

        Method sendText = AIChatService.class.getDeclaredMethod("sendTextChunk", SseEmitter.class, String.class, String.class);
        sendText.setAccessible(true);
        sendText.invoke(service, emitter, null, "   ");
        sendText.invoke(service, emitter, null, (Object) null);
        sendText.invoke(service, emitter, null, "<think>hidden</think>");
        sendText.invoke(service, emitter, null, "<think>hidden</think> visible");
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));

        Method sendError = AIChatService.class.getDeclaredMethod("sendError", SseEmitter.class, String.class);
        sendError.setAccessible(true);
        SseEmitter failing = mock(SseEmitter.class);
        doThrow(new IOException("io")).when(failing).send(any(SseEmitter.SseEventBuilder.class));
        sendError.invoke(service, failing, "bad");
        verify(failing).complete();

        Method sendEnd = AIChatService.class.getDeclaredMethod("sendMessageEnd", SseEmitter.class, String.class, List.class, JsonNode.class);
        sendEnd.setAccessible(true);
        sendEnd.invoke(service, emitter, null, List.of(), objectMapper.missingNode());
    }

    private AIChatService service() {
        return new AIChatService(difyClient, retrieval, props, objectMapper, "http://dify/");
    }

    private void dispatch(AIChatService service, SseEmitter emitter, String json,
                          AtomicReference<String> conv, List<String> sources,
                          AtomicReference<Boolean> endSent,
                          AtomicReference<Boolean> textSent) throws Exception {
        Method method = AIChatService.class.getDeclaredMethod("dispatchDifyEvent", SseEmitter.class, JsonNode.class,
                AtomicReference.class, List.class, AtomicReference.class, AtomicReference.class);
        method.setAccessible(true);
        method.invoke(service, emitter, objectMapper.readTree(json), conv, sources, endSent, textSent);
    }
}
