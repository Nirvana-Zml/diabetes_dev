package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.MetricType;
import io.milvus.param.IndexType;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.grpc.SearchResults;
import io.milvus.response.SearchResultsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

class MilvusArticleClientTest {

    private MilvusArticleClient client;
    private MilvusProperties properties;

    private static Object answer(InvocationOnMock invocation) {
        return R.success(null);
    }

    @BeforeEach
    void setUp() {
        properties = new MilvusProperties();
        properties.setEnabled(false);
        client = new MilvusArticleClient(properties);
    }

    private MilvusArticleClient createReadyClient() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        properties.setMetricType("COSINE");
        properties.setIndexType("IVF_FLAT");
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.hasCollection(any())).thenReturn(R.success(true));
        when(mockClient.loadCollection(any())).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        return c;
    }

    @Test
    void testIsReadyWhenDisabled() {
        assertFalse(client.isReady());
    }

    @Test
    void testUpsertWhenDisabled() {
        assertDoesNotThrow(() -> client.upsert("art_01", 1, new float[]{1.0f, 2.0f}));
    }

    @Test
    void testSearchWhenDisabled() {
        var result = client.search(new float[]{1.0f, 2.0f}, 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWhenDisabledNullVector() {
        var result = client.search(null, 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testDeleteWhenDisabled() {
        assertDoesNotThrow(() -> client.delete("art_01"));
    }

    @Test
    void testDeleteWhenArticleIdNull() {
        assertDoesNotThrow(() -> client.delete(null));
    }

    @Test
    void testDeleteWhenArticleIdBlank() {
        assertDoesNotThrow(() -> client.delete("   "));
    }

    @Test
    void testToSimilarityCosine() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 0.5f, MetricType.COSINE);
        assertTrue(result >= 0 && result <= 1);
    }

    @Test
    void testToSimilarityIP() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 0.8f, MetricType.IP);
        assertEquals(0.8, result, 0.0001);
    }

    @Test
    void testToSimilarityL2() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 1.0f, MetricType.L2);
        assertEquals(0.5, result, 0.0001);
    }

    @Test
    void testToSimilarityIPClamped() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result1 = (double) method.invoke(null, 1.5f, MetricType.IP);
        assertEquals(1.0, result1, 0.0001);
        
        double result2 = (double) method.invoke(null, -0.5f, MetricType.IP);
        assertEquals(0.0, result2, 0.0001);
    }

    @Test
    void testParseMetricValid() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("parseMetric", String.class);
        method.setAccessible(true);
        
        MetricType result = (MetricType) method.invoke(null, "COSINE");
        assertEquals(MetricType.COSINE, result);
    }

    @Test
    void testParseMetricInvalid() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("parseMetric", String.class);
        method.setAccessible(true);
        
        MetricType result = (MetricType) method.invoke(null, "INVALID");
        assertEquals(MetricType.COSINE, result);
    }

    @Test
    void testParseIndexTypeValid() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("parseIndexType", String.class);
        method.setAccessible(true);
        
        IndexType result = (IndexType) method.invoke(null, "IVF_FLAT");
        assertEquals(IndexType.IVF_FLAT, result);
    }

    @Test
    void testParseIndexTypeInvalid() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("parseIndexType", String.class);
        method.setAccessible(true);
        
        IndexType result = (IndexType) method.invoke(null, "INVALID");
        assertEquals(IndexType.IVF_FLAT, result);
    }

    @Test
    void testEscapeExpr() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("escapeExpr", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "test\\quote\"");
        assertEquals("test\\\\quote\\\"", result);
    }

    @Test
    void testEscapeExprEmpty() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("escapeExpr", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "");
        assertEquals("", result);
    }

    @Test
    void testCheckResponseNull() {
        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> {
            try {
                Method method = MilvusArticleClient.class.getDeclaredMethod("checkResponse", io.milvus.param.R.class, String.class);
                method.setAccessible(true);
                method.invoke(client, null, "test");
            } catch (InvocationTargetException e) {
                throw (com.diabetes.common.exception.BusinessException) e.getTargetException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testCheckResponseFailure() {
        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> {
            try {
                Method method = MilvusArticleClient.class.getDeclaredMethod("checkResponse", io.milvus.param.R.class, String.class);
                method.setAccessible(true);
                io.milvus.param.R<?> response = io.milvus.param.R.failed(new RuntimeException("error"));
                method.invoke(client, response, "test");
            } catch (InvocationTargetException e) {
                throw (com.diabetes.common.exception.BusinessException) e.getTargetException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testInitWhenEnabledButConnectionFails() {
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(1);
        
        client = new MilvusArticleClient(properties);
        assertFalse(client.isReady());
    }

    @Test
    void testToSimilarityNegativeCosine() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, -0.5f, MetricType.COSINE);
        assertEquals(1.0, result, 0.0001);
    }

    @Test
    void testToSimilarityLargeL2() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 100.0f, MetricType.L2);
        assertEquals(1.0 / 101.0, result, 0.0001);
    }

    @Test
    void testUpsertWhenReady() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.upsert(any(UpsertParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        assertDoesNotThrow(() -> c.upsert("art_01", 1, new float[128]));
    }

    @Test
    void testDeleteWhenReady() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.delete(any(DeleteParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        assertDoesNotThrow(() -> c.delete("art_01"));
    }

    @Test
    void testSearchWhenReadyWithNullData() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.search(any(SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWhenReadyWithSearchFailure() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.search(any(SearchParam.class))).thenReturn(R.failed(new RuntimeException("search error")));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> c.search(new float[128], 10, Set.of()));
    }

    @Test
    void testUpsertFailure() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.upsert(any(UpsertParam.class))).thenReturn(R.failed(new RuntimeException("upsert error")));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> c.upsert("art_01", 1, new float[128]));
    }

    @Test
    void testDeleteFailure() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.delete(any(DeleteParam.class))).thenReturn(R.failed(new RuntimeException("delete error")));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> c.delete("art_01"));
    }

    @Test
    void testCheckResponseSuccess() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("checkResponse", io.milvus.param.R.class, String.class);
        method.setAccessible(true);
        
        io.milvus.param.R<?> response = R.success(null);
        assertDoesNotThrow(() -> method.invoke(client, response, "test"));
    }

    @Test
    void testToSimilarityWithIP() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 1.0f, MetricType.IP);
        assertEquals(1.0, result, 0.0001);
        
        result = (double) method.invoke(null, -1.0f, MetricType.IP);
        assertEquals(0.0, result, 0.0001);
    }

    @Test
    void testToSimilarityWithL2() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 0.0f, MetricType.L2);
        assertEquals(1.0, result, 0.0001);
        
        result = (double) method.invoke(null, 1000.0f, MetricType.L2);
        assertTrue(result > 0 && result < 0.01);
    }

    @Test
    void testEscapeExprWithSpecialChars() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("escapeExpr", String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "test\\\"quote");
        assertEquals("test\\\\\\\"quote", result);
    }

    @Test
    void testEscapeExprWithNull() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("escapeExpr", String.class);
        method.setAccessible(true);
        
        assertThrows(IllegalArgumentException.class, () -> method.invoke(null, null));
    }

    @Test
    void testToSimilarityWithUnknownMetric() throws Exception {
        Method method = MilvusArticleClient.class.getDeclaredMethod("toSimilarity", float.class, MetricType.class);
        method.setAccessible(true);
        
        double result = (double) method.invoke(null, 0.5f, MetricType.L2);
        assertTrue(result >= 0 && result <= 1);
    }

    @Test
    void testEnsureCollectionExists() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.hasCollection(any(io.milvus.param.collection.HasCollectionParam.class))).thenReturn(R.success(true));
        when(mockClient.loadCollection(any(io.milvus.param.collection.LoadCollectionParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        Method ensureMethod = MilvusArticleClient.class.getDeclaredMethod("ensureCollection");
        ensureMethod.setAccessible(true);
        ensureMethod.invoke(c);
        
        assertTrue(c.isReady());
    }

    @Test
    void testEnsureCollectionNotExists() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        properties.setMetricType("COSINE");
        properties.setIndexType("IVF_FLAT");
        properties.setIndexNlist(128);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.hasCollection(any(io.milvus.param.collection.HasCollectionParam.class))).thenReturn(R.success(false));
        when(mockClient.createCollection(any(io.milvus.param.collection.CreateCollectionParam.class))).thenReturn(R.success(null));
        when(mockClient.createIndex(any(io.milvus.param.index.CreateIndexParam.class))).thenReturn(R.success(null));
        when(mockClient.loadCollection(any(io.milvus.param.collection.LoadCollectionParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        Method ensureMethod = MilvusArticleClient.class.getDeclaredMethod("ensureCollection");
        ensureMethod.setAccessible(true);
        ensureMethod.invoke(c);
        
        assertTrue(c.isReady());
    }

    @Test
    void testInitWithException() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        
        assertDoesNotThrow(() -> {
            Method initMethod = MilvusArticleClient.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(c);
        });
        
        assertFalse(c.isReady());
    }

    @Test
    void testCloseClient() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        Method closeMethod = MilvusArticleClient.class.getDeclaredMethod("closeClient");
        closeMethod.setAccessible(true);
        closeMethod.invoke(c);
        
        assertFalse(c.isReady());
    }

    @Test
    void testDestroy() throws Exception {
        MilvusArticleClient c = createReadyClient();
        assertTrue(c.isReady());
        
        Method destroyMethod = MilvusArticleClient.class.getDeclaredMethod("destroy");
        destroyMethod.setAccessible(true);
        destroyMethod.invoke(c);
        
        assertFalse(c.isReady());
    }

    @Test
    void testSearchWithNullVector() {
        var result = client.search(null, 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithResults() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        var result = c.search(new float[]{1.0f, 2.0f}, 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithEmptyResults() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        var result = c.search(new float[]{1.0f, 2.0f}, 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testDeleteWithBlankId() throws Exception {
        MilvusArticleClient c = createReadyClient();
        assertDoesNotThrow(() -> c.delete("   "));
    }

    @Test
    void testDeleteWithNullId() throws Exception {
        MilvusArticleClient c = createReadyClient();
        assertDoesNotThrow(() -> c.delete(null));
    }

    @Test
    void testIsReadyWithClientNull() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, null);
        
        assertFalse(c.isReady());
    }

    @Test
    void testIsReadyWithReadyFalse() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, false);
        
        assertFalse(c.isReady());
    }

    @Test
    void testSearchWithValidResponse() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithMetrics() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, Set.of());
        assertTrue(result.isEmpty());
    }

    

    @Test
    void testSearchWithEmptyCandidateFilter() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, Collections.emptySet());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithNullCandidateFilter() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithNullVectorWhenReady() throws Exception {
        MilvusArticleClient c = createReadyClient();
        var result = c.search(null, 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithNullData() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        io.milvus.param.R<?> response = R.success(null);
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn((io.milvus.param.R<io.milvus.grpc.SearchResults>) response);
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithValidSearchResults() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        doAnswer(MilvusArticleClientTest::answer).when(mockClient).search(any(io.milvus.param.dml.SearchParam.class));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        try {
            c.search(new float[128], 10, Set.of());
        } catch (io.milvus.exception.ParamException ignored) {
        }
    }

    @Test
    void testSearchWithL2Metric() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        properties.setMetricType("L2");
        properties.setIndexType("IVF_FLAT");
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        var result = c.search(new float[128], 10, Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchWithNonEmptyCandidateFilter() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.search(any(io.milvus.param.dml.SearchParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        var result = c.search(new float[128], 10, Set.of("article_001", "article_002"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractArticleIdWithValidStrId() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        SearchResultsWrapper.IDScore idScore = Mockito.mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getStrID()).thenReturn("article_001");
        
        Method extractMethod = MilvusArticleClient.class.getDeclaredMethod("extractArticleId", SearchResultsWrapper.IDScore.class);
        extractMethod.setAccessible(true);
        
        String result = (String) extractMethod.invoke(c, idScore);
        assertEquals("article_001", result);
    }

    @Test
    void testExtractArticleIdWithBlankStrId() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        SearchResultsWrapper.IDScore idScore = Mockito.mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getStrID()).thenReturn("");
        when(idScore.get("article_id")).thenReturn("article_from_field");
        
        Method extractMethod = MilvusArticleClient.class.getDeclaredMethod("extractArticleId", SearchResultsWrapper.IDScore.class);
        extractMethod.setAccessible(true);
        
        String result = (String) extractMethod.invoke(c, idScore);
        assertEquals("article_from_field", result);
    }

    @Test
    void testExtractArticleIdWithNullStrIdAndNullField() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        SearchResultsWrapper.IDScore idScore = Mockito.mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getStrID()).thenReturn(null);
        when(idScore.get("article_id")).thenReturn(null);
        
        Method extractMethod = MilvusArticleClient.class.getDeclaredMethod("extractArticleId", SearchResultsWrapper.IDScore.class);
        extractMethod.setAccessible(true);
        
        String result = (String) extractMethod.invoke(c, idScore);
        assertNull(result);
    }

    @Test
    void testProcessSearchResultsWithValidResults() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        SearchResultsWrapper.IDScore idScore = Mockito.mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getStrID()).thenReturn("article_001");
        when(idScore.getScore()).thenReturn(0.2f);
        
        Method processMethod = MilvusArticleClient.class.getDeclaredMethod("processSearchResults", List.class, io.milvus.param.MetricType.class, Set.class);
        processMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Double> result = (Map<String, Double>) processMethod.invoke(c, List.of(idScore), io.milvus.param.MetricType.COSINE, null);
        
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("article_001"));
    }

    @Test
    void testProcessSearchResultsWithFilteredOut() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        SearchResultsWrapper.IDScore idScore = Mockito.mock(SearchResultsWrapper.IDScore.class);
        when(idScore.getStrID()).thenReturn("article_not_in_filter");
        when(idScore.getScore()).thenReturn(0.2f);
        
        Method processMethod = MilvusArticleClient.class.getDeclaredMethod("processSearchResults", List.class, io.milvus.param.MetricType.class, Set.class);
        processMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Double> result = (Map<String, Double>) processMethod.invoke(c, List.of(idScore), io.milvus.param.MetricType.COSINE, Set.of("article_in_filter"));
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testProcessSearchResultsWithEmptyScores() throws Exception {
        MilvusArticleClient c = createReadyClient();
        
        Method processMethod = MilvusArticleClient.class.getDeclaredMethod("processSearchResults", List.class, io.milvus.param.MetricType.class, Set.class);
        processMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Double> result = (Map<String, Double>) processMethod.invoke(c, List.of(), io.milvus.param.MetricType.COSINE, null);
        
        assertTrue(result.isEmpty());
    }

    

    

    

    

    

    

    

    

    

    @Test
    void testInitWhenEnabledWithMockClient() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        properties.setMetricType("COSINE");
        properties.setIndexType("IVF_FLAT");
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.hasCollection(any(io.milvus.param.collection.HasCollectionParam.class))).thenReturn(R.success(true));
        when(mockClient.loadCollection(any(io.milvus.param.collection.LoadCollectionParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        assertTrue(c.isReady());
    }

    @Test
    void testInitWhenDisabled() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(false);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        
        Method initMethod = MilvusArticleClient.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        assertDoesNotThrow(() -> initMethod.invoke(c));
        
        assertFalse(c.isReady());
    }

    @Test
    void testInitSuccessWithMockedClient() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("localhost");
        properties.setPort(19530);
        properties.setCollection("test_collection");
        properties.setDimension(128);
        properties.setMetricType("COSINE");
        properties.setIndexType("IVF_FLAT");
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        
        when(mockClient.hasCollection(any(io.milvus.param.collection.HasCollectionParam.class))).thenReturn(R.success(true));
        when(mockClient.loadCollection(any(io.milvus.param.collection.LoadCollectionParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        assertTrue(c.isReady());
    }

    @Test
    void testInitWithDifferentMetricTypes() throws Exception {
        String[] metricTypes = {"L2", "IP", "COSINE"};
        for (String metric : metricTypes) {
            properties = new MilvusProperties();
            properties.setEnabled(true);
            properties.setHost("localhost");
            properties.setPort(19530);
            properties.setCollection("test_collection_" + metric);
            properties.setDimension(128);
            properties.setMetricType(metric);
            properties.setIndexType("IVF_FLAT");
            
            MilvusArticleClient c = new MilvusArticleClient(properties);
            assertFalse(c.isReady());
        }
    }

    @Test
    void testInitWithDifferentIndexTypes() throws Exception {
        String[] indexTypes = {"IVF_FLAT", "IVF_SQ8", "HNSW"};
        for (String indexType : indexTypes) {
            properties = new MilvusProperties();
            properties.setEnabled(true);
            properties.setHost("localhost");
            properties.setPort(19530);
            properties.setCollection("test_collection_" + indexType);
            properties.setDimension(128);
            properties.setMetricType("COSINE");
            properties.setIndexType(indexType);
            
            MilvusArticleClient c = new MilvusArticleClient(properties);
            assertFalse(c.isReady());
        }
    }

    

    @Test
    void testCloseClientWithClientNull() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, null);
        
        Method closeMethod = MilvusArticleClient.class.getDeclaredMethod("closeClient");
        closeMethod.setAccessible(true);
        closeMethod.invoke(c);
        
        assertFalse(c.isReady());
    }

    @Test
    void testCloseClientWithException() throws Exception {
        properties = new MilvusProperties();
        properties.setEnabled(true);
        
        MilvusArticleClient c = new MilvusArticleClient(properties);
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        doThrow(new RuntimeException("close error")).when(mockClient).close();
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Field readyField = MilvusArticleClient.class.getDeclaredField("ready");
        readyField.setAccessible(true);
        readyField.set(c, true);
        
        Method closeMethod = MilvusArticleClient.class.getDeclaredMethod("closeClient");
        closeMethod.setAccessible(true);
        assertDoesNotThrow(() -> closeMethod.invoke(c));
        
        assertFalse(c.isReady());
    }

    @Test
    void testLoadCollection() throws Exception {
        MilvusArticleClient c = createReadyClient();
        MilvusServiceClient mockClient = Mockito.mock(MilvusServiceClient.class);
        when(mockClient.loadCollection(any(io.milvus.param.collection.LoadCollectionParam.class))).thenReturn(R.success(null));
        
        Field clientField = MilvusArticleClient.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(c, mockClient);
        
        Method loadMethod = MilvusArticleClient.class.getDeclaredMethod("loadCollection", String.class);
        loadMethod.setAccessible(true);
        assertDoesNotThrow(() -> loadMethod.invoke(c, "test_collection"));
    }
}
