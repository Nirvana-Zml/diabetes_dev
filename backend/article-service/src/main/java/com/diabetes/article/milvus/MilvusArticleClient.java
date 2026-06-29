package com.diabetes.article.milvus;

import com.diabetes.article.config.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MilvusArticleClient {

    private static final Logger log = LoggerFactory.getLogger(MilvusArticleClient.class);

    private final MilvusProperties properties;
    private MilvusServiceClient client;
    private volatile boolean ready;

    public MilvusArticleClient(MilvusProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("Milvus 未启用 (milvus.enabled=false)");
            return;
        }
        try {
            client = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(properties.getHost())
                    .withPort(properties.getPort())
                    .build());
            ensureCollection();
            ready = true;
            log.info("Milvus 已连接 {}:{} collection={}", properties.getHost(), properties.getPort(),
                    properties.getCollection());
        } catch (Exception e) {
            log.warn("Milvus 初始化失败，Phase3 将降级本地语义: {}", e.getMessage());
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

    public void upsert(String articleId, int category, float[] embedding) {
        if (!isReady()) {
            return;
        }
        List<String> ids = List.of(articleId);
        List<Long> categories = List.of((long) category);
        List<List<Float>> vectors = List.of(toList(embedding));

        UpsertParam param = UpsertParam.newBuilder()
                .withCollectionName(properties.getCollection())
                .withFields(List.of(
                        new InsertParam.Field("article_id", ids),
                        new InsertParam.Field("category", categories),
                        new InsertParam.Field("embedding", vectors)
                ))
                .build();
        R<MutationResult> resp = client.upsert(param);
        checkResponse(resp, "upsert");
    }

    public void delete(String articleId) {
        if (!isReady() || articleId == null || articleId.isBlank()) {
            return;
        }
        DeleteParam param = DeleteParam.newBuilder()
                .withCollectionName(properties.getCollection())
                .withExpr("article_id == \"" + escapeExpr(articleId) + "\"")
                .build();
        R<MutationResult> resp = client.delete(param);
        checkResponse(resp, "delete");
    }

    /**
     * @return articleId -> cosine similarity (0~1)
     */
    public Map<String, Double> search(float[] queryVector, int topK, Set<String> candidateFilter) {
        if (!isReady() || queryVector == null) {
            return Map.of();
        }
        MetricType metric = parseMetric(properties.getMetricType());
        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(properties.getCollection())
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withMetricType(metric)
                .withTopK(topK)
                .withVectors(List.of(toList(queryVector)))
                .withVectorFieldName("embedding")
                .withOutFields(List.of("article_id", "category"))
                .withParams("{\"nprobe\":16}");

        R<SearchResults> resp = client.search(builder.build());
        checkResponse(resp, "search");
        if (resp.getData() == null) {
            return Map.of();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        if (scores == null || scores.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (SearchResultsWrapper.IDScore score : scores) {
            String articleId = score.getStrID();
            if (articleId == null || articleId.isBlank()) {
                Object idField = score.get("article_id");
                articleId = idField != null ? idField.toString() : null;
            }
            if (articleId == null || articleId.isBlank()) {
                continue;
            }
            if (candidateFilter != null && !candidateFilter.isEmpty() && !candidateFilter.contains(articleId)) {
                continue;
            }
            double sim = toSimilarity(score.getScore(), metric);
            result.put(articleId, sim);
        }
        return result;
    }

    private void ensureCollection() {
        String collection = properties.getCollection();
        R<Boolean> exists = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        checkResponse(exists, "hasCollection");
        if (Boolean.TRUE.equals(exists.getData())) {
            loadCollection(collection);
            return;
        }

        FieldType articleId = FieldType.newBuilder()
                .withName("article_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        FieldType category = FieldType.newBuilder()
                .withName("category")
                .withDataType(DataType.Int64)
                .build();
        FieldType embedding = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(properties.getDimension())
                .build();

        CreateCollectionParam create = CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withFieldTypes(List.of(articleId, category, embedding))
                .build();
        R<RpcStatus> createResp = client.createCollection(create);
        checkResponse(createResp, "createCollection");

        IndexType indexType = parseIndexType(properties.getIndexType());
        MetricType metric = parseMetric(properties.getMetricType());
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("embedding")
                .withIndexType(indexType)
                .withMetricType(metric)
                .withExtraParam("{\"nlist\":" + properties.getIndexNlist() + "}")
                .build();
        R<RpcStatus> indexResp = client.createIndex(indexParam);
        checkResponse(indexResp, "createIndex");

        loadCollection(collection);
        log.info("Milvus collection [{}] 已创建并加载", collection);
    }

    private void loadCollection(String collection) {
        R<RpcStatus> loadResp = client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection)
                .build());
        checkResponse(loadResp, "loadCollection");
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
        // COSINE: Milvus 返回 distance，similarity = 1 - distance（对于归一化向量）
        return Math.max(0, Math.min(1, 1.0 - score));
    }

    private static MetricType parseMetric(String name) {
        try {
            return MetricType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return MetricType.COSINE;
        }
    }

    private static IndexType parseIndexType(String name) {
        try {
            return IndexType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return IndexType.IVF_FLAT;
        }
    }

    private static String escapeExpr(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void checkResponse(R<?> response, String op) {
        if (response == null) {
            throw new IllegalStateException("Milvus " + op + " 无响应");
        }
        if (response.getStatus() == R.Status.Success.getCode()) {
            return;
        }
        throw new IllegalStateException("Milvus " + op + " 失败: " + response.getMessage());
    }
}
