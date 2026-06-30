package com.diabetes.home.config;

import com.diabetes.common.auth.JwtAuthInterceptor;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class HomeConfigTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void adminAuthInterceptorRequiresValidAdminToken() {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        request.addHeader("Authorization", "Basic x");
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

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
        assertNull(noToken.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader("Authorization", "Bearer bad");
        assertTrue(interceptor.preHandle(invalid, new MockHttpServletResponse(), new Object()));
        assertNull(invalid.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));

        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic x");
        assertTrue(interceptor.preHandle(basic, new MockHttpServletResponse(), new Object()));
        assertNull(basic.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));

        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.addHeader("Authorization", "Bearer " + JwtUtil.generateToken(SECRET, "u1", "user", 60));
        assertTrue(interceptor.preHandle(valid, new MockHttpServletResponse(), new Object()));
        assertEquals("u1", valid.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));
    }

    @Test
    void trailingSlashFilterNormalizesUriAndServletPath() throws Exception {
        TrailingSlashNormalizeFilter filter = new TrailingSlashNormalizeFilter();

        MockHttpServletRequest trailing = new MockHttpServletRequest("GET", "/api/v1/chat/");
        trailing.setServletPath("/api/v1/chat/");
        MockFilterChain trailingChain = new MockFilterChain();
        filter.doFilter(trailing, new MockHttpServletResponse(), trailingChain);
        HttpServletRequest normalized = (HttpServletRequest) trailingChain.getRequest();
        assertEquals("/api/v1/chat", normalized.getRequestURI());
        assertEquals("/api/v1/chat", normalized.getServletPath());

        MockHttpServletRequest trailingUriOnly = new MockHttpServletRequest("GET", "/api/v1/chat/");
        trailingUriOnly.setServletPath("/api/v1/chat");
        MockFilterChain trailingUriOnlyChain = new MockFilterChain();
        filter.doFilter(trailingUriOnly, new MockHttpServletResponse(), trailingUriOnlyChain);
        assertEquals("/api/v1/chat", ((HttpServletRequest) trailingUriOnlyChain.getRequest()).getServletPath());

        MockHttpServletRequest rootServletPath = new MockHttpServletRequest("GET", "/api/v1/chat/");
        rootServletPath.setServletPath("/");
        MockFilterChain rootServletPathChain = new MockFilterChain();
        filter.doFilter(rootServletPath, new MockHttpServletResponse(), rootServletPathChain);
        assertEquals("/", ((HttpServletRequest) rootServletPathChain.getRequest()).getServletPath());

        MockHttpServletRequest root = new MockHttpServletRequest("GET", "/");
        MockFilterChain rootChain = new MockFilterChain();
        filter.doFilter(root, new MockHttpServletResponse(), rootChain);
        assertSame(root, rootChain.getRequest());

        MockHttpServletRequest normal = new MockHttpServletRequest("GET", "/api/v1/chat");
        MockFilterChain normalChain = new MockFilterChain();
        filter.doFilter(normal, new MockHttpServletResponse(), normalChain);
        assertSame(normal, normalChain.getRequest());
    }

    @Test
    void webMvcConfigRegistersInterceptorsAndConfigClassConstructs() {
        WebMvcConfig config = new WebMvcConfig(new OptionalJwtInterceptor(SECRET), new AdminAuthInterceptor(SECRET));
        assertDoesNotThrow(() -> config.addInterceptors(new InterceptorRegistry()));
        assertNotNull(new HomeServiceConfig());
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
