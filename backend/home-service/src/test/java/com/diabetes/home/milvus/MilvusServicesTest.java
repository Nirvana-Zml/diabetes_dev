package com.diabetes.home.milvus;

import com.diabetes.home.config.KnowledgeMilvusProperties;
import com.diabetes.home.knowledge.DocumentChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MilvusServicesTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embeddingReturnsZeroLocalHashAndFallsBackFromOpenAiFailure() {
        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setDimension(8);
        KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(props, new ObjectMapper());
        assertArrayEquals(new float[8], service.embed(null));
        assertArrayEquals(new float[8], service.embed(" "));

        float[] local = service.embed("糖尿病 饮食 运动");
        assertEquals(8, local.length);
        assertTrue(norm(local) > 0.99 && norm(local) < 1.01);

        props.getEmbedding().setOpenaiBaseUrl("http://127.0.0.1:1/");
        float[] fallback = service.embed("糖尿病 饮食 运动");
        assertEquals(8, fallback.length);
        assertTrue(norm(fallback) > 0.99 && norm(fallback) < 1.01);

        assertArrayEquals(new float[4], KnowledgeEmbeddingService.embedLocalHash("a b c", 4));
        assertArrayEquals(new float[4], KnowledgeEmbeddingService.embedLocalHash("a b", 4));
    }

    @Test
    void embeddingUsesOpenAiCompatibleApiWhenConfigured() throws Exception {
        startServer(exchange -> respond(exchange, 200,
                "{\"data\":[{\"embedding\":[3.0,4.0]}]}"));
        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setDimension(2);
        props.getEmbedding().setProvider("openai");
        props.getEmbedding().setOpenaiBaseUrl(baseUrl() + "/");
        props.getEmbedding().setOpenaiApiKey("test-key");
        props.getEmbedding().setOpenaiModel("text-embedding-test");

        KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(props, new ObjectMapper());
        float[] vector = service.embed("糖尿病知识");

        assertEquals(2, vector.length);
        assertEquals(0.6f, vector[0], 0.0001f);
        assertEquals(0.8f, vector[1], 0.0001f);
    }

    @Test
    void embeddingFallsBackWhenOpenAiResponseInvalid() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"data\":[{\"embedding\":[]}]}"));
        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setDimension(4);
        props.getEmbedding().setProvider("openai");
        props.getEmbedding().setOpenaiBaseUrl(baseUrl());
        props.getEmbedding().setOpenaiApiKey("test-key");

        KnowledgeEmbeddingService service = new KnowledgeEmbeddingService(props, new ObjectMapper());
        float[] vector = service.embed("fallback case");

        assertEquals(4, vector.length);
        assertTrue(norm(vector) > 0.99 && norm(vector) < 1.01);
    }

    @Test
    void milvusClientLifecycleSearchGuardAndPrivateHelpers() throws Exception {
        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setEnabled(false);
        MilvusKnowledgeClient client = new MilvusKnowledgeClient(props);
        client.init();
        assertFalse(client.isReady());
        assertTrue(client.search(new float[]{1}, 1, null, 0).isEmpty());
        assertTrue(client.search(null, 1, null, 0).isEmpty());

        props.setEnabled(true);
        props.setHost("bad-host.invalid");
        client.init();
        assertFalse(client.isReady());
        client.destroy();

        MilvusKnowledgeClient closeClient = new MilvusKnowledgeClient(props);
        injectClient(closeClient, mock(MilvusServiceClient.class), true);
        closeClient.destroy();
        assertFalse(closeClient.isReady());

        MilvusServiceClient throwingClose = mock(MilvusServiceClient.class);
        doThrow(new RuntimeException("close failed")).when(throwingClose).close();
        MilvusKnowledgeClient closeWithError = new MilvusKnowledgeClient(props);
        injectClient(closeWithError, throwingClose, true);
        assertDoesNotThrow(closeWithError::destroy);

        assertEquals(List.of(1.0f, 2.0f), invoke("toList", new Class[]{float[].class}, new Object[]{new float[]{1, 2}}));
        assertEquals(0.5, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class}, new Object[]{0.5f, MetricType.IP}));
        assertEquals(0.0, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class}, new Object[]{-1f, MetricType.IP}));
        assertEquals(1.0, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class}, new Object[]{2f, MetricType.IP}));
        assertEquals(0.5, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class}, new Object[]{1f, MetricType.L2}));
        assertEquals(0.8, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class}, new Object[]{0.2f, MetricType.COSINE}), 0.0001);
        assertEquals(MetricType.IP, invoke("parseMetric", new Class[]{String.class}, new Object[]{"ip"}));
        assertEquals(MetricType.COSINE, invoke("parseMetric", new Class[]{String.class}, new Object[]{"bad"}));
        assertEquals("a\\\\b\\\"c", invoke("escapeExpr", new Class[]{String.class}, new Object[]{"a\\b\"c"}));

        SearchResultsWrapper.IDScore score = mock(SearchResultsWrapper.IDScore.class);
        when(score.get("content")).thenReturn("hello");
        when(score.get("missing")).thenReturn(null);
        Method fieldAsString = MilvusKnowledgeClient.class.getDeclaredMethod(
                "fieldAsString", SearchResultsWrapper.IDScore.class, String.class);
        fieldAsString.setAccessible(true);
        assertEquals("hello", fieldAsString.invoke(null, score, "content"));
        assertEquals("", fieldAsString.invoke(null, score, "missing"));
    }

    @Test
    void milvusInitHandlesMissingCollectionAndLoadFailure() {
        KnowledgeMilvusProperties props = milvusProps(true);

        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> {
                    @SuppressWarnings("unchecked")
                    R<Boolean> exists = mock(R.class);
                    when(exists.getData()).thenReturn(false);
                    when(mock.hasCollection(any())).thenReturn(exists);
                })) {
            MilvusKnowledgeClient client = new MilvusKnowledgeClient(props);
            client.init();
            assertFalse(client.isReady());
        }

        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> {
                    @SuppressWarnings("unchecked")
                    R<Boolean> exists = mock(R.class);
                    when(exists.getData()).thenReturn(true);
                    when(mock.hasCollection(any())).thenReturn(exists);
                    @SuppressWarnings("unchecked")
                    R<RpcStatus> load = mock(R.class);
                    when(load.getStatus()).thenReturn(1);
                    when(load.getMessage()).thenReturn("load failed");
                    when(mock.loadCollection(any())).thenReturn(load);
                })) {
            MilvusKnowledgeClient client = new MilvusKnowledgeClient(props);
            client.init();
            assertFalse(client.isReady());
        }
    }

    @Test
    void milvusInitConnectsWhenCollectionReady() {
        KnowledgeMilvusProperties props = milvusProps(true);

        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> {
                    @SuppressWarnings("unchecked")
                    R<Boolean> exists = mock(R.class);
                    when(exists.getData()).thenReturn(true);
                    when(mock.hasCollection(any())).thenReturn(exists);
                    @SuppressWarnings("unchecked")
                    R<RpcStatus> load = mock(R.class);
                    when(load.getStatus()).thenReturn(R.Status.Success.getCode());
                    when(mock.loadCollection(any())).thenReturn(load);
                })) {
            MilvusKnowledgeClient client = new MilvusKnowledgeClient(props);
            client.init();
            assertTrue(client.isReady());
            client.destroy();
            assertFalse(client.isReady());
        }
    }

    @Test
    void milvusSearchReturnsMappedChunksAndHandlesFailures() throws Exception {
        KnowledgeMilvusProperties props = milvusProps(true);
        props.setMetricType("IP");
        MilvusKnowledgeClient client = new MilvusKnowledgeClient(props);
        MilvusServiceClient mockClient = mock(MilvusServiceClient.class);
        injectClient(client, mockClient, true);

        @SuppressWarnings("unchecked")
        R<SearchResults> failed = mock(R.class);
        when(failed.getStatus()).thenReturn(1);
        when(failed.getMessage()).thenReturn("search failed");
        when(mockClient.search(any(SearchParam.class))).thenReturn(failed);
        assertTrue(client.search(new float[]{0.1f, 0.2f}, 3, null, 0).isEmpty());

        @SuppressWarnings("unchecked")
        R<SearchResults> emptyScores = mock(R.class);
        when(emptyScores.getStatus()).thenReturn(R.Status.Success.getCode());
        SearchResults emptyResults = SearchResults.newBuilder()
                .setResults(SearchResultData.newBuilder().addTopks(0).build())
                .build();
        when(emptyScores.getData()).thenReturn(emptyResults);
        when(mockClient.search(any(SearchParam.class))).thenReturn(emptyScores);
        assertTrue(client.search(new float[]{0.1f, 0.2f}, 3, null, 0).isEmpty());

        SearchResultsWrapper.IDScore idScore = mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getScore()).thenReturn(0.95f);
        when(idScore.getStrID()).thenReturn("chunk-2");
        when(idScore.get("content")).thenReturn("正文2");
        when(idScore.get("doc_title")).thenReturn("标题2");
        when(idScore.get("doc_source")).thenReturn("指南2");
        when(idScore.get("doc_type")).thenReturn("article");

        @SuppressWarnings("unchecked")
        R<SearchResults> success = mock(R.class);
        when(success.getStatus()).thenReturn(R.Status.Success.getCode());
        SearchResults minimalResults = SearchResults.newBuilder()
                .setResults(SearchResultData.newBuilder().addTopks(1).build())
                .build();
        when(success.getData()).thenReturn(minimalResults);
        when(mockClient.search(any(SearchParam.class))).thenReturn(success);

        try (MockedConstruction<SearchResultsWrapper> ignored = mockConstruction(SearchResultsWrapper.class,
                (mock, context) -> when(mock.getIDScore(0)).thenReturn(List.of(idScore)))) {
            List<DocumentChunk> chunks = client.search(new float[]{0.1f, 0.2f}, 5, "article", 0.5);
            assertEquals(1, chunks.size());
            assertEquals("chunk-2", chunks.get(0).id());
            assertEquals("正文2", chunks.get(0).content());
            assertEquals("标题2", chunks.get(0).docTitle());
            assertEquals(0.95, chunks.get(0).score(), 0.0001);
        }

        SearchResultsWrapper.IDScore longIdScore = mock(SearchResultsWrapper.IDScore.class);
        when(longIdScore.getScore()).thenReturn(0.99f);
        when(longIdScore.getStrID()).thenReturn("");
        when(longIdScore.getLongID()).thenReturn(999L);
        when(longIdScore.get("content")).thenReturn("long-id");
        when(longIdScore.get("doc_title")).thenReturn("title");
        when(longIdScore.get("doc_source")).thenReturn("source");
        when(longIdScore.get("doc_type")).thenReturn("article");

        try (MockedConstruction<SearchResultsWrapper> ignored = mockConstruction(SearchResultsWrapper.class,
                (mock, context) -> when(mock.getIDScore(0)).thenReturn(List.of(longIdScore)))) {
            List<DocumentChunk> longIdChunks = client.search(new float[]{0.1f, 0.2f}, 5, null, 0);
            assertEquals(1, longIdChunks.size());
            assertEquals("999", longIdChunks.get(0).id());
        }
    }

    private static KnowledgeMilvusProperties milvusProps(boolean enabled) {
        KnowledgeMilvusProperties props = new KnowledgeMilvusProperties();
        props.setEnabled(enabled);
        props.setHost("localhost");
        props.setPort(19530);
        props.setCollection("diabetes_knowledge");
        return props;
    }

    private static void injectClient(MilvusKnowledgeClient client, MilvusServiceClient mockClient, boolean ready)
            throws Exception {
        Field clientField = MilvusKnowledgeClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(client, mockClient);
        Field readyField = MilvusKnowledgeClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(client, ready);
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

    private static Object invoke(String name, Class<?>[] types, Object[] args) throws Exception {
        Method method = MilvusKnowledgeClient.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static double norm(float[] values) {
        double sum = 0;
        for (float value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
