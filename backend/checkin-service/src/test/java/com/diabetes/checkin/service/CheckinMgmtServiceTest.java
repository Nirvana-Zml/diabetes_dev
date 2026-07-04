package com.diabetes.checkin.service;

import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckinMgmtServiceTest {

    private final CheckinService checkinService = mock(CheckinService.class);
    private final HealthServiceClient healthClient = mock(HealthServiceClient.class);
    private final UserServiceClient userServiceClient = mock(UserServiceClient.class);
    private final DifyClient difyClient = mock(DifyClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void delegatesStatsTrendsAndBuildsSpecAndExportTask() {
        CheckinMgmtService service = service("");
        CheckinMgmtService nullModeService = new CheckinMgmtService(checkinService, healthClient, userServiceClient,
                difyClient, objectMapper, null, "", null, "");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        when(checkinService.buildStats("u1", start, end)).thenReturn(Map.of("totalCheckins", 1));
        when(checkinService.buildTrends("u1", start, end)).thenReturn(Map.of("dietTrend", List.of()));

        assertEquals(1, service.getStats("u1", start, end).get("totalCheckins"));
        assertEquals(List.of(), service.getTrends("u1", start, end).get("dietTrend"));
        assertTrue(service.getDifyWorkflowSpec().get("workflowUrl").toString().endsWith("/v1/workflows/run"));
        assertEquals("blocking", nullModeService.getDifyWorkflowSpec().get("responseMode"));
        Map<String, Object> report = service.exportReport("u1", start, end, "xlsx");
        assertEquals("processing", report.get("status"));
        assertEquals("xlsx", report.get("format"));
    }

    @Test
    void aiSummaryUsesLocalWhenApiKeyMissing() {
        CheckinMgmtService service = service("");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        when(checkinService.buildStats("u1", start, end)).thenReturn(stats());
        when(checkinService.buildTrends("u1", start, end)).thenReturn(trends());

        Map<String, Object> result = service.getAiSummary("u1", start, end);

        assertEquals("local", result.get("source"));
        assertTrue(result.get("summary").toString().contains("2次"));
        verifyNoInteractions(difyClient);
    }

    @Test
    void aiSummaryParsesDifyBehaviorAnalysisObject() throws Exception {
        CheckinMgmtService service = service("key");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        stubStats(start, end);
        when(healthClient.getLatestHealthProfile("u1", "internal")).thenReturn(Map.of("height", 170));
        when(difyClient.runWorkflowBlocking(eq("key"), eq("u1"), anyMap(), eq("streaming")))
                .thenReturn(objectMapper.readTree("""
                        {"data":{"status":"succeeded","outputs":{"behavior_analysis":{
                          "summary":"AI总结",
                          "behavior_patterns":[{"type":"diet","pattern":"regular","completion_rate":0.8,"description":"ok","suggestion":"keep"},{"type":"exercise"},"bad"],
                          "anomalies":[{"date":"2024-01-01","type":"glucose","value":8.1,"description":"high","possible_reason":"meal"},{"date":"2024-01-02"},"bad"],
                          "improvements":["固定时间"]
                        }}}}
                        """));

        Map<String, Object> result = service.getAiSummary("u1", start, end);

        assertEquals("dify", result.get("source"));
        assertEquals("AI总结", result.get("summary"));
        assertTrue(result.get("behaviorPatterns").toString().contains("completionRate"));
        assertTrue(result.get("anomalies").toString().contains("possibleReason"));
    }

    @Test
    void aiSummaryParsesAlternativeDifyShapesAndFallsBackOnBadResponses() throws Exception {
        CheckinMgmtService service = service("key");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        stubStats(start, end);
        when(healthClient.getLatestHealthProfile(eq("u1"), anyString())).thenReturn(Map.of());
        when(difyClient.runWorkflowBlocking(eq("key"), eq("u1"), anyMap(), anyString()))
                .thenReturn(objectMapper.readTree("""
                        {}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"behavior_analysis":"{\\"summary\\":\\"root\\",\\"behaviorPatterns\\":[{\\"type\\":\\"x\\",\\"completionRate\\":1}],\\"anomalies\\":[],\\"improvements\\":[1,null,\\"a\\"]}"}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"outputs":{"summary":"outputs","behavior_patterns":"bad","anomalies":"bad","improvements":"bad"}}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"data":{"outputs":{"text":"{\\"behavior_analysis\\":{\\"summary\\":\\"text\\"}}"}}, "status":"succeeded"}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"data":{"outputs":{"text":"not-json"}}}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"behavior_analysis":"not-json"}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"outputs":{"text":"{\\"summary\\":\\"plainText\\"}"}}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"outputs":{"text":"{\\"x\\":1}"}}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"behavior_analysis":"123"}
                        """))
                .thenReturn(objectMapper.readTree("""
                        {"status":"failed","error":"bad"}
                        """));

        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
        assertEquals("root", service.getAiSummary("u1", start, end).get("summary"));
        assertEquals("outputs", service.getAiSummary("u1", start, end).get("summary"));
        assertEquals("text", service.getAiSummary("u1", start, end).get("summary"));
        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
        assertEquals("plainText", service.getAiSummary("u1", start, end).get("summary"));
        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
    }

    @Test
    void aiSummaryFallsBackWhenDifyThrowsOrLacksSummary() throws Exception {
        CheckinMgmtService service = service("key");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        stubStats(start, end);
        when(healthClient.getLatestHealthProfile(eq("u1"), anyString())).thenReturn(Map.of());
        when(difyClient.runWorkflowBlocking(eq("key"), eq("u1"), anyMap(), anyString()))
                .thenReturn(objectMapper.readTree("""
                        {"data":{"outputs":{"behavior_analysis":{"behavior_patterns":[]}}}}
                        """))
                .thenThrow(new RuntimeException("down"));

        Map<String, Object> normalizedEmptySummary = service.getAiSummary("u1", start, end);
        assertEquals("dify", normalizedEmptySummary.get("source"));
        assertEquals("", normalizedEmptySummary.get("summary"));
        assertEquals("local", service.getAiSummary("u1", start, end).get("source"));
    }

    @Test
    void privateDifyParsingHelpers_coverRemainingBranches() throws Exception {
        CheckinMgmtService service = service("key");
        CheckinMgmtService nullKeyService = new CheckinMgmtService(checkinService, healthClient, userServiceClient,
                difyClient, objectMapper, "http://dify/", null, "blocking", "internal");
        Method assertWorkflowSucceeded = CheckinMgmtService.class.getDeclaredMethod("assertWorkflowSucceeded", com.fasterxml.jackson.databind.JsonNode.class);
        Method unwrapJsonNode = CheckinMgmtService.class.getDeclaredMethod("unwrapJsonNode", com.fasterxml.jackson.databind.JsonNode.class);
        Method firstPresent = CheckinMgmtService.class.getDeclaredMethod("firstPresent", Map.class, String[].class);
        Method firstPresentMap = CheckinMgmtService.class.getDeclaredMethod("firstPresentMap", Map.class, String[].class);
        assertWorkflowSucceeded.setAccessible(true);
        unwrapJsonNode.setAccessible(true);
        firstPresent.setAccessible(true);
        firstPresentMap.setAccessible(true);

        when(checkinService.buildStats("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2))).thenReturn(stats());
        when(checkinService.buildTrends("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2))).thenReturn(trends());
        assertEquals("local", nullKeyService.getAiSummary("u1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)).get("source"));
        assertDoesNotThrow(() -> assertWorkflowSucceeded.invoke(service, objectMapper.readTree("{}")));
        assertDoesNotThrow(() -> assertWorkflowSucceeded.invoke(service, objectMapper.readTree("{\"status\":\"succeeded\"}")));
        assertDoesNotThrow(() -> assertWorkflowSucceeded.invoke(service, objectMapper.readTree("{\"data\":{\"status\":\" \"}}")));
        assertDoesNotThrow(() -> assertWorkflowSucceeded.invoke(service, objectMapper.readTree("{\"data\":{\"status\":\" \"},\"status\":\" \"}")));
        assertTrue(((com.fasterxml.jackson.databind.JsonNode) unwrapJsonNode.invoke(service, objectMapper.nullNode())).isMissingNode());
        assertTrue(((com.fasterxml.jackson.databind.JsonNode) unwrapJsonNode.invoke(service, objectMapper.missingNode())).isMissingNode());
        assertEquals("x", firstPresent.invoke(service, Map.of("a", "x"), new String[]{"a", "b"}));
        assertNull(firstPresent.invoke(service, Map.of("a", "x"), new String[]{"b"}));
        assertEquals("x", firstPresentMap.invoke(service, Map.of("a", "x"), new String[]{"a", "b"}));
        assertNull(firstPresentMap.invoke(service, Map.of("a", "x"), new String[]{"b"}));
        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("a", null);
        assertNull(firstPresent.invoke(service, nullValue, new String[]{"a"}));
        assertNull(firstPresentMap.invoke(service, nullValue, new String[]{"a"}));

        Method triggerAnalysisIntervention = CheckinMgmtService.class.getDeclaredMethod(
                "triggerAnalysisIntervention", String.class, LocalDate.class, LocalDate.class, Map.class);
        triggerAnalysisIntervention.setAccessible(true);
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 2);
        triggerAnalysisIntervention.invoke(service, "u1", start, end, Map.of("anomalies", "bad"));
        triggerAnalysisIntervention.invoke(service, "u1", start, end, Map.of("anomalies", List.of()));
        triggerAnalysisIntervention.invoke(service, "u1", start, end,
                Map.of("anomalies", List.of(Map.of("date", "2024-01-01")), "improvements", List.of("x")));
        verify(userServiceClient).evaluateIntervention(eq("internal"), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    private CheckinMgmtService service(String apiKey) {
        return new CheckinMgmtService(checkinService, healthClient, userServiceClient, difyClient, objectMapper,
                "http://dify/", apiKey, " streaming ", "internal");
    }

    private void stubStats(LocalDate start, LocalDate end) {
        when(checkinService.buildStats("u1", start, end)).thenReturn(stats());
        when(checkinService.buildTrends("u1", start, end)).thenReturn(trends());
    }

    private static Map<String, Object> stats() {
        return Map.of("totalCheckins", 2, "completionRate", 0.25, "totalPoints", 10,
                "streakDays", 1, "calendarData", Map.of());
    }

    private static Map<String, Object> trends() {
        return Map.of("dietTrend", List.of(), "exerciseTrend", List.of(),
                "medicationTrend", List.of(), "glucoseTrend", List.of());
    }
}
