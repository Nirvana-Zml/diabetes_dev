package com.diabetes.health.controller;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.health.dto.RiskAssessRequest;
import com.diabetes.health.service.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentControllerTest {

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @InjectMocks
    private RiskAssessmentController controller;

    @Test
    void assess() {
        RiskAssessRequest request = new RiskAssessRequest();
        request.setHeight(170f);
        request.setWeight(65f);
        Map<String, Object> expected = Map.of("riskLevel", "low", "riskScore", 20);
        when(riskAssessmentService.assess("user1", request)).thenReturn(expected);

        var result = controller.assess("user1", request);

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
        verify(riskAssessmentService).assess("user1", request);
    }

    @Test
    void difyWorkflowSpec() {
        Map<String, Object> expected = Map.of("baseUrl", "http://localhost:5001");
        when(riskAssessmentService.getDifyWorkflowSpec()).thenReturn(expected);

        var result = controller.difyWorkflowSpec();

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
        verify(riskAssessmentService).getDifyWorkflowSpec();
    }

    @Test
    void history() {
        Map<String, Object> expected = Map.of("list", Map.of(), "total", 0);
        when(riskAssessmentService.getHistory("user1", 1, 10)).thenReturn(expected);

        var result = controller.history("user1", 1, 10);

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
        verify(riskAssessmentService).getHistory("user1", 1, 10);
    }

    @Test
    void detail() {
        Map<String, Object> expected = Map.of("assessmentId", "ra_001", "riskLevel", "medium");
        when(riskAssessmentService.getDetail("ra_001")).thenReturn(expected);

        var result = controller.detail("ra_001");

        assertEquals(200, result.code());
        assertEquals(expected, result.data());
        verify(riskAssessmentService).getDetail("ra_001");
    }

    @Test
    void detail_notFound() {
        when(riskAssessmentService.getDetail("ra_999")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.detail("ra_999"));

        assertEquals(404, exception.getCode());
        assertEquals("评估记录不存在", exception.getMessage());
        verify(riskAssessmentService).getDetail("ra_999");
    }
}