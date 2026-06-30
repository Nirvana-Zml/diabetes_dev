package com.diabetes.home.config;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_ADMIN_ID = "currentAdminId";

    private final String jwtSecret;

    public AdminAuthInterceptor(@Value("${jwt.secret}") String jwtSecret) {
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
            Object role = claims.get("role");
            if (role == null || !"admin".equals(role.toString())) {
                throw new BusinessException(403, "需要管理员权限");
            }
            request.setAttribute(ATTR_ADMIN_ID, claims.getSubject());
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "Token 已过期或无效");
        }
    }
}
