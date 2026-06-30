package com.diabetes.home.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Boot 3 默认不再匹配尾斜杠路径，统一去掉 URI 末尾的 {@code /}（根路径除外）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrailingSlashNormalizeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.length() > 1 && uri.endsWith("/")) {
            filterChain.doFilter(new TrailingSlashStripRequestWrapper(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class TrailingSlashStripRequestWrapper extends HttpServletRequestWrapper {

        private final String normalizedUri;

        TrailingSlashStripRequestWrapper(HttpServletRequest request) {
            super(request);
            String uri = request.getRequestURI();
            this.normalizedUri = uri.substring(0, uri.length() - 1);
        }

        @Override
        public String getRequestURI() {
            return normalizedUri;
        }

        @Override
        public String getServletPath() {
            String path = super.getServletPath();
            if (path.length() > 1 && path.endsWith("/")) {
                return path.substring(0, path.length() - 1);
            }
            return path;
        }
    }
}
