package com.diabetes.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class PlanServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PlanServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PlanServiceClient(@Value("${services.plan.base-url:http://localhost:8086}") String baseUrl,
                             ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getPlanHistory(String userId, String difyKey, int page, int size) {
        return fetchInternal("/api/v1/internal/plan/user/" + userId + "/history?page="
                + page + "&size=" + size, difyKey);
    }

    public Map<String, Object> getLatestPlan(String userId, String difyKey) {
        return fetchInternal("/api/v1/internal/plan/user/" + userId + "/latest", difyKey);
    }

    private Map<String, Object> fetchInternal(String path, String difyKey) {
        try {
            String body = restClient.get()
                    .uri(path)
                    .header("X-Dify-Key", difyKey == null ? "" : difyKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(200);
            if (code != 200) {
                log.warn("plan-service 内部接口失败 path={} code={}", path, code);
                return new HashMap<>();
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return new HashMap<>();
            }
            if (data.isObject()) {
                return objectMapper.convertValue(data, Map.class);
            }
            return Map.of("items", objectMapper.convertValue(data, Object.class));
        } catch (Exception e) {
            log.warn("plan-service 内部接口调用异常 path={} error={}", path, e.getMessage());
            return new HashMap<>();
        }
    }
}
