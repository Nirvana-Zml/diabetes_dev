package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.service.HealthInterventionOrchestrator;
import com.diabetes.user.service.HealthTrendAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthTrendControllerTest {

    private HealthTrendAnalysisService trendAnalysisService;
    private HealthInterventionOrchestrator interventionOrchestrator;
    private HealthTrendController controller;

    @BeforeEach
    void setUp() {
        trendAnalysisService = mock(HealthTrendAnalysisService.class);
        interventionOrchestrator = mock(HealthInterventionOrchestrator.class);
        controller = new HealthTrendController(trendAnalysisService, interventionOrchestrator, "http://dify.local");
    }

    @Test
    void healthTrend_withoutForce() {
        Map<String, Object> data = Map.of("summary", "local", "source", "local");
        when(trendAnalysisService.analyze("u1", 30, false)).thenReturn(data);

        ApiResponse<Map<String, Object>> response = controller.healthTrend("u1", 30, false);

        assertEquals(data, response.data());
        verify(interventionOrchestrator, never()).evaluateAsync(anyString(), anyString(), any());
    }

    @Test
    void healthTrend_withForce_triggersIntervention() {
        Map<String, Object> data = Map.of("summary", "AI", "source", "dify");
        when(trendAnalysisService.analyze("u1", 15, true)).thenReturn(data);

        ApiResponse<Map<String, Object>> response = controller.healthTrend("u1", 15, true);

        assertEquals(data, response.data());
        verify(interventionOrchestrator).evaluateAsync("u1", "manual_refresh", Map.of());
    }

    @Test
    void healthAlert() {
        Map<String, Object> alert = Map.of("active", true, "has_alert", true);
        when(interventionOrchestrator.getActiveAlert("u1")).thenReturn(alert);

        assertEquals(alert, controller.healthAlert("u1").data());
    }

    @Test
    void acknowledge() {
        ApiResponse<Void> response = controller.acknowledge("u1", "ivp_1");

        assertNull(response.data());
        verify(interventionOrchestrator).acknowledge("u1", "ivp_1");
    }

    @Test
    void difyWorkflowSpec() {
        Map<String, Object> spec = Map.of("workflow", "health-trend");
        when(trendAnalysisService.getWorkflowSpec("http://dify.local")).thenReturn(spec);

        assertEquals(spec, controller.difyWorkflowSpec().data());
    }
}
