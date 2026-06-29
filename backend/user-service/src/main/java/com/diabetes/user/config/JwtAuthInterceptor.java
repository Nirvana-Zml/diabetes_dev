package com.diabetes.user.config;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "currentUserId";

    private final String jwtSecret;

    public JwtAuthInterceptor(@Value("${jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(401, "未登录或 Token 无效");
        }
        try {
            Claims claims = JwtUtil.parseToken(jwtSecret, auth.substring(7));
            request.setAttribute(ATTR_USER_ID, claims.getSubject());
            return true;
        } catch (Exception e) {
            throw new BusinessException(401, "Token 已过期或无效");
        }
    }
}
