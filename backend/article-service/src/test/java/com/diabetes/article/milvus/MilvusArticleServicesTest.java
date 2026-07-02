package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MilvusArticleServicesTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embeddingReturnsZeroLocalHashAndFallsBackFromOpenAiFailure() {
        MilvusProperties props = milvusProps(false);
        props.setDimension(8);
        ArticleEmbeddingService service = new ArticleEmbeddingService(props, new ObjectMapper());
        assertArrayEquals(new float[8], service.embed(null));
        assertArrayEquals(new float[8], service.embed(" "));
        assertEquals(8, service.dimension());

        float[] local = service.embed("糖尿病 饮食 运动");
        assertEquals(8, local.length);
        assertTrue(norm(local) > 0.99 && norm(local) < 1.01);

        props.getEmbedding().setOpenaiBaseUrl("http://127.0.0.1:1/");
        float[] fallback = service.embed("糖尿病 饮食 运动");
        assertEquals(8, fallback.length);
        assertTrue(norm(fallback) > 0.99 && norm(fallback) < 1.01);

        assertArrayEquals(new float[4], ArticleEmbeddingService.embedLocalHash("a b c", 4));
    }

    @Test
    void embeddingUsesOpenAiCompatibleApiWhenConfigured() throws Exception {
        startServer(exchange -> respond(exchange, 200,
                "{\"data\":[{\"embedding\":[3.0,4.0]}]}"));
        MilvusProperties props = milvusProps(false);
        props.setDimension(2);
        props.getEmbedding().setProvider("openai");
        props.getEmbedding().setOpenaiBaseUrl(baseUrl() + "/");
        props.getEmbedding().setOpenaiApiKey("test-key");

        ArticleEmbeddingService service = new ArticleEmbeddingService(props, new ObjectMapper());
        float[] vector = service.embed("糖尿病资讯");

        assertEquals(2, vector.length);
        assertEquals(0.6f, vector[0], 0.0001f);
        assertEquals(0.8f, vector[1], 0.0001f);
    }

    @Test
    void embeddingFallsBackWhenOpenAiResponseInvalid() throws Exception {
        startServer(exchange -> respond(exchange, 200, "{\"data\":[{\"embedding\":[]}]}"));
        MilvusProperties props = milvusProps(false);
        props.setDimension(4);
        props.getEmbedding().setProvider("openai");
        props.getEmbedding().setOpenaiBaseUrl(baseUrl());
        props.getEmbedding().setOpenaiApiKey("test-key");

        ArticleEmbeddingService service = new ArticleEmbeddingService(props, new ObjectMapper());
        float[] vector = service.embed("fallback case");

        assertEquals(4, vector.length);
        assertTrue(norm(vector) > 0.99 && norm(vector) < 1.01);
    }

    @Test
    void milvusClientLifecycleSearchGuardAndPrivateHelpers() throws Exception {
        MilvusProperties props = milvusProps(false);
        MilvusArticleClient client = new MilvusArticleClient(props);
        client.init();
        assertFalse(client.isReady());
        assertTrue(client.search(new float[]{1}, 1, null).isEmpty());
        assertTrue(client.search(null, 1, null).isEmpty());
        client.upsert("art_1", 1, new float[]{1});
        client.delete("art_1");
        client.delete(" ");

        props.setEnabled(true);
        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> when(mock.hasCollection(any())).thenThrow(new RuntimeException("connect fail")))) {
            MilvusArticleClient failClient = new MilvusArticleClient(props);
            failClient.init();
            assertFalse(failClient.isReady());
        }

        MilvusArticleClient closeClient = new MilvusArticleClient(milvusProps(false));
        injectClient(closeClient, mock(MilvusServiceClient.class), true);
        closeClient.destroy();
        assertFalse(closeClient.isReady());

        MilvusServiceClient throwingClose = mock(MilvusServiceClient.class);
        doThrow(new RuntimeException("close failed")).when(throwingClose).close();
        MilvusArticleClient closeWithError = new MilvusArticleClient(props);
        injectClient(closeWithError, throwingClose, true);
        assertDoesNotThrow(closeWithError::destroy);

        assertEquals(0.5, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class},
                new Object[]{0.5f, MetricType.IP}));
        assertEquals(0.0, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class},
                new Object[]{-1f, MetricType.IP}));
        assertEquals(0.5, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class},
                new Object[]{1f, MetricType.L2}));
        assertEquals(0.8, (double) invoke("toSimilarity", new Class[]{float.class, MetricType.class},
                new Object[]{0.2f, MetricType.COSINE}), 0.0001);
        assertEquals(MetricType.IP, invoke("parseMetric", new Class[]{String.class}, new Object[]{"ip"}));
        assertEquals(MetricType.COSINE, invoke("parseMetric", new Class[]{String.class}, new Object[]{"bad"}));
        assertEquals("a\\\\b\\\"c", invoke("escapeExpr", new Class[]{String.class}, new Object[]{"a\\b\"c"}));
    }

    @Test
    void milvusInitHandlesMissingCollectionAndLoadFailure() {
        MilvusProperties props = milvusProps(true);

        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> {
                    @SuppressWarnings("unchecked")
                    R<Boolean> exists = mock(R.class);
                    when(exists.getData()).thenReturn(false);
                    when(exists.getStatus()).thenReturn(R.Status.Success.getCode());
                    when(mock.hasCollection(any())).thenReturn(exists);
                    @SuppressWarnings("unchecked")
                    R<RpcStatus> create = mock(R.class);
                    when(create.getStatus()).thenReturn(1);
                    when(create.getMessage()).thenReturn("create failed");
                    when(mock.createCollection(any(CreateCollectionParam.class))).thenReturn(create);
                })) {
            MilvusArticleClient client = new MilvusArticleClient(props);
            client.init();
            assertFalse(client.isReady());
        }

        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> {
                    @SuppressWarnings("unchecked")
                    R<Boolean> exists = mock(R.class);
                    when(exists.getData()).thenReturn(true);
                    when(exists.getStatus()).thenReturn(R.Status.Success.getCode());
                    when(mock.hasCollection(any())).thenReturn(exists);
                    @SuppressWarnings("unchecked")
                    R<RpcStatus> load = mock(R.class);
                    when(load.getStatus()).thenReturn(1);
                    when(load.getMessage()).thenReturn("load failed");
                    when(mock.loadCollection(any())).thenReturn(load);
                })) {
            MilvusArticleClient client = new MilvusArticleClient(props);
            client.init();
            assertFalse(client.isReady());
        }
    }

    @Test
    void milvusInitConnectsWhenCollectionReady() {
        MilvusProperties props = milvusProps(true);

        try (MockedConstruction<MilvusServiceClient> ignored = mockConstruction(MilvusServiceClient.class,
                (mock, context) -> {
                    @SuppressWarnings("unchecked")
                    R<Boolean> exists = mock(R.class);
                    when(exists.getData()).thenReturn(true);
                    when(exists.getStatus()).thenReturn(R.Status.Success.getCode());
                    when(mock.hasCollection(any())).thenReturn(exists);
                    @SuppressWarnings("unchecked")
                    R<RpcStatus> load = mock(R.class);
                    when(load.getStatus()).thenReturn(R.Status.Success.getCode());
                    when(mock.loadCollection(any())).thenReturn(load);
                })) {
            MilvusArticleClient client = new MilvusArticleClient(props);
            client.init();
            assertTrue(client.isReady());
            client.destroy();
            assertFalse(client.isReady());
        }
    }

    @Test
    void milvusUpsertDeleteAndSearchHandleResponses() throws Exception {
        MilvusProperties props = milvusProps(true);
        props.setMetricType("IP");
        MilvusArticleClient client = new MilvusArticleClient(props);
        MilvusServiceClient mockClient = mock(MilvusServiceClient.class);
        injectClient(client, mockClient, true);

        @SuppressWarnings("unchecked")
        R<MutationResult> upsertOk = mock(R.class);
        when(upsertOk.getStatus()).thenReturn(R.Status.Success.getCode());
        when(mockClient.upsert(any(UpsertParam.class))).thenReturn(upsertOk);
        client.upsert("art_1", 2, new float[]{0.1f, 0.2f});
        verify(mockClient).upsert(any(UpsertParam.class));

        @SuppressWarnings("unchecked")
        R<MutationResult> deleteOk = mock(R.class);
        when(deleteOk.getStatus()).thenReturn(R.Status.Success.getCode());
        when(mockClient.delete(any(DeleteParam.class))).thenReturn(deleteOk);
        client.delete("art_1");
        verify(mockClient).delete(any(DeleteParam.class));

        @SuppressWarnings("unchecked")
        R<SearchResults> failed = mock(R.class);
        when(failed.getStatus()).thenReturn(1);
        when(failed.getMessage()).thenReturn("search failed");
        when(mockClient.search(any(SearchParam.class))).thenReturn(failed);
        assertThrows(IllegalStateException.class,
                () -> client.search(new float[]{0.1f, 0.2f}, 3, Set.of("art_1")));

        SearchResultsWrapper.IDScore idScore = mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getScore()).thenReturn(0.95f);
        when(idScore.getStrID()).thenReturn("");
        when(idScore.get("article_id")).thenReturn("art_2");

        @SuppressWarnings("unchecked")
        R<SearchResults> success = mock(R.class);
        when(success.getStatus()).thenReturn(R.Status.Success.getCode());
        SearchResults minimalResults = SearchResults.newBuilder()
                .setResults(SearchResultData.newBuilder().addTopks(1).build())
                .build();
        when(success.getData()).thenReturn(minimalResults);
        when(mockClient.search(any(SearchParam.class))).thenReturn(success);

        try (MockedConstruction<SearchResultsWrapper> ignored = mockConstruction(SearchResultsWrapper.class,
                (mock, context) -> when(mock.getIDScore(0)).thenReturn(java.util.List.of(idScore)))) {
            Map<String, Double> hits = client.search(new float[]{0.1f, 0.2f}, 5, Set.of("art_2"));
            assertEquals(0.95, hits.get("art_2"), 0.0001);
        }

        SearchResultsWrapper.IDScore filtered = mock(SearchResultsWrapper.IDScore.class);
        when(filtered.getScore()).thenReturn(0.5f);
        when(filtered.getStrID()).thenReturn("art_other");
        try (MockedConstruction<SearchResultsWrapper> ignored = mockConstruction(SearchResultsWrapper.class,
                (mock, context) -> when(mock.getIDScore(0)).thenReturn(java.util.List.of(filtered)))) {
            assertTrue(client.search(new float[]{0.1f}, 5, Set.of("art_x")).isEmpty());
        }
    }

    private static MilvusProperties milvusProps(boolean enabled) {
        MilvusProperties props = new MilvusProperties();
        props.setEnabled(enabled);
        props.setHost("localhost");
        props.setPort(19530);
        props.setCollection("article_knowledge");
        props.setDimension(2);
        return props;
    }

    private static void injectClient(MilvusArticleClient client, MilvusServiceClient mockClient, boolean ready)
            throws Exception {
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(client, mockClient);
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
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
        Method method = MilvusArticleClient.class.getDeclaredMethod(name, types);
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
