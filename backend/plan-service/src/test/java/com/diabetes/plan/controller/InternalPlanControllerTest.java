package com.diabetes.plan.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.plan.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalPlanControllerTest {

    @Mock
    private PlanService planService;

    private InternalPlanController controllerWithKey;
    private InternalPlanController controllerNoKey;

    @BeforeEach
    void setUp() {
        controllerWithKey = new InternalPlanController(planService, "secret");
        controllerNoKey = new InternalPlanController(planService, "");
    }

    @Test
    void history_withValidKey() {
        when(planService.getHistory("u_1", 1, 50)).thenReturn(Map.of("total", 2));
        ApiResponse<Map<String, Object>> response = controllerWithKey.history("u_1", 1, 50, "secret");
        assertEquals(2, response.data().get("total"));
    }

    @Test
    void history_invalidKey() {
        assertThrows(BusinessException.class,
                () -> controllerWithKey.history("u_1", 1, 50, "wrong"));
    }

    @Test
    void history_noKeyConfigured() {
        when(planService.getHistory("u_1", 1, 50)).thenReturn(Map.of("total", 0));
        assertNotNull(controllerNoKey.history("u_1", 1, 50, null));
    }

    @Test
    void latest_withPlan() {
        when(planService.getLatest("u_1")).thenReturn(Map.of("planId", "p1"));
        ApiResponse<Map<String, Object>> response = controllerWithKey.latest("u_1", "secret");
        assertEquals("p1", response.data().get("planId"));
    }

    @Test
    void latest_nullPlan() {
        when(planService.getLatest("u_1")).thenReturn(null);
        ApiResponse<Map<String, Object>> response = controllerWithKey.latest("u_1", "secret");
        assertTrue(response.data().isEmpty());
    }
}
