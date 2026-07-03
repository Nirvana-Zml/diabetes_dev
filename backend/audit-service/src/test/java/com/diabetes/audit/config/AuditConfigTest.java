package com.diabetes.audit.config;

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

class AuditConfigTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void adminAuthInterceptorRequiresValidAdminToken() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest invalidPrefix = new MockHttpServletRequest();
        invalidPrefix.addHeader("Authorization", "Basic token");
        assertEquals(401, assertThrows(BusinessException.class,
                () -> interceptor.preHandle(invalidPrefix, new MockHttpServletResponse(), new Object())).getCode());

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
    void webMvcConfigRegistersAdminInterceptor() {
        WebMvcConfig config = new WebMvcConfig(new AdminAuthInterceptor(SECRET));
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
