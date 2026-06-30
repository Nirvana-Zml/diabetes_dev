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
public class HealthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HealthServiceClient(@Value("${services.health.base-url:http://localhost:8083}") String baseUrl,
                               ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getLatestHealthProfile(String userId, String difyKey) {
        return fetchInternal("/api/v1/internal/health/user/" + userId + "/latest-record", difyKey);
    }

    public Map<String, Object> getLatestRiskAssessment(String userId, String difyKey) {
        return fetchInternal("/api/v1/internal/health/user/" + userId + "/latest-assessment", difyKey);
    }

    public Map<String, Object> getRiskHistory(String userId, String difyKey, int page, int size) {
        return fetchInternal("/api/v1/internal/health/user/" + userId + "/risk-history?page="
                + page + "&size=" + size, difyKey);
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
                log.warn("health-service 内部接口失败 path={} code={} message={}",
                        path, code, root.path("message").asText(""));
                return new HashMap<>();
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull() || !data.isObject()) {
                return new HashMap<>();
            }
            return objectMapper.convertValue(data, Map.class);
        } catch (Exception e) {
            log.warn("health-service 内部接口调用异常 path={} error={}", path, e.getMessage());
            return new HashMap<>();
        }
    }
}
