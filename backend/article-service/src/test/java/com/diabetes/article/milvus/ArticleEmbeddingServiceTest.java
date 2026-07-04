package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleEmbeddingServiceTest {

    private ArticleEmbeddingService embeddingService;
    private MilvusProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MilvusProperties();
        properties.setDimension(128);
        properties.getEmbedding().setProvider("local");
        embeddingService = new ArticleEmbeddingService(properties, new ObjectMapper());
    }

    @Test
    void testDimension() {
        assertEquals(128, embeddingService.dimension());
    }

    @Test
    void testEmbedNullText() {
        float[] result = embeddingService.embed(null);
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedBlankText() {
        float[] result = embeddingService.embed("   ");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHash() {
        float[] result = embeddingService.embed("糖尿病饮食管理");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedWithOpenaiProviderDisabled() {
        float[] result = embeddingService.embed("测试文本");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashWithEmptyTokens() {
        float[] result = embeddingService.embed("a b c");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashWithChinesePunctuation() {
        float[] result = embeddingService.embed("饮食，运动；用药。");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashWithEnglishText() {
        float[] result = embeddingService.embed("diabetes diet management");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testNormalizeWithZeroVector() throws Exception {
        Method normalizeMethod = ArticleEmbeddingService.class.getDeclaredMethod("normalize", float[].class);
        normalizeMethod.setAccessible(true);
        
        float[] vec = new float[]{0.0f, 0.0f};
        float[] result = (float[]) normalizeMethod.invoke(null, (Object) vec);
        assertNotNull(result);
        assertEquals(0.0f, result[0], 0.0001f);
    }

    @Test
    void testNormalizeWithNonZeroVector() throws Exception {
        Method normalizeMethod = ArticleEmbeddingService.class.getDeclaredMethod("normalize", float[].class);
        normalizeMethod.setAccessible(true);
        
        float[] vec = new float[]{3.0f, 4.0f};
        float[] result = (float[]) normalizeMethod.invoke(null, (Object) vec);
        assertNotNull(result);
        assertEquals(0.6f, result[0], 0.0001f);
        assertEquals(0.8f, result[1], 0.0001f);
    }

    @Test
    void testNormalizeWithNegativeValues() throws Exception {
        Method normalizeMethod = ArticleEmbeddingService.class.getDeclaredMethod("normalize", float[].class);
        normalizeMethod.setAccessible(true);
        
        float[] vec = new float[]{-3.0f, -4.0f};
        float[] result = (float[]) normalizeMethod.invoke(null, (Object) vec);
        assertNotNull(result);
        assertEquals(-0.6f, result[0], 0.0001f);
        assertEquals(-0.8f, result[1], 0.0001f);
    }

    @Test
    void testNormalizeWithSingleElement() throws Exception {
        Method normalizeMethod = ArticleEmbeddingService.class.getDeclaredMethod("normalize", float[].class);
        normalizeMethod.setAccessible(true);
        
        float[] vec = new float[]{5.0f};
        float[] result = (float[]) normalizeMethod.invoke(null, (Object) vec);
        assertNotNull(result);
        assertEquals(1.0f, result[0], 0.0001f);
    }

    @Test
    void testZeroVector() throws Exception {
        Method zeroVectorMethod = ArticleEmbeddingService.class.getDeclaredMethod("zeroVector");
        zeroVectorMethod.setAccessible(true);
        
        float[] result = (float[]) zeroVectorMethod.invoke(embeddingService);
        assertNotNull(result);
        assertEquals(128, result.length);
        for (float v : result) {
            assertEquals(0.0f, v, 0.0001f);
        }
    }

    @Test
    void testEmbedLocalHashWithMixedText() {
        float[] result = embeddingService.embed("糖尿病 Diabetes 饮食管理 Diet Management");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashWithNumbers() {
        float[] result = embeddingService.embed("2型糖尿病 血糖控制 100mg/dl");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashWithEmptyString() {
        float[] result = embeddingService.embed("");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashStaticMethod() throws Exception {
        Method embedLocalHashMethod = ArticleEmbeddingService.class.getDeclaredMethod("embedLocalHash", String.class, int.class);
        embedLocalHashMethod.setAccessible(true);
        
        float[] result = (float[]) embedLocalHashMethod.invoke(null, "测试文本", 64);
        assertNotNull(result);
        assertEquals(64, result.length);
    }

    @Test
    void testEmbedLocalHashStaticWithNull() {
        assertThrows(NullPointerException.class, () -> {
            try {
                Method embedLocalHashMethod = ArticleEmbeddingService.class.getDeclaredMethod("embedLocalHash", String.class, int.class);
                embedLocalHashMethod.setAccessible(true);
                embedLocalHashMethod.invoke(null, null, 64);
            } catch (InvocationTargetException e) {
                throw (NullPointerException) e.getTargetException();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testEmbedLocalHashStaticWithBlank() throws Exception {
        Method embedLocalHashMethod = ArticleEmbeddingService.class.getDeclaredMethod("embedLocalHash", String.class, int.class);
        embedLocalHashMethod.setAccessible(true);
        
        float[] result = (float[]) embedLocalHashMethod.invoke(null, "   ", 64);
        assertNotNull(result);
        assertEquals(64, result.length);
    }

    @Test
    void testNormalizeWithAllNegative() throws Exception {
        Method normalizeMethod = ArticleEmbeddingService.class.getDeclaredMethod("normalize", float[].class);
        normalizeMethod.setAccessible(true);
        
        float[] vec = new float[]{-1.0f, -1.0f};
        float[] result = (float[]) normalizeMethod.invoke(null, (Object) vec);
        assertNotNull(result);
        assertEquals(-Math.sqrt(0.5), result[0], 0.0001f);
    }

    @Test
    void testEmbedWithOpenaiProviderButNoBaseUrl() {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("");
        
        float[] result = embeddingService.embed("测试文本");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedWithOpenaiProviderBaseUrlNull() {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl(null);
        
        float[] result = embeddingService.embed("测试文本");
        assertNotNull(result);
        assertEquals(128, result.length);
    }

    @Test
    void testEmbedLocalHashStaticWithEmptyString() throws Exception {
        Method embedLocalHashMethod = ArticleEmbeddingService.class.getDeclaredMethod("embedLocalHash", String.class, int.class);
        embedLocalHashMethod.setAccessible(true);
        
        float[] result = (float[]) embedLocalHashMethod.invoke(null, "", 64);
        assertNotNull(result);
        assertEquals(64, result.length);
    }

    @Test
    void testZeroVectorWithDimensionChange() throws Exception {
        properties.setDimension(256);
        Method zeroVectorMethod = ArticleEmbeddingService.class.getDeclaredMethod("zeroVector");
        zeroVectorMethod.setAccessible(true);
        
        float[] result = (float[]) zeroVectorMethod.invoke(embeddingService);
        assertNotNull(result);
        assertEquals(256, result.length);
    }

    @Test
    void testEmbedViaOpenAiCompatibleSuccess() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");
        properties.setDimension(3);

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenReturn("{\"data\":[{\"embedding\":[1.0, 0.0, 0.0]}]}");

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        Method method = ArticleEmbeddingService.class.getDeclaredMethod("embedViaOpenAiCompatible", String.class, MilvusProperties.Embedding.class);
        method.setAccessible(true);

        float[] result = (float[]) method.invoke(embeddingService, "test text", properties.getEmbedding());
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(1.0f, result[0], 0.0001f);
    }

    @Test
    void testEmbedViaOpenAiCompatibleInvalidResponse() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenReturn("{\"data\":[]}");

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        Method method = ArticleEmbeddingService.class.getDeclaredMethod("embedViaOpenAiCompatible", String.class, MilvusProperties.Embedding.class);
        method.setAccessible(true);

        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> {
            try {
                method.invoke(embeddingService, "test text", properties.getEmbedding());
            } catch (InvocationTargetException e) {
                throw (com.diabetes.common.exception.BusinessException) e.getTargetException();
            }
        });
    }

    @Test
    void testEmbedViaOpenAiCompatibleEmptyEmbedding() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenReturn("{\"data\":[{\"embedding\":[]}]}");

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        Method method = ArticleEmbeddingService.class.getDeclaredMethod("embedViaOpenAiCompatible", String.class, MilvusProperties.Embedding.class);
        method.setAccessible(true);

        assertThrows(com.diabetes.common.exception.BusinessException.class, () -> {
            try {
                method.invoke(embeddingService, "test text", properties.getEmbedding());
            } catch (InvocationTargetException e) {
                throw (com.diabetes.common.exception.BusinessException) e.getTargetException();
            }
        });
    }

    @Test
    void testEmbedViaOpenAiCompatibleWithTrailingSlash() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080/");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");
        properties.setDimension(2);

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenReturn("{\"data\":[{\"embedding\":[0.5, 0.5]}]}");

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        Method method = ArticleEmbeddingService.class.getDeclaredMethod("embedViaOpenAiCompatible", String.class, MilvusProperties.Embedding.class);
        method.setAccessible(true);

        float[] result = (float[]) method.invoke(embeddingService, "test text", properties.getEmbedding());
        assertNotNull(result);
        assertEquals(2, result.length);
    }

    @Test
    void testEmbedViaOpenAiCompatibleApiError() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenThrow(new RuntimeException("API error"));

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        Method method = ArticleEmbeddingService.class.getDeclaredMethod("embedViaOpenAiCompatible", String.class, MilvusProperties.Embedding.class);
        method.setAccessible(true);

        assertThrows(RuntimeException.class, () -> {
            try {
                method.invoke(embeddingService, "test text", properties.getEmbedding());
            } catch (InvocationTargetException e) {
                throw (RuntimeException) e.getTargetException();
            }
        });
    }

    @Test
    void testEmbedWithOpenaiProviderSuccess() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");
        properties.setDimension(2);

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenReturn("{\"data\":[{\"embedding\":[0.3, 0.4]}]}");

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        float[] result = embeddingService.embed("test text");
        assertNotNull(result);
        assertEquals(2, result.length);
    }

    @Test
    void testEmbedWithOpenaiProviderFailureFallback() throws Exception {
        properties.getEmbedding().setProvider("openai");
        properties.getEmbedding().setOpenaiBaseUrl("http://localhost:8080");
        properties.getEmbedding().setOpenaiApiKey("test-key");
        properties.getEmbedding().setOpenaiModel("text-embedding-3-small");
        properties.setDimension(128);

        RestClient.RequestBodyUriSpec requestBodyMock = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpecMock = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpecMock = mock(RestClient.ResponseSpec.class);
        RestClient restClientMock = mock(RestClient.class);

        when(restClientMock.post()).thenReturn(requestBodyMock);
        when(requestBodyMock.uri(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.header(anyString(), anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.contentType(any())).thenReturn(bodySpecMock);
        when(bodySpecMock.body(anyString())).thenReturn(bodySpecMock);
        when(bodySpecMock.retrieve()).thenReturn(responseSpecMock);
        when(responseSpecMock.body(String.class)).thenThrow(new RuntimeException("API error"));

        Field restClientField = ArticleEmbeddingService.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(embeddingService, restClientMock);

        float[] result = embeddingService.embed("test text");
        assertNotNull(result);
        assertEquals(128, result.length);
    }
}
