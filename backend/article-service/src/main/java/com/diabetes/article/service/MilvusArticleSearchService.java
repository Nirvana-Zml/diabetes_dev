package com.diabetes.article.service;

import com.diabetes.article.config.MilvusProperties;
import com.diabetes.article.config.RecommendProperties;
import com.diabetes.article.milvus.ArticleEmbeddingService;
import com.diabetes.article.milvus.MilvusArticleClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MilvusArticleSearchService {

    private final MilvusProperties milvusProperties;
    private final RecommendProperties recommendProperties;
    private final MilvusArticleClient milvusClient;
    private final ArticleEmbeddingService embeddingService;

    public MilvusArticleSearchService(MilvusProperties milvusProperties,
                                      RecommendProperties recommendProperties,
                                      MilvusArticleClient milvusClient,
                                      ArticleEmbeddingService embeddingService) {
        this.milvusProperties = milvusProperties;
        this.recommendProperties = recommendProperties;
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    public boolean isAvailable() {
        return recommendProperties.isMilvusEnabled() && milvusClient.isReady();
    }

    /**
     * @return articleId -> 相似度(0~1)
     */
    public Map<String, Double> searchSimilar(String interestText, List<String> candidateIds, int topK) {
        if (!isAvailable() || interestText == null || interestText.isBlank()) {
            return Map.of();
        }
        float[] query = embeddingService.embed(interestText);
        Set<String> filter = candidateIds == null || candidateIds.isEmpty()
                ? Set.of()
                : Set.copyOf(candidateIds);
        int k = Math.max(topK, filter.isEmpty() ? topK : filter.size());
        Map<String, Double> hits = milvusClient.search(query, k, filter.isEmpty() ? null : filter);
        if (hits.isEmpty() && !filter.isEmpty()) {
            hits = milvusClient.search(query, Math.max(k * 2, 50), filter);
        }
        return hits;
    }
}
