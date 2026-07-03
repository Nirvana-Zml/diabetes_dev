package com.diabetes.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CheckinServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CheckinServiceClient(@Value("${services.checkin.base-url:http://localhost:8084}") String baseUrl,
                                ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecentCheckins(String userId, String difyKey, int days) {
        try {
            String body = restClient.get()
                    .uri("/api/v1/internal/checkin/user/{userId}/recent?days={days}", userId, days)
                    .header("X-Dify-Key", difyKey == null ? "" : difyKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || !data.isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(data, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    public Map<String, Object> getCheckinSummary(String userId, String difyKey, int days) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("recent_days", days);
        summary.put("records", getRecentCheckins(userId, difyKey, days));
        return summary;
    }

    public void applySystemReminderAdjust(String difyKey, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/reminders/system-adjust")
                    .header("X-Dify-Key", difyKey == null ? "" : difyKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ignored) {
            // 提醒调整失败不阻断干预主流程
        }
    }
}
