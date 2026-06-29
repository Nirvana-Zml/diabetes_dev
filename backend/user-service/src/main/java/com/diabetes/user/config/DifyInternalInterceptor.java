package com.diabetes.user.config;

import com.diabetes.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DifyInternalInterceptor implements HandlerInterceptor {

    private final String internalKey;

    public DifyInternalInterceptor(@Value("${dify.internal-key}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String key = request.getHeader("X-Dify-Key");
        if (key == null || !key.equals(internalKey)) {
            throw new BusinessException(403, "内部接口密钥无效");
        }
        return true;
    }
}
