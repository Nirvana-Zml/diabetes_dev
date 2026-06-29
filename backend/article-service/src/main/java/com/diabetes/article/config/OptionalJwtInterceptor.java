package com.diabetes.article.config;

import com.diabetes.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OptionalJwtInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "optionalUserId";

    private final String jwtSecret;

    public OptionalJwtInterceptor(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = JwtUtil.parseToken(jwtSecret, auth.substring(7));
                request.setAttribute(ATTR_USER_ID, claims.getSubject());
            } catch (Exception ignored) {
            }
        }
        return true;
    }
}
