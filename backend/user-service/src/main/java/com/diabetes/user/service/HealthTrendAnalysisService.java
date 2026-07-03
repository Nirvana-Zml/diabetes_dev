package com.diabetes.user.service;

import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.user.dify.DifyHealthTrendWorkflowContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HealthTrendAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(HealthTrendAnalysisService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ConcurrentHashMap<String, CachedTrendAnalysis> trendCache = new ConcurrentHashMap<>();

    private final HealthServiceClient healthServiceClient;
    private final DifyClient difyClient;
    private final ObjectMapper objectMapper;
    private final String difyApiKey;
    private final String difyResponseMode;
    private final String difyInternalKey;

    public HealthTrendAnalysisService(HealthServiceClient healthServiceClient,
                                      DifyClient difyClient,
                                      ObjectMapper objectMapper,
                                      @Value("${dify.workflows.health-trend.api-key:}") String difyApiKey,
                                      @Value("${dify.workflows.health-trend.response-mode:blocking}") String difyResponseMode,
                                      @Value("${dify-internal.key:}") String difyInternalKey) {
        this.healthServiceClient = healthServiceClient;
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.difyApiKey = difyApiKey;
        this.difyResponseMode = difyResponseMode == null ? "blocking" : difyResponseMode.trim();
        this.difyInternalKey = difyInternalKey;
    }

    public Map<String, Object> analyze(String userId, int limit, boolean forceRefresh) {
        List<Map<String, Object>> history = healthServiceClient.getHealthHistory(userId, difyInternalKey, limit);
        Map<String, Object> result = new LinkedHashMap<>();

        if (history == null || history.size() < 2) {
            result.put("summary", "数据不足，请继续记录健康指标后再查看趋势分析。");
            result.put("riskLevel", "normal");
            result.put("bmiTrend", List.of());
            result.put("glucoseTrend", List.of());
            result.put("bpTrend", List.of());
            result.put("anomalies", List.of());
            result.put("source", "local");
            result.put("cached", false);
            return result;
        }

        Map<String, Object> baseline = history.get(0);
        Map<String, Object> localTrend = buildLocalTrendLines(history);
        result.putAll(localTrend);

        String fingerprint = computeFingerprint(history);
        String cacheKey = cacheKey(userId, limit);
        CachedTrendAnalysis cached = trendCache.get(cacheKey);
        boolean dataChanged = cached != null && !fingerprint.equals(cached.fingerprint());

        if (!forceRefresh && cached != null && fingerprint.equals(cached.fingerprint())) {
            result.putAll(cached.aiResult());
            result.put("cached", true);
            return result;
        }

        boolean shouldCallDify = forceRefresh || dataChanged;
        Map<String, Object> difyTrend = null;
        if (shouldCallDify && difyApiKey != null && !difyApiKey.isBlank()) {
            try {
                difyTrend = callDify(userId, history, baseline);
                mergeDifyResult(result, difyTrend);
                result.put("source", "dify");
                trendCache.put(cacheKey, new CachedTrendAnalysis(fingerprint, copyAiResult(result)));
            } catch (Exception e) {
                log.warn("健康趋势 Dify 调用失败 userId={} error={}", userId, e.getMessage());
                result.put("summary", cached != null
                        ? String.valueOf(cached.aiResult().getOrDefault("summary", "趋势数据已更新，AI 解读暂不可用。"))
                        : "趋势数据已更新，AI 解读暂不可用。");
                result.put("riskLevel", cached != null
                        ? cached.aiResult().getOrDefault("riskLevel", "normal")
                        : "normal");
                result.put("source", cached != null ? cached.aiResult().getOrDefault("source", "local") : "local");
            }
        } else if (cached != null) {
            result.putAll(cached.aiResult());
            result.put("source", cached.aiResult().getOrDefault("source", "local"));
            result.put("cached", true);
            return result;
        } else {
            result.put("summary", buildLocalSummary(history));
            result.put("riskLevel", "normal");
            result.put("source", "local");
        }
        result.put("cached", false);
        return result;
    }

    public void invalidateCache(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String prefix = userId + ":";
        trendCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String cacheKey(String userId, int limit) {
        return userId + ":" + limit;
    }

    private Map<String, Object> copyAiResult(Map<String, Object> result) {
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("summary", result.get("summary"));
        ai.put("riskLevel", result.get("riskLevel"));
        ai.put("anomalies", result.get("anomalies"));
        ai.put("source", result.get("source"));
        return ai;
    }

    private String computeFingerprint(List<Map<String, Object>> history) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : history) {
            sb.append(stringValue(row.get("recordId"), row.get("record_id"))).append('|');
            sb.append(stringValue(row.get("recordedAt"), row.get("recorded_at"))).append('|');
            sb.append(numberValue(row.get("fastingGlucose"), row.get("fasting_glucose"))).append('|');
            sb.append(numberValue(row.get("bmi"))).append('|');
            sb.append(numberValue(row.get("systolicBp"), row.get("systolic_bp"))).append('|');
            sb.append(numberValue(row.get("diastolicBp"), row.get("diastolic_bp"))).append(';');
        }
        return sb.toString();
    }

    private record CachedTrendAnalysis(String fingerprint, Map<String, Object> aiResult) {
    }

    public Map<String, Object> callDifyTrendOnly(String userId, int limit) {
        List<Map<String, Object>> history = healthServiceClient.getHealthHistory(userId, difyInternalKey, limit);
        if (history == null || history.size() < 2) {
            return Map.of();
        }
        try {
            return callDify(userId, history, history.get(0));
        } catch (Exception e) {
            log.warn("Dify trend only failed: {}", e.getMessage());
            return Map.of();
        }
    }

    public List<Map<String, Object>> fetchHistory(String userId, int limit) {
        return healthServiceClient.getHealthHistory(userId, difyInternalKey, limit);
    }

    public Map<String, Object> getWorkflowSpec(String difyBaseUrl) {
        return DifyHealthTrendWorkflowContract.workflowSpec(difyBaseUrl, difyApiKey, difyResponseMode);
    }

    private Map<String, Object> callDify(String userId, List<Map<String, Object>> history,
                                         Map<String, Object> baseline) throws Exception {
        Map<String, Object> inputs = DifyHealthTrendWorkflowContract.buildInputs(objectMapper, history, baseline);
        JsonNode response = difyClient.runWorkflowBlocking(difyApiKey, userId, inputs, difyResponseMode);
        assertWorkflowSucceeded(response);
        return parseTrendAnalysis(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTrendAnalysis(JsonNode response) {
        JsonNode node = response.path("data").path("outputs").path(DifyHealthTrendWorkflowContract.OUTPUT_KEY);
        if (node.isMissingNode() || node.isNull()) {
            node = response.path("outputs").path(DifyHealthTrendWorkflowContract.OUTPUT_KEY);
        }
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("缺少 trend_analysis 输出");
        }
        Map<String, Object> raw = objectMapper.convertValue(node, Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", stringValue(raw.get("summary")));
        result.put("riskLevel", stringValue(raw.get("risk_level"), raw.get("riskLevel")));
        result.put("bmiTrend", trendObject(raw.get("bmi_trend"), raw.get("bmiTrend")));
        result.put("glucoseTrend", trendObject(raw.get("glucose_trend"), raw.get("glucoseTrend")));
        result.put("bpTrend", bpTrendObject(raw.get("bp_trend"), raw.get("bpTrend")));
        result.put("anomalies", normalizeAnomalies(raw.get("anomalies")));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void mergeDifyResult(Map<String, Object> target, Map<String, Object> difyTrend) {
        target.put("summary", difyTrend.get("summary"));
        target.put("riskLevel", difyTrend.get("riskLevel"));
        Object bmi = difyTrend.get("bmiTrend");
        if (bmi instanceof Map<?, ?> map && map.get("data_points") instanceof List<?> points && !points.isEmpty()) {
            target.put("bmiTrend", points);
        }
        Object glucose = difyTrend.get("glucoseTrend");
        if (glucose instanceof Map<?, ?> map && map.get("data_points") instanceof List<?> points && !points.isEmpty()) {
            target.put("glucoseTrend", points);
        }
        Object bp = difyTrend.get("bpTrend");
        if (bp instanceof Map<?, ?> map && map.get("data_points") instanceof List<?> points && !points.isEmpty()) {
            target.put("bpTrend", points);
        }
        target.put("anomalies", difyTrend.get("anomalies"));
    }

    private Map<String, Object> buildLocalTrendLines(List<Map<String, Object>> history) {
        List<Map<String, Object>> bmiTrend = new ArrayList<>();
        List<Map<String, Object>> glucoseTrend = new ArrayList<>();
        List<Map<String, Object>> bpTrend = new ArrayList<>();
        List<Map<String, Object>> chronological = new ArrayList<>(history);
        java.util.Collections.reverse(chronological);
        for (Map<String, Object> row : chronological) {
            String date = formatDate(row);
            putPoint(bmiTrend, date, row.get("bmi"));
            putPoint(glucoseTrend, date, row.get("fastingGlucose"), row.get("fasting_glucose"));
            Map<String, Object> bp = new LinkedHashMap<>();
            bp.put("date", date);
            bp.put("systolic", numberValue(row.get("systolicBp"), row.get("systolic_bp")));
            bp.put("diastolic", numberValue(row.get("diastolicBp"), row.get("diastolic_bp")));
            bpTrend.add(bp);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bmiTrend", bmiTrend);
        result.put("glucoseTrend", glucoseTrend);
        result.put("bpTrend", bpTrend);
        result.put("anomalies", List.of());
        return result;
    }

    private String buildLocalSummary(List<Map<String, Object>> history) {
        double first = numberValue(history.get(history.size() - 1).get("fastingGlucose"),
                history.get(history.size() - 1).get("fasting_glucose"));
        double last = numberValue(history.get(0).get("fastingGlucose"), history.get(0).get("fasting_glucose"));
        return String.format("近 %d 次记录：空腹血糖从 %.1f 变化至 %.1f mmol/L。", history.size(), first, last);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeAnomalies(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", stringValue(map.get("type")));
            row.put("date", stringValue(map.get("date")));
            row.put("value", numberValue(map.get("value")));
            row.put("severity", stringValue(map.get("severity")));
            row.put("alert", stringValue(map.get("description"), map.get("alert")));
            row.put("description", stringValue(map.get("description"), map.get("alert")));
            row.put("suggestion", stringValue(map.get("suggestion")));
            result.add(row);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> trendObject(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bpTrendObject(Object primary, Object fallback) {
        return trendObject(primary, fallback);
    }

    private void putPoint(List<Map<String, Object>> list, String date, Object... values) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("date", date);
        point.put("value", numberValue(values));
        list.add(point);
    }

    private String formatDate(Map<String, Object> row) {
        Object recordedAt = row.get("recordedAt");
        if (recordedAt == null) {
            recordedAt = row.get("recorded_at");
        }
        if (recordedAt == null) {
            return "";
        }
        String text = String.valueOf(recordedAt);
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }

    private void assertWorkflowSucceeded(JsonNode response) {
        String status = response.path("data").path("status").asText(null);
        if (status == null || status.isBlank()) {
            status = response.path("status").asText(null);
        }
        if (status == null) {
            return;
        }
        if (status.isBlank()) {
            return;
        }
        if ("succeeded".equalsIgnoreCase(status)) {
            return;
        }
        throw new IllegalStateException("Dify 工作流状态=" + status);
    }

    private double numberValue(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return 0;
    }

    private String stringValue(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }
}
