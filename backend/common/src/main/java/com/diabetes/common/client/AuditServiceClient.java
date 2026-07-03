package com.diabetes.common.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志写入客户端（fire-and-forget，失败不影响主流程）。
 */
@Component
public class AuditServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceClient.class);

    private final RestClient restClient;
    private final String internalKey;

    public AuditServiceClient(@Value("${services.audit.base-url:http://localhost:8088}") String baseUrl,
                              @Value("${dify-internal.key:LoginAuth2026}") String internalKey) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalKey = internalKey;
    }

    public void log(String userId,
                    String action,
                    String resource,
                    Map<String, Object> detail,
                    String ipAddress,
                    String userAgent,
                    int result) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("action", action);
        body.put("resource", resource);
        if (detail != null && !detail.isEmpty()) {
            body.put("detail", detail);
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            body.put("ipAddress", ipAddress);
        }
        if (userAgent != null && !userAgent.isBlank()) {
            body.put("userAgent", userAgent);
        }
        body.put("result", result);
        postInternal(body);
    }

    private void postInternal(Map<String, Object> body) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/audit/logs")
                    .header("X-Dify-Key", internalKey == null ? "" : internalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("审计日志写入失败: action={}, userId={}, reason={}",
                    body.get("action"), body.get("userId"), e.getMessage());
        }
    }
}
