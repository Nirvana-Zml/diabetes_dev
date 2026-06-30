package com.diabetes.common;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.dify.DifyJsonSchema;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.redis.RedisKeys;
import com.diabetes.common.util.IdGenerator;
import com.diabetes.common.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreCommonTest {

    private static final String JWT_SECRET = "diabetes_jwt_secret_key_at_least_32_chars_test";

    @Test
    @DisplayName("ApiResponse 构建成功和失败响应")
    void shouldBuildApiResponses() {
        assertEquals(new ApiResponse<>(200, "success", "data"), ApiResponse.ok("data"));
        assertEquals(new ApiResponse<>(200, "ok", "data"), ApiResponse.ok("ok", "data"));
        assertEquals(new ApiResponse<>(400, "bad", null), ApiResponse.fail(400, "bad"));
    }

    @Test
    @DisplayName("BusinessException 保存业务码和消息")
    void shouldKeepBusinessExceptionCode() {
        BusinessException ex = new BusinessException(409, "conflict");

        assertEquals(409, ex.getCode());
        assertEquals("conflict", ex.getMessage());
    }

    @Test
    @DisplayName("IdGenerator 生成带前缀的 ID")
    void shouldGeneratePrefixedId() {
        String id = IdGenerator.nextId("u_");

        assertTrue(id.startsWith("u_"));
        assertEquals(18, id.length());
    }

    @Test
    @DisplayName("JwtUtil 生成并解析 token")
    void shouldGenerateAndParseJwt() {
        String token = JwtUtil.generateToken(JWT_SECRET, "u_001", "user", 60);

        Claims claims = JwtUtil.parseToken(JWT_SECRET, token);

        assertEquals("u_001", claims.getSubject());
        assertEquals("user", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("RedisKeys 生成业务 key")
    void shouldBuildRedisKeys() {
        assertEquals("diabetes:", RedisKeys.PREFIX);
        assertEquals("diabetes:checkin:today:u1:2026-06-30", RedisKeys.checkinToday("u1", "2026-06-30"));
        assertEquals("diabetes:checkin:stats:u1:week", RedisKeys.checkinStats("u1", "week"));
        assertEquals("diabetes:article:recommend:1:10", RedisKeys.articleRecommend(1, 10));
        assertEquals("diabetes:article:rec:popular:1:10", RedisKeys.articleRecPopular(1, 10));
        assertEquals("diabetes:article:rec:user:u1:1:10", RedisKeys.articleRecPersonalized("u1", 1, 10));
        assertEquals("diabetes:article:rec:user:u1:*", RedisKeys.articleRecPersonalizedPattern("u1"));
        assertEquals("diabetes:article:rec:popular:*", RedisKeys.articleRecPopularPattern());
        assertEquals("diabetes:article:list:all:1:10", RedisKeys.articleList(null, 1, 10));
        assertEquals("diabetes:article:list:2:1:10", RedisKeys.articleList(2, 1, 10));
        assertEquals("diabetes:article:recommend:*", RedisKeys.articleRecommendPattern());
        assertEquals("diabetes:article:list:*", RedisKeys.articleListPattern());
    }

    @Test
    @DisplayName("DifyJsonSchema 构建 schema 和转换值")
    void shouldBuildDifySchemasAndConvertValues() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> props = Map.of("name", DifyJsonSchema.stringType());
        Map<String, Object> root = DifyJsonSchema.rootObject(props);

        assertEquals("object", root.get("type"));
        assertEquals(props, root.get("properties"));
        assertEquals(Map.of(), DifyJsonSchema.rootObject(null).get("properties"));
        assertEquals(true, root.get("additionalProperties"));
        assertEquals("object", DifyJsonSchema.object(null).get("type"));
        assertEquals(props, DifyJsonSchema.object(props).get("properties"));
        assertEquals(Map.of("type", "number"), DifyJsonSchema.numberType());
        assertEquals(Map.of("type", "integer"), DifyJsonSchema.integerType());
        assertEquals(Map.of("type", "boolean"), DifyJsonSchema.booleanType());
        assertEquals(Map.of("type", "array", "items", DifyJsonSchema.stringType()),
                DifyJsonSchema.array(DifyJsonSchema.stringType()));
        assertEquals(Map.of("a", 1), DifyJsonSchema.flatWorkflowInputs(Map.of("a", 1)));
        assertEquals(Map.of(), DifyJsonSchema.sanitize(null));
        Map<String, Object> payloadWithNull = new LinkedHashMap<>();
        payloadWithNull.put("a", 1);
        payloadWithNull.put("b", null);
        assertEquals(Map.of("a", 1), DifyJsonSchema.sanitize(payloadWithNull));
        assertEquals("", DifyJsonSchema.asString(null));
        assertEquals("123", DifyJsonSchema.asString(123));
        assertEquals("{}", DifyJsonSchema.asJsonString(null, mapper));
        assertEquals("{}", DifyJsonSchema.asJsonString("   ", mapper));
        assertEquals("{\"a\":1}", DifyJsonSchema.asJsonString(Map.of("a", 1), mapper));
        assertEquals("raw", DifyJsonSchema.asJsonString("raw", mapper));
        assertEquals("[]", DifyJsonSchema.asJsonArrayString(null, mapper));
        assertEquals("[]", DifyJsonSchema.asJsonArrayString("   ", mapper));
        assertEquals("[1,2]", DifyJsonSchema.asJsonArrayString(List.of(1, 2), mapper));
        assertEquals("raw", DifyJsonSchema.asJsonArrayString("raw", mapper));
        assertEquals(12, DifyJsonSchema.asInteger(12.9));
        assertEquals(12, DifyJsonSchema.asInteger("12.9"));
        assertEquals(0, DifyJsonSchema.asInteger(null));
        assertEquals(0, DifyJsonSchema.asInteger("bad"));
        assertEquals(1.5, DifyJsonSchema.asNumber(1.5));
        assertEquals(2.5, DifyJsonSchema.asNumber("2.5"));
        assertEquals(0.0, DifyJsonSchema.asNumber(null));
        assertEquals(0.0, DifyJsonSchema.asNumber("bad"));
        assertEquals(Map.of("a", 1), DifyJsonSchema.asObject(Map.of("a", 1)));
        assertTrue(DifyJsonSchema.asObject("bad").isEmpty());
        assertEquals(List.of(Map.of("a", 1)), DifyJsonSchema.asObjectList(List.of(Map.of("a", 1), "bad")));
        assertTrue(DifyJsonSchema.asObjectList("bad").isEmpty());
        List<Object> listWithNull = new java.util.ArrayList<>();
        listWithNull.add("a");
        listWithNull.add(1);
        listWithNull.add(null);
        assertEquals(List.of("a", "1"), DifyJsonSchema.asStringList(listWithNull));
        assertTrue(DifyJsonSchema.asStringList("bad").isEmpty());
    }

    @Test
    @DisplayName("DifyJsonSchema 包装工作流输入")
    void shouldWrapWorkflowInputs() {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> objectInputs = DifyJsonSchema.wrapWorkflowInputs(
                "payload", payloadWithNull(), "object", mapper);
        Map<String, Object> stringInputs = DifyJsonSchema.wrapWorkflowInputs(
                "payload", Map.of("a", 1), "string", mapper);

        assertEquals(Map.of("a", 1), objectInputs.get("payload"));
        assertEquals("{\"a\":1}", stringInputs.get("payload"));
    }

    @Test
    @DisplayName("DifyJsonSchema 序列化失败时返回默认值")
    void shouldFallbackWhenJsonSerializationFails() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});

        assertEquals("{}", DifyJsonSchema.asJsonString(Map.of("a", 1), mapper));
        assertEquals("[]", DifyJsonSchema.asJsonArrayString(List.of(1), mapper));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DifyJsonSchema.wrapWorkflowInputs("payload", Map.of("a", 1), "string", mapper));
        assertTrue(ex.getMessage().startsWith("构建 Dify inputs 失败"));
    }

    @Test
    @DisplayName("工具类私有构造器可通过反射覆盖")
    void shouldCoverUtilityConstructors() throws Exception {
        assertPrivateConstructor(IdGenerator.class);
        assertPrivateConstructor(JwtUtil.class);
        assertPrivateConstructor(RedisKeys.class);
        assertPrivateConstructor(DifyJsonSchema.class);
    }

    private static void assertPrivateConstructor(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    private static Map<String, Object> payloadWithNull() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("a", 1);
        payload.put("b", null);
        return payload;
    }
}
