package com.diabetes.home.milvus;

import com.diabetes.home.config.KnowledgeMilvusProperties;
import com.diabetes.home.knowledge.DocumentChunk;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MilvusKnowledgeClient {

    private static final Logger log = LoggerFactory.getLogger(MilvusKnowledgeClient.class);

    private final KnowledgeMilvusProperties properties;
    private MilvusServiceClient client;
    private volatile boolean ready;

    public MilvusKnowledgeClient(KnowledgeMilvusProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("医学知识库 Milvus 未启用 (knowledge.milvus.enabled=false)");
            return;
        }
        try {
            client = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(properties.getHost())
                    .withPort(properties.getPort())
                    .build());
            String collection = properties.getCollection();
            R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .build());
            if (!Boolean.TRUE.equals(exists.getData())) {
                log.warn("Milvus collection [{}] 不存在，知识检索将降级为空结果", collection);
                closeClient();
                return;
            }
            R<?> loadResp = client.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collection)
                    .build());
            if (loadResp.getStatus() != R.Status.Success.getCode()) {
                throw new IllegalStateException("loadCollection 失败: " + loadResp.getMessage());
            }
            ready = true;
            log.info("Milvus 知识库已连接 {}:{} collection={}", properties.getHost(), properties.getPort(), collection);
        } catch (Exception e) {
            log.warn("Milvus 知识库初始化失败: {}", e.getMessage());
            closeClient();
        }
    }

    @PreDestroy
    public void destroy() {
        closeClient();
    }

    public boolean isReady() {
        return properties.isEnabled() && ready && client != null;
    }

    public List<DocumentChunk> search(float[] queryVector, int topK, String docType, double scoreThreshold) {
        if (!isReady() || queryVector == null) {
            return List.of();
        }
        MetricType metric = parseMetric(properties.getMetricType());
        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(properties.getCollection())
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withMetricType(metric)
                .withTopK(topK)
                .withVectors(List.of(toList(queryVector)))
                .withVectorFieldName("vector")
                .withOutFields(List.of("content", "doc_title", "doc_source", "doc_type", "chunk_index"))
                .withParams("{\"nprobe\":16}");
        if (docType != null && !docType.isBlank()) {
            builder.withExpr("doc_type == \"" + escapeExpr(docType) + "\"");
        }

        R<SearchResults> resp = client.search(builder.build());
        if (resp.getStatus() != R.Status.Success.getCode() || resp.getData() == null) {
            log.warn("Milvus search 失败: {}", resp.getMessage());
            return List.of();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> result = new ArrayList<>();
        for (SearchResultsWrapper.IDScore score : scores) {
            double sim = toSimilarity(score.getScore(), metric);
            if (sim < scoreThreshold) {
                continue;
            }
            String id = score.getStrID();
            if (id == null || id.isBlank()) {
                id = String.valueOf(score.getLongID());
            }
            result.add(new DocumentChunk(
                    id,
                    fieldAsString(score, "content"),
                    fieldAsString(score, "doc_title"),
                    fieldAsString(score, "doc_source"),
                    fieldAsString(score, "doc_type"),
                    sim
            ));
        }
        return result;
    }

    private static String fieldAsString(SearchResultsWrapper.IDScore score, String field) {
        Object val = score.get(field);
        return val == null ? "" : val.toString();
    }

    private void closeClient() {
        ready = false;
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
        }
    }

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private static double toSimilarity(float score, MetricType metric) {
        if (metric == MetricType.IP) {
            return Math.max(0, Math.min(1, score));
        }
        if (metric == MetricType.L2) {
            return 1.0 / (1.0 + score);
        }
        return Math.max(0, Math.min(1, 1.0 - score));
    }

    private static MetricType parseMetric(String name) {
        try {
            return MetricType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return MetricType.COSINE;
        }
    }

    private static String escapeExpr(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
