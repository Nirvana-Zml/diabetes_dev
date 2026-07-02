package com.diabetes.article.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ArticleConfigTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void adminAuthInterceptorRequiresValidAdminToken() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        request.addHeader("Authorization", "Basic x");
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest userRequest = new MockHttpServletRequest();
        userRequest.addHeader("Authorization", "Bearer " + JwtUtil.generateToken(SECRET, "u1", "user", 60));
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(userRequest, new MockHttpServletResponse(), new Object()));
        assertEquals(403, forbidden.getCode());

        MockHttpServletRequest noRoleRequest = new MockHttpServletRequest();
        noRoleRequest.addHeader("Authorization", "Bearer " + tokenWithoutRole());
        assertEquals(403, assertThrows(BusinessException.class,
                () -> interceptor.preHandle(noRoleRequest, new MockHttpServletResponse(), new Object())).getCode());

        MockHttpServletRequest badToken = new MockHttpServletRequest();
        badToken.addHeader("Authorization", "Bearer bad");
        assertEquals(401, assertThrows(BusinessException.class,
                () -> interceptor.preHandle(badToken, new MockHttpServletResponse(), new Object())).getCode());

        MockHttpServletRequest adminRequest = new MockHttpServletRequest();
        adminRequest.addHeader("Authorization", "Bearer " + JwtUtil.generateToken(SECRET, "admin1", "admin", 60));
        assertTrue(interceptor.preHandle(adminRequest, new MockHttpServletResponse(), new Object()));
        assertEquals("admin1", adminRequest.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_ID));
    }

    @Test
    void optionalJwtInterceptorNeverBlocksAndSetsUserWhenTokenValid() {
        OptionalJwtInterceptor interceptor = new OptionalJwtInterceptor(SECRET);
        MockHttpServletRequest noToken = new MockHttpServletRequest();
        assertTrue(interceptor.preHandle(noToken, new MockHttpServletResponse(), new Object()));
        assertNull(noToken.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID));

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader("Authorization", "Bearer bad");
        assertTrue(interceptor.preHandle(invalid, new MockHttpServletResponse(), new Object()));
        assertNull(invalid.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID));

        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.addHeader("Authorization", "Bearer " + JwtUtil.generateToken(SECRET, "u1", "user", 60));
        assertTrue(interceptor.preHandle(valid, new MockHttpServletResponse(), new Object()));
        assertEquals("u1", valid.getAttribute(OptionalJwtInterceptor.ATTR_USER_ID));
    }

    @Test
    void recommendProperties_defaults() {
        RecommendProperties properties = new RecommendProperties();
        assertTrue(properties.isPhase1Enabled());
        assertTrue(properties.isPhase2Enabled());
        assertTrue(properties.isPhase3Enabled());
        assertFalse(properties.isMilvusEnabled());
        assertTrue(properties.isPhase4DifyEnabled());
        assertEquals(20, properties.getCacheTtlMinutes());
        assertEquals(20, properties.getDifyTopN());
        assertEquals(200, properties.getCandidateLimit());
        assertEquals(1, properties.getPhase2MinCoReaders());
    }

    @Test
    void recommendProperties_setters() {
        RecommendProperties properties = new RecommendProperties();
        properties.setPhase1Enabled(false);
        properties.setPhase2Enabled(false);
        properties.setPhase2MinCoReaders(2);
        properties.setPhase3Enabled(false);
        properties.setMilvusEnabled(true);
        properties.setPhase4DifyEnabled(false);
        properties.setCacheTtlMinutes(30);
        properties.setDifyTopN(10);
        properties.setCandidateLimit(100);
        assertFalse(properties.isPhase1Enabled());
        assertFalse(properties.isPhase2Enabled());
        assertEquals(2, properties.getPhase2MinCoReaders());
        assertFalse(properties.isPhase3Enabled());
        assertTrue(properties.isMilvusEnabled());
        assertFalse(properties.isPhase4DifyEnabled());
        assertEquals(30, properties.getCacheTtlMinutes());
        assertEquals(10, properties.getDifyTopN());
        assertEquals(100, properties.getCandidateLimit());
    }

    @Test
    void milvusProperties_settersAndEmbedding() {
        MilvusProperties properties = new MilvusProperties();
        properties.setEnabled(true);
        properties.setHost("milvus.local");
        properties.setPort(19531);
        properties.setCollection("articles");
        properties.setDimension(512);
        properties.setMetricType("IP");
        properties.setIndexType("HNSW");
        properties.setIndexNlist(64);
        properties.setSyncOnStartup(false);
        assertTrue(properties.isEnabled());
        assertEquals("milvus.local", properties.getHost());
        assertEquals(19531, properties.getPort());
        assertEquals("articles", properties.getCollection());
        assertEquals(512, properties.getDimension());
        assertEquals("IP", properties.getMetricType());
        assertEquals("HNSW", properties.getIndexType());
        assertEquals(64, properties.getIndexNlist());
        assertFalse(properties.isSyncOnStartup());

        MilvusProperties.Embedding embedding = properties.getEmbedding();
        embedding.setProvider("openai");
        embedding.setOpenaiBaseUrl("http://openai");
        embedding.setOpenaiApiKey("key");
        embedding.setOpenaiModel("embed-model");
        assertEquals("openai", embedding.getProvider());
        assertEquals("http://openai", embedding.getOpenaiBaseUrl());
        assertEquals("key", embedding.getOpenaiApiKey());
        assertEquals("embed-model", embedding.getOpenaiModel());
    }

    @Test
    void webMvcConfigRegistersInterceptors() {
        WebMvcConfig config = new WebMvcConfig(
                mock(JwtAuthInterceptor.class),
                new OptionalJwtInterceptor(SECRET),
                new AdminAuthInterceptor(SECRET));
        assertDoesNotThrow(() -> config.addInterceptors(new InterceptorRegistry()));
    }

    private static String tokenWithoutRole() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("u1")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
