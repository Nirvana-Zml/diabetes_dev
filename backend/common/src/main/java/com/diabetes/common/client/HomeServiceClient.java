package com.diabetes.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 科普首页 / 知识库内部 API 客户端。
 */
@Component
public class HomeServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HomeServiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HomeServiceClient(@Value("${services.home.base-url:http://localhost:8082}") String baseUrl,
                             ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Milvus 语义检索，返回拼接后的 knowledge_context 文本。
     */
    public String searchKnowledgeContext(String query, int topK, String difyKey) {
        if (query == null || query.isBlank()) {
            return "";
        }
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/internal/home/knowledge/search")
                            .queryParam("query", query)
                            .queryParam("topK", topK)
                            .build())
                    .header("X-Dify-Key", difyKey == null ? "" : difyKey)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(200) != 200) {
                log.warn("home-service 知识检索业务失败 baseUrl={} code={} message={}",
                        baseUrl, root.path("code").asInt(), root.path("message").asText(""));
                return "";
            }
            JsonNode data = root.path("data");
            String context = data.path("knowledgeContext").asText("");
            if (context.isBlank()) {
                context = data.path("knowledge_context").asText("");
            }
            int count = data.path("count").asInt(-1);
            if (context.isBlank()) {
                log.warn("home-service 知识检索返回空上下文 baseUrl={} count={} query={}",
                        baseUrl, count, truncate(query, 80));
            } else {
                log.debug("home-service 知识检索成功 baseUrl={} count={} contextLen={}",
                        baseUrl, count, context.length());
            }
            return context;
        } catch (Exception e) {
            log.warn("home-service 知识检索失败 baseUrl={} error={}", baseUrl, e.getMessage());
            return "";
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
