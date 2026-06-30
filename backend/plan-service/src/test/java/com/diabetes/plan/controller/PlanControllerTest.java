package com.diabetes.plan.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.plan.service.PlanService;
import com.diabetes.plan.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock
    private PlanService planService;
    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private PlanController planController;

    @Test
    void generate_successReturnsSse() {
        Map<String, Object> profile = Map.of("height", 170, "weight", 65);
        when(userProfileService.buildUserProfile("u_1")).thenReturn(profile);
        doNothing().when(userProfileService).validateProfileForPlan(profile);
        SseEmitter emitter = new SseEmitter();
        when(planService.generatePlanStream("u_1", profile)).thenReturn(emitter);

        Object result = planController.generate("u_1");
        assertSame(emitter, result);
    }

    @Test
    void generate_validationFailure() {
        Map<String, Object> profile = Map.of("health_profile", Map.of());
        when(userProfileService.buildUserProfile("u_1")).thenReturn(profile);
        doThrow(new BusinessException(400, "缺少身高体重")).when(userProfileService).validateProfileForPlan(profile);

        Object result = planController.generate("u_1");
        assertInstanceOf(ResponseEntity.class, result);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void generate_validationNotFound() {
        Map<String, Object> profile = Map.of();
        when(userProfileService.buildUserProfile("u_1")).thenReturn(profile);
        doThrow(new BusinessException(404, "未找到")).when(userProfileService).validateProfileForPlan(profile);

        ResponseEntity<?> response = (ResponseEntity<?>) planController.generate("u_1");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void difyWorkflowSpec() {
        when(planService.getDifyWorkflowSpec()).thenReturn(Map.of("apiKey", "k"));
        ApiResponse<Map<String, Object>> response = planController.difyWorkflowSpec();
        assertEquals("k", response.data().get("apiKey"));
    }

    @Test
    void latest_found() {
        when(planService.getLatest("u_1")).thenReturn(Map.of("planId", "p1"));
        assertEquals("p1", planController.latest("u_1").data().get("planId"));
    }

    @Test
    void latest_notFound() {
        when(planService.getLatest("u_1")).thenReturn(null);
        assertThrows(BusinessException.class, () -> planController.latest("u_1"));
    }

    @Test
    void history() {
        when(planService.getHistory("u_1", 1, 10)).thenReturn(Map.of("total", 0));
        assertEquals(0, planController.history("u_1", 1, 10).data().get("total"));
    }

    @Test
    void favorite() {
        when(planService.toggleFavorite("u_1", "p1")).thenReturn(Map.of("favorited", true));
        assertEquals(true, planController.favorite("u_1", "p1").data().get("favorited"));
    }

    @Test
    void detail_found() {
        when(planService.getDetail("p1")).thenReturn(Map.of("planId", "p1"));
        assertEquals("p1", planController.detail("p1").data().get("planId"));
    }

    @Test
    void detail_notFound() {
        when(planService.getDetail("p1")).thenReturn(null);
        assertThrows(BusinessException.class, () -> planController.detail("p1"));
    }
}
