package com.diabetes.checkin.service;

import com.diabetes.checkin.dify.DifyCheckinAnalysisWorkflowContract;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.dify.DifyJsonSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckinMgmtService {

    private static final Logger log = LoggerFactory.getLogger(CheckinMgmtService.class);

    private final CheckinService checkinService;
    private final HealthServiceClient healthServiceClient;
    private final DifyClient difyClient;
    private final ObjectMapper objectMapper;
    private final String difyApiKey;
    private final String difyBaseUrl;
    private final String difyResponseMode;
    private final String difyInternalKey;

    public CheckinMgmtService(CheckinService checkinService,
                              HealthServiceClient healthServiceClient,
                              DifyClient difyClient,
                              ObjectMapper objectMapper,
                              @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
                              @Value("${dify.workflows.checkin-analysis.api-key:}") String difyApiKey,
                              @Value("${dify.workflows.checkin-analysis.response-mode:blocking}") String difyResponseMode,
                              @Value("${dify-internal.key:}") String difyInternalKey) {
        this.checkinService = checkinService;
        this.healthServiceClient = healthServiceClient;
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.difyBaseUrl = difyBaseUrl;
        this.difyApiKey = difyApiKey;
        this.difyResponseMode = difyResponseMode == null ? "blocking" : difyResponseMode.trim();
        this.difyInternalKey = difyInternalKey;
    }

    public Map<String, Object> getStats(String userId, LocalDate start, LocalDate end) {
        return checkinService.buildStats(userId, start, end);
    }

    public Map<String, Object> getTrends(String userId, LocalDate start, LocalDate end) {
        return checkinService.buildTrends(userId, start, end);
    }

    public Map<String, Object> getAiSummary(String userId, LocalDate start, LocalDate end) {
        Map<String, Object> stats = checkinService.buildStats(userId, start, end);
        Map<String, Object> trends = checkinService.buildTrends(userId, start, end);

        if (difyApiKey != null && !difyApiKey.isBlank()) {
            try {
                Map<String, Object> userProfile = healthServiceClient.getLatestHealthProfile(userId, difyInternalKey);
                Map<String, Object> payload = DifyCheckinAnalysisWorkflowContract.buildInputObject(
                        userId, stats, trends, userProfile, start.toString(), end.toString());
                JsonNode response = callDifyWorkflow(userId, payload);
                assertWorkflowSucceeded(response);
                Map<String, Object> parsed = parseBehaviorAnalysis(response);
                if (parsed != null && parsed.containsKey("summary")) {
                    parsed.put("source", "dify");
                    return parsed;
                }
                log.warn("Dify 行为分析响应缺少 summary，降级本地总结");
            } catch (Exception e) {
                log.warn("Dify 行为分析调用失败，降级本地总结: {}", e.getMessage());
            }
        } else {
            log.debug("未配置 DIFY_CHECKIN_API_KEY，使用本地行为分析总结");
        }
        return localSummary(stats);
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyCheckinAnalysisWorkflowContract.workflowSpec(
                difyBaseUrl, difyApiKey, difyResponseMode);
    }

    public Map<String, Object> exportReport(String userId, LocalDate start, LocalDate end, String format) {
        String taskId = "task_" + System.currentTimeMillis();
        return Map.of("taskId", taskId, "status", "processing", "format", format);
    }

    private JsonNode callDifyWorkflow(String userId, Map<String, Object> payload) {
        Map<String, Object> inputs = buildWorkflowInputs(payload);
        log.info("发起 Dify 打卡行为分析工作流 userId={} period={}~{}",
                userId, payload.get("start_date"), payload.get("end_date"));
        return difyClient.runWorkflowBlocking(difyApiKey, userId, inputs, difyResponseMode);
    }

    private Map<String, Object> buildWorkflowInputs(Map<String, Object> payload) {
        return DifyJsonSchema.flatWorkflowInputs(payload);
    }

    private void assertWorkflowSucceeded(JsonNode response) {
        String status = response.path("data").path("status").asText(null);
        if (status == null || status.isBlank()) {
            status = response.path("status").asText(null);
        }
        if (status != null && !status.isBlank() && !"succeeded".equalsIgnoreCase(status)) {
            String error = response.path("data").path("error").asText(
                    response.path("error").asText("工作流执行失败"));
            throw new IllegalStateException("Dify 工作流状态=" + status + ": " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBehaviorAnalysis(JsonNode response) {
        JsonNode analysisNode = extractBehaviorAnalysis(response);
        if (analysisNode.isMissingNode()) {
            return null;
        }

        Map<String, Object> raw = objectMapper.convertValue(analysisNode, Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", stringValue(raw.get("summary")));

        Object patterns = firstPresent(raw, "behavior_patterns", "behaviorPatterns");
        result.put("behaviorPatterns", normalizePatterns(patterns));

        Object anomalies = raw.get("anomalies");
        result.put("anomalies", normalizeAnomalies(anomalies));

        Object improvements = raw.get("improvements");
        result.put("improvements", normalizeStringList(improvements));

        return result;
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private JsonNode extractBehaviorAnalysis(JsonNode difyResponse) {
        for (JsonNode candidate : List.of(
                difyResponse.path(DifyCheckinAnalysisWorkflowContract.OUTPUT_KEY),
                difyResponse.path("data").path("outputs").path(DifyCheckinAnalysisWorkflowContract.OUTPUT_KEY),
                difyResponse.path("outputs").path(DifyCheckinAnalysisWorkflowContract.OUTPUT_KEY))) {
            JsonNode parsed = unwrapJsonNode(candidate);
            if (!parsed.isMissingNode() && parsed.isObject()) {
                return parsed;
            }
        }

        for (JsonNode outputs : List.of(
                difyResponse.path("data").path("outputs"),
                difyResponse.path("outputs"))) {
            if (!outputs.isMissingNode() && outputs.has("summary")) {
                return outputs;
            }
        }

        JsonNode text = difyResponse.path("data").path("outputs").path("text");
        if (text.isMissingNode()) text = difyResponse.path("outputs").path("text");
        if (text.isTextual()) {
            try {
                JsonNode parsed = objectMapper.readTree(text.asText());
                JsonNode fromText = parsed.path(DifyCheckinAnalysisWorkflowContract.OUTPUT_KEY);
                if (!fromText.isMissingNode()) {
                    return unwrapJsonNode(fromText);
                }
                if (parsed.has("summary")) return parsed;
            } catch (Exception e) {
                log.debug("无法从 Dify text 输出解析 behavior_analysis: {}", e.getMessage());
            }
        }
        return objectMapper.missingNode();
    }

    private JsonNode unwrapJsonNode(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return objectMapper.missingNode();
        }
        if (node.isTextual()) {
            try {
                return objectMapper.readTree(node.asText());
            } catch (Exception e) {
                log.debug("无法解析 behavior_analysis JSON 字符串: {}", e.getMessage());
                return objectMapper.missingNode();
            }
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizePatterns(Object patterns) {
        if (!(patterns instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("type", stringValue(map.get("type")));
            normalized.put("pattern", stringValue(map.get("pattern")));
            Object rate = firstPresentMap(map, "completion_rate", "completionRate");
            normalized.put("completionRate", rate);
            normalized.put("description", stringValue(map.get("description")));
            normalized.put("suggestion", stringValue(map.get("suggestion")));
            result.add(normalized);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeAnomalies(Object anomalies) {
        if (!(anomalies instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("date", stringValue(map.get("date")));
            normalized.put("type", stringValue(map.get("type")));
            normalized.put("value", map.get("value"));
            normalized.put("description", stringValue(map.get("description")));
            Object reason = firstPresentMap(map, "possible_reason", "possibleReason");
            normalized.put("possibleReason", stringValue(reason));
            result.add(normalized);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Object firstPresentMap(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> localSummary(Map<String, Object> stats) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", "本期共完成" + stats.get("totalCheckins") + "次打卡，完成率"
                + stats.get("completionRate") + "。建议保持规律打卡习惯。");
        result.put("behaviorPatterns", List.of());
        result.put("anomalies", List.of());
        result.put("improvements", List.of("建议固定每日打卡时间", "增加运动打卡频率"));
        result.put("source", "local");
        return result;
    }
}
