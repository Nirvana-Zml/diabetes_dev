package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ArticleEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ArticleEmbeddingService.class);

    private final MilvusProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ArticleEmbeddingService(MilvusProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return zeroVector();
        }
        MilvusProperties.Embedding cfg = properties.getEmbedding();
        if ("openai".equalsIgnoreCase(cfg.getProvider())
                && cfg.getOpenaiBaseUrl() != null && !cfg.getOpenaiBaseUrl().isBlank()) {
            try {
                return embedViaOpenAiCompatible(text, cfg);
            } catch (Exception e) {
                log.warn("OpenAI Embedding 调用失败，降级本地向量: {}", e.getMessage());
            }
        }
        return embedLocalHash(text, properties.getDimension());
    }

    public int dimension() {
        return properties.getDimension();
    }

    private float[] embedViaOpenAiCompatible(String text, MilvusProperties.Embedding cfg) throws Exception {
        String base = cfg.getOpenaiBaseUrl().replaceAll("/+$", "");
        String body = objectMapper.writeValueAsString(Map.of(
                "model", cfg.getOpenaiModel(),
                "input", text
        ));
        String response = restClient.post()
                .uri(base + "/v1/embeddings")
                .header("Authorization", "Bearer " + cfg.getOpenaiApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(response);
        JsonNode embedding = root.path("data").path(0).path("embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new BusinessException(500, "Embedding 响应无效");
        }
        float[] vec = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vec[i] = (float) embedding.get(i).asDouble();
        }
        return normalize(vec);
    }

    /** 本地哈希 Bag-of-words 向量（无需外部 Embedding 服务，开发/演示可用） */
    static float[] embedLocalHash(String text, int dim) {
        float[] vec = new float[dim];
        List<String> tokens = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s,，、；;。.!！?？]+"))
                .filter(t -> t.length() > 1)
                .toList();
        for (String token : tokens) {
            int h = token.hashCode();
            for (int i = 0; i < 4; i++) {
                int idx = Math.floorMod(h + i * 9973, dim);
                vec[idx] += 1.0f;
            }
        }
        return normalize(vec);
    }

    private float[] zeroVector() {
        return new float[properties.getDimension()];
    }

    private static float[] normalize(float[] vec) {
        double norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        if (norm <= 0) {
            return vec;
        }
        double scale = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < vec.length; i++) {
            vec[i] = (float) (vec[i] * scale);
        }
        return vec;
    }
}
