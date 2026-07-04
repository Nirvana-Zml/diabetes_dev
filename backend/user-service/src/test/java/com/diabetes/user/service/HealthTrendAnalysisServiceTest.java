package com.diabetes.user.service;

import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthTrendAnalysisServiceTest {

    private HealthServiceClient healthServiceClient;
    private DifyClient difyClient;
    private HealthTrendAnalysisService service;

    @BeforeEach
    void setUp() {
        healthServiceClient = mock(HealthServiceClient.class);
        difyClient = mock(DifyClient.class);
        service = new HealthTrendAnalysisService(
                healthServiceClient, difyClient, new ObjectMapper(), "dify-key", "blocking", "internal");
    }

    @Test
    void analyze_withoutForce_callsDifyWhenNoCache() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"首次AI趋势",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, false);

        assertEquals("dify", result.get("source"));
        assertEquals("首次AI趋势", result.get("summary"));
        assertEquals(false, result.get("cached"));
        verify(difyClient, times(1)).runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    void analyze_withoutForce_returnsCachedAiWhenFingerprintUnchanged() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"AI趋势总结",
                          "risk_level":"warning",
                          "anomalies":[]
                        }}}}
                        """));

        service.analyze("u1", 30, true);
        Map<String, Object> cached = service.analyze("u1", 30, false);

        assertEquals("AI趋势总结", cached.get("summary"));
        assertEquals(true, cached.get("cached"));
        verify(difyClient, times(1)).runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    void analyze_insufficientHistory() {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(List.of(Map.of("fastingGlucose", 6.0)));

        Map<String, Object> result = service.analyze("u1", 30, false);

        assertEquals("local", result.get("source"));
        assertTrue(String.valueOf(result.get("summary")).contains("数据不足"));
        verifyNoInteractions(difyClient);
    }

    @Test
    void analyze_nullHistory() {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(null);

        Map<String, Object> result = service.analyze("u1", 30, false);

        assertEquals("local", result.get("source"));
        assertTrue(((List<?>) result.get("anomalies")).isEmpty());
    }

    @Test
    void analyze_difyFailureWithoutCache() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenThrow(new RuntimeException("dify down"));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertFalse(String.valueOf(result.get("summary")).contains("AI 解读暂不可用"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void analyze_difyFailureUsesCachedSummary() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"缓存摘要",
                          "risk_level":"warning",
                          "anomalies":[]
                        }}}}
                        """))
                .thenThrow(new RuntimeException("dify down"));

        service.analyze("u1", 30, true);
        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("缓存摘要", result.get("summary"));
        assertEquals("warning", result.get("riskLevel"));
    }

    @Test
    void analyze_difySuccessWithDataPointsAndRootOutputs() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"根路径输出",
                          "risk_level":"warning",
                          "bmi_trend":{"data_points":[{"date":"2026-07-01","value":21.0}]},
                          "glucose_trend":{"data_points":[{"date":"2026-07-01","value":6.0}]},
                          "bp_trend":{"data_points":[{"date":"2026-07-01","systolic":120,"diastolic":80}]},
                          "anomalies":[{"type":"glucose","severity":"warning","description":"alert","value":"7.1","suggestion":"复查"}]
                        }}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("根路径输出", result.get("summary"));
        assertEquals("dify", result.get("source"));
        assertFalse(((List<?>) result.get("bmiTrend")).isEmpty());
        assertFalse(((List<?>) result.get("glucoseTrend")).isEmpty());
        assertFalse(((List<?>) result.get("bpTrend")).isEmpty());
        assertFalse(((List<?>) result.get("anomalies")).isEmpty());
    }

    @Test
    void analyze_difyWorkflowFailedStatus() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"failed","outputs":{}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertFalse(String.valueOf(result.get("summary")).contains("AI 解读暂不可用"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void analyze_withoutApiKeyUsesLocalSummary() {
        HealthTrendAnalysisService localOnly = new HealthTrendAnalysisService(
                healthServiceClient, difyClient, new ObjectMapper(), "", "blocking", "internal");
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());

        Map<String, Object> result = localOnly.analyze("u1", 30, false);

        assertEquals("local", result.get("source"));
        assertTrue(String.valueOf(result.get("summary")).contains("空腹血糖"));
        verifyNoInteractions(difyClient);
    }

    @Test
    void analyze_reusesCachedAiWhenDataUnchangedWithoutForce() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"首次AI",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        service.analyze("u1", 30, true);
        Map<String, Object> result = service.analyze("u1", 30, false);

        assertEquals("首次AI", result.get("summary"));
        assertEquals(true, result.get("cached"));
        verify(difyClient, times(1)).runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    void invalidateCache_removesUserEntries() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"缓存",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        service.analyze("u1", 30, true);
        service.invalidateCache("u1");
        service.invalidateCache(null);
        service.invalidateCache("  ");
        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("缓存", result.get("summary"));
        verify(difyClient, times(2)).runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    void callDifyTrendOnly_returnsEmptyWhenInsufficientHistory() {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(List.of(Map.of("fastingGlucose", 6.0)));

        assertTrue(service.callDifyTrendOnly("u1", 30).isEmpty());
    }

    @Test
    void callDifyTrendOnly_returnsLocalOnFailure() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenThrow(new RuntimeException("fail"));

        Map<String, Object> result = service.callDifyTrendOnly("u1", 30);

        assertFalse(result.isEmpty());
        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void callDifyTrendOnly_success() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"only",
                          "risk_level":"warning",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.callDifyTrendOnly("u1", 30);

        assertEquals("only", result.get("summary"));
    }

    @Test
    void fetchHistory_delegatesToClient() {
        List<Map<String, Object>> history = sampleHistory();
        when(healthServiceClient.getHealthHistory("u1", "internal", 30)).thenReturn(history);

        assertEquals(history, service.fetchHistory("u1", 30));
    }

    @Test
    void getWorkflowSpec() {
        Map<String, Object> spec = service.getWorkflowSpec("http://dify.local");
        assertNotNull(spec);
    }

    @Test
    void analyze_formatsSnakeCaseDatesAndShortRecordedAt() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("record_id", "hr_2");
        latest.put("recorded_at", "20260702");
        latest.put("fasting_glucose", 6.8);
        latest.put("bmi", 21.3);
        latest.put("systolic_bp", 128);
        latest.put("diastolic_bp", 82);
        Map<String, Object> older = new LinkedHashMap<>();
        older.put("record_id", "hr_1");
        older.put("recorded_at", "2026-07-01T10:00:00");
        older.put("fasting_glucose", 5.6);
        older.put("bmi", 21.5);
        older.put("systolic_bp", 120);
        older.put("diastolic_bp", 80);
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30)))
                .thenReturn(List.of(latest, older));

        Map<String, Object> result = service.analyze("u1", 30, false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> glucoseTrend = (List<Map<String, Object>>) result.get("glucoseTrend");
        assertEquals(2, glucoseTrend.size());
        assertEquals("2026-07-01", glucoseTrend.get(0).get("date"));
        assertEquals("20260702", glucoseTrend.get(1).get("date"));
    }

    @Test
    void constructor_defaultsNullResponseMode() {
        HealthTrendAnalysisService nullModeService = new HealthTrendAnalysisService(
                healthServiceClient, difyClient, new ObjectMapper(), "dify-key", null, "internal");
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        assertNotNull(nullModeService.analyze("u1", 30, false));
    }

    @Test
    void analyze_skipsDifyWhenApiKeyNull() throws Exception {
        HealthTrendAnalysisService nullKeyService = new HealthTrendAnalysisService(
                healthServiceClient, difyClient, new ObjectMapper(), null, "blocking", "internal");
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());

        Map<String, Object> result = nullKeyService.analyze("u1", 30, true);

        assertEquals("local", result.get("source"));
        verifyNoInteractions(difyClient);
    }

    @Test
    void callDifyTrendOnly_returnsEmptyWhenHistoryNull() {
        when(healthServiceClient.getHealthHistory("u1", "internal", 30)).thenReturn(null);
        assertTrue(service.callDifyTrendOnly("u1", 30).isEmpty());
    }

    @Test
    void analyze_difyUsesNestedOutputPath() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"nested",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("nested", result.get("summary"));
    }

    @Test
    void analyze_difyKeepsLocalTrendWhenDataPointsEmpty() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"empty points",
                          "risk_level":"normal",
                          "bmi_trend":{"data_points":[]},
                          "glucose_trend":{"data_points":[]},
                          "bp_trend":{"data_points":[]},
                          "anomalies":[{"type":"x","severity":"warning","description":"a","value":1}]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertFalse(((List<?>) result.get("glucoseTrend")).isEmpty());
    }

    @Test
    void analyze_difyAllowsBlankStatus() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"","outputs":{"trend_analysis":{
                          "summary":"blank status",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("blank status", result.get("summary"));
    }

    @Test
    void analyze_normalizesNonMapAnomalyItems() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"ok",
                          "risk_level":"normal",
                          "anomalies":["skip",{"type":"x","severity":"warning","description":"a","value":1}]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals(1, ((List<?>) result.get("anomalies")).size());
    }

    @Test
    void analyze_difyUsesRootOutputsWhenNestedMissing() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{}},"outputs":{"trend_analysis":{
                          "summary":"root only",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("root only", result.get("summary"));
    }

    @Test
    void analyze_difyNestedStatusSucceeded() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"nested status",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("nested status", result.get("summary"));
    }

    @Test
    void analyze_mergeDifyIgnoresNonMapTrendObjects() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"mixed",
                          "risk_level":"normal",
                          "bmi_trend":"bad",
                          "glucose_trend":123,
                          "bp_trend":[],
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("mixed", result.get("summary"));
        assertFalse(((List<?>) result.get("bmiTrend")).isEmpty());
    }

    @Test
    void analyze_mergeDifyHandlesInvalidDataPointsType() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"bad points",
                          "risk_level":"normal",
                          "bmi_trend":{"data_points":"bad"},
                          "glucose_trend":{"data_points":"bad"},
                          "bp_trend":{"data_points":"bad"},
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("bad points", result.get("summary"));
    }

    @Test
    void analyze_difyAllowsMissingStatusField() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"outputs":{"trend_analysis":{
                          "summary":"no status",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("no status", result.get("summary"));
    }

    @Test
    void analyze_fingerprintSkipsBlankRecordId() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("recordId", "   ");
        latest.put("record_id", "hr_2");
        latest.put("recordedAt", "2026-07-02T10:00:00");
        latest.put("fastingGlucose", 6.8);
        Map<String, Object> older = new LinkedHashMap<>(sampleHistory().get(1));
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30)))
                .thenReturn(List.of(latest, older));

        assertNotNull(service.analyze("u1", 30, false));
    }

    @Test
    void analyze_difyPlaceholderSummaryUsesLocalDefault() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"已记录7条健康数据，但AI分析暂时不可用，请稍后重试或查看原始数据。",
                          "risk_level":"warning",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertFalse(String.valueOf(result.get("summary")).contains("AI分析暂时不可用"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void analyze_difyTrendAnalysisNullNode() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":null}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertFalse(String.valueOf(result.get("summary")).contains("AI 解读暂不可用"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void analyze_difyRootTrendAnalysisNullAfterFallback() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{}},"outputs":{"trend_analysis":null}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertFalse(String.valueOf(result.get("summary")).contains("AI 解读暂不可用"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void analyze_difyAcceptsSucceededUpperCaseStatus() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"SUCCEEDED","outputs":{"trend_analysis":{
                          "summary":"upper status",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("upper status", result.get("summary"));
    }

    @Test
    void mergeDifyResult_replacesSingleSeriesIndependently() throws Exception {
        var method = HealthTrendAnalysisService.class.getDeclaredMethod(
                "mergeDifyResult", Map.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("bmiTrend", List.of(Map.of("date", "2026-07-01", "value", 21.0)));
        target.put("glucoseTrend", List.of(Map.of("date", "2026-07-01", "value", 6.0)));
        target.put("bpTrend", List.of(Map.of("date", "2026-07-01", "systolic", 120, "diastolic", 80)));

        method.invoke(service, target, Map.of(
                "summary", "bmi only",
                "riskLevel", "normal",
                "bmiTrend", Map.of("data_points", List.of(Map.of("date", "2026-07-02", "value", 20.0))),
                "anomalies", List.of()
        ));
        method.invoke(service, target, Map.of(
                "summary", "glucose only",
                "riskLevel", "normal",
                "glucoseTrend", Map.of("data_points", List.of(Map.of("date", "2026-07-03", "value", 5.5))),
                "anomalies", List.of()
        ));
        method.invoke(service, target, Map.of(
                "summary", "bp only",
                "riskLevel", "normal",
                "bpTrend", Map.of("data_points", List.of(Map.of("date", "2026-07-04", "systolic", 118, "diastolic", 78))),
                "anomalies", List.of()
        ));
    }

    @Test
    void assertWorkflowSucceeded_coversAllStatusBranches() throws Exception {
        var method = HealthTrendAnalysisService.class.getDeclaredMethod(
                "assertWorkflowSucceeded", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        ObjectMapper mapper = new ObjectMapper();

        method.invoke(service, mapper.readTree("{\"data\":{}}"));
        method.invoke(service, mapper.readTree("{\"data\":{\"status\":\"\"}}"));
        method.invoke(service, mapper.readTree("{\"data\":{\"status\":\"succeeded\"}}"));
        method.invoke(service, mapper.readTree("{\"data\":{\"status\":\"Succeeded\"}}"));
        method.invoke(service, mapper.readTree("{\"status\":\"succeeded\",\"data\":{}}"));
        method.invoke(service, mapper.readTree("{\"data\":{\"status\":\"\\t\"}}"));
        method.invoke(service, mapper.readTree("{\"status\":\"   \"}"));

        Exception failed = assertThrows(Exception.class, () ->
                method.invoke(service, mapper.readTree("{\"data\":{\"status\":\"failed\"}}")));
        assertTrue(failed.getCause() instanceof IllegalStateException);
    }

    @Test
    void analyze_difyUsesRootStatusWhenDataStatusBlank() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"status":"succeeded","data":{"status":"  ","outputs":{"trend_analysis":{
                          "summary":"root status fallback",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("root status fallback", result.get("summary"));
    }

    @Test
    void mergeDifyResult_coversEachTrendSeriesBranch() throws Exception {
        var method = HealthTrendAnalysisService.class.getDeclaredMethod(
                "mergeDifyResult", Map.class, Map.class);
        method.setAccessible(true);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("bmiTrend", List.of(Map.of("date", "2026-07-01", "value", 21.0)));
        target.put("glucoseTrend", List.of(Map.of("date", "2026-07-01", "value", 6.0)));
        target.put("bpTrend", List.of(Map.of("date", "2026-07-01", "systolic", 120, "diastolic", 80)));

        Map<String, Object> dify = Map.of(
                "summary", "series",
                "riskLevel", "normal",
                "bmiTrend", Map.of("data_points", List.of(Map.of("date", "2026-07-02", "value", 20.0))),
                "glucoseTrend", Map.of("data_points", List.of(Map.of("date", "2026-07-02", "value", 5.0))),
                "bpTrend", Map.of("data_points", List.of(Map.of("date", "2026-07-02", "systolic", 118, "diastolic", 78))),
                "anomalies", List.of()
        );

        method.invoke(service, target, dify);

        assertEquals(1, ((List<?>) target.get("bmiTrend")).size());
        assertEquals(1, ((List<?>) target.get("glucoseTrend")).size());
        assertEquals(1, ((List<?>) target.get("bpTrend")).size());
    }

    @Test
    void analyze_difySucceededStatusExplicit() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"succeeded",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("succeeded", result.get("summary"));
    }

    @Test
    void analyze_mergesAllDifyTrendSeries() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"series",
                          "risk_level":"normal",
                          "bmiTrend":{"data_points":[{"date":"2026-07-01","value":21.0}]},
                          "glucoseTrend":{"data_points":[{"date":"2026-07-01","value":6.0}]},
                          "bpTrend":{"data_points":[{"date":"2026-07-01","systolic":120,"diastolic":80}]},
                          "anomalies":[]
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals(1, ((List<?>) result.get("bmiTrend")).size());
        assertEquals(1, ((List<?>) result.get("glucoseTrend")).size());
        assertEquals(1, ((List<?>) result.get("bpTrend")).size());
    }

    @Test
    void analyze_reusesCachedAiWhenDataChangedButApiKeyBlank() throws Exception {
        HealthTrendAnalysisService keyedService = new HealthTrendAnalysisService(
                healthServiceClient, difyClient, new ObjectMapper(), "dify-key", "blocking", "internal");
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"缓存AI",
                          "risk_level":"warning",
                          "anomalies":[]
                        }}}}
                        """));
        keyedService.analyze("u1", 30, true);

        HealthTrendAnalysisService blankKeyService = new HealthTrendAnalysisService(
                healthServiceClient, difyClient, new ObjectMapper(), "", "blocking", "internal");
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> cache = (ConcurrentHashMap<String, ?>)
                ReflectionTestUtils.getField(keyedService, "trendCache");
        ReflectionTestUtils.setField(blankKeyService, "trendCache", cache);

        Map<String, Object> changedLatest = new LinkedHashMap<>(sampleHistory().get(0));
        changedLatest.put("recordId", "hr_3");
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30)))
                .thenReturn(List.of(changedLatest, sampleHistory().get(1)));

        Map<String, Object> result = blankKeyService.analyze("u1", 30, false);

        assertEquals("缓存AI", result.get("summary"));
        assertEquals("warning", result.get("riskLevel"));
        assertEquals(true, result.get("cached"));
        verify(difyClient, times(1)).runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString());
    }

    @Test
    void analyze_parsesTrendAnalysisFromTextOutput() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var data = root.putObject("data");
        data.put("status", "succeeded");
        data.putObject("outputs").put("text",
                "{\"trend_analysis\":{\"summary\":\"text输出\",\"risk_level\":\"normal\",\"anomalies\":[]}}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(root);

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("text输出", result.get("summary"));
        assertEquals("dify", result.get("source"));
    }

    @Test
    void analyze_parsesTrendAnalysisJsonString() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.createObjectNode();
        var data = root.putObject("data");
        data.put("status", "succeeded");
        data.putObject("outputs").put("trend_analysis",
                "{\"summary\":\"字符串输出\",\"risk_level\":\"attention\",\"anomalies\":[]}");
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(root);

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("字符串输出", result.get("summary"));
    }

    @Test
    void analyze_difyMissingTrendOutput() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(String.valueOf(result.get("summary")).contains("根据近"));
        assertFalse(String.valueOf(result.get("summary")).contains("AI 解读暂不可用"));
        assertEquals("local", result.get("source"));
    }

    @Test
    void analyze_normalizesNonListAnomalies() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"ok",
                          "risk_level":"normal",
                          "anomalies":"bad"
                        }}}}
                        """));

        Map<String, Object> result = service.analyze("u1", 30, true);

        assertTrue(((List<?>) result.get("anomalies")).isEmpty());
    }

    @Test
    void analyze_handlesMissingRecordedAtAndInvalidNumbers() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("recordId", "hr_2");
        latest.put("fasting_glucose", "bad");
        latest.put("bmi", "bad");
        Map<String, Object> older = new LinkedHashMap<>();
        older.put("recordId", "hr_1");
        older.put("recordedAt", "2026-07-01T10:00:00");
        older.put("fastingGlucose", 5.6);
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30)))
                .thenReturn(List.of(latest, older));

        Map<String, Object> result = service.analyze("u1", 30, false);

        assertNotNull(result.get("glucoseTrend"));
    }

    @Test
    void analyze_withForce_alwaysCallsDifyEvenWhenCached() throws Exception {
        when(healthServiceClient.getHealthHistory(eq("u1"), anyString(), eq(30))).thenReturn(sampleHistory());
        when(difyClient.runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"首次",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """))
                .thenReturn(new ObjectMapper().readTree("""
                        {"data":{"status":"succeeded","outputs":{"trend_analysis":{
                          "summary":"再次",
                          "risk_level":"normal",
                          "anomalies":[]
                        }}}}
                        """));

        service.analyze("u1", 30, true);
        Map<String, Object> result = service.analyze("u1", 30, true);

        assertEquals("再次", result.get("summary"));
        verify(difyClient, times(2)).runWorkflowBlocking(anyString(), anyString(), anyMap(), anyString());
    }

    private List<Map<String, Object>> sampleHistory() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("recordId", "hr_2");
        latest.put("recordedAt", "2026-07-02T10:00:00");
        latest.put("fastingGlucose", 6.8);
        latest.put("bmi", 21.3);
        latest.put("systolicBp", 128);
        latest.put("diastolicBp", 82);

        Map<String, Object> older = new LinkedHashMap<>();
        older.put("recordId", "hr_1");
        older.put("recordedAt", "2026-07-01T10:00:00");
        older.put("fastingGlucose", 5.6);
        older.put("bmi", 21.5);
        older.put("systolicBp", 120);
        older.put("diastolicBp", 80);
        return List.of(latest, older);
    }
}
