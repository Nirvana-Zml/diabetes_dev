package com.diabetes.home.knowledge;

import com.diabetes.home.config.KnowledgeMilvusProperties;
import com.diabetes.home.dify.DifyQaChatContract;
import com.diabetes.home.milvus.KnowledgeEmbeddingService;
import com.diabetes.home.milvus.MilvusKnowledgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeRetrieval {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrieval.class);

    private final KnowledgeMilvusProperties properties;
    private final MilvusKnowledgeClient milvusClient;
    private final KnowledgeEmbeddingService embeddingService;

    public KnowledgeRetrieval(KnowledgeMilvusProperties properties,
                              MilvusKnowledgeClient milvusClient,
                              KnowledgeEmbeddingService embeddingService) {
        this.properties = properties;
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
    }

    public List<DocumentChunk> semanticSearch(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (!milvusClient.isReady()) {
            log.warn("Milvus 不可用，返回空检索结果");
            return List.of();
        }
        float[] vector = embeddingService.embed(query);
        int k = topK > 0 ? topK : properties.getSearchTopK();
        List<DocumentChunk> chunks = milvusClient.search(
                vector, k, DifyQaChatContract.PHASE1_DOC_TYPE, properties.getScoreThreshold());
        if (chunks.isEmpty()) {
            log.warn("Milvus 按 doc_type={} 无结果，尝试全库检索 query={}",
                    DifyQaChatContract.PHASE1_DOC_TYPE, truncate(query, 80));
            chunks = milvusClient.search(vector, k, null, properties.getScoreThreshold());
        }
        if (chunks.isEmpty()) {
            log.warn("Milvus 全库检索仍无结果 query={}", truncate(query, 80));
        }
        return chunks;
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

    public String buildKnowledgeContext(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            String title = chunk.docTitle() == null || chunk.docTitle().isBlank() ? "未知来源" : chunk.docTitle();
            sb.append(String.format("【片段%d | 来源: %s | 相似度: %.3f】%n%s",
                    i + 1, title, chunk.score(), chunk.content() == null ? "" : chunk.content().trim()));
        }
        return sb.toString();
    }

    public List<String> extractSources(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Set<String> sources = new LinkedHashSet<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk.docTitle() != null && !chunk.docTitle().isBlank()) {
                sources.add(chunk.docTitle());
            }
        }
        return new ArrayList<>(sources);
    }
}
