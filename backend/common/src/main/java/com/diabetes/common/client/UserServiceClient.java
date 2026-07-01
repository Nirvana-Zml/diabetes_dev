package com.diabetes.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UserServiceClient(@Value("${services.user.base-url:http://localhost:8081}") String baseUrl,
                             ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getUserProfile(String userId, String difyKey) {
        return fetchInternal("/api/v1/internal/user/" + userId + "/profile", difyKey);
    }

    public Map<String, Object> getUserOverview(String userId, String difyKey) {
        return fetchInternal("/api/v1/internal/user/" + userId + "/overview", difyKey);
    }

    /**
     * 创建用户消息（工作流完成/失败时调用）。失败时静默忽略，不影响主流程。
     */
    public void createMessage(String difyKey, Map<String, Object> body) {
        postInternal("/api/v1/internal/messages", difyKey, body);
    }

    private Map<String, Object> fetchInternal(String path, String difyKey) {
        try {
            String body = restClient.get()
                    .uri(path)
                    .header("X-Dify-Key", difyKey == null ? "" : difyKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return Map.of();
            }
            return objectMapper.convertValue(data, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void postInternal(String path, String difyKey, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri(path)
                    .header("X-Dify-Key", difyKey == null ? "" : difyKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ignored) {
            // 消息写入失败不阻断业务
        }
    }
}
