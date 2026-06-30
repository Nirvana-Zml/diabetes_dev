package com.diabetes.plan.service;

import com.diabetes.plan.entity.HealthPlan;
import com.diabetes.plan.mapper.HealthPlanMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanPersistenceServiceTest {

    @Mock
    private HealthPlanMapper healthPlanMapper;
    @Mock
    private PlanDetailPersistence planDetailPersistence;

    private PlanPersistenceService planPersistenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        planPersistenceService = new PlanPersistenceService(healthPlanMapper, planDetailPersistence, objectMapper);
    }

    @Test
    void savePlan() throws Exception {
        when(healthPlanMapper.countByUserId("u_1")).thenReturn(2);

        Map<String, Object> profile = Map.of(
                "health_record_id", "hr_1",
                "risk_assessment_id", "ra_1");
        Map<String, Object> content = Map.of(
                "summary", "方案摘要",
                "medicationNote", "按时用药",
                "dietPlan", Map.of(),
                "exercisePlan", Map.of(),
                "restPlan", Map.of());

        HealthPlan saved = planPersistenceService.savePlan("u_1", profile, 1800, content);

        verify(healthPlanMapper).deactivateActivePlans("u_1");
        ArgumentCaptor<HealthPlan> captor = ArgumentCaptor.forClass(HealthPlan.class);
        verify(healthPlanMapper).insert(captor.capture());
        HealthPlan plan = captor.getValue();
        assertEquals("u_1", plan.getUserId());
        assertEquals(3, plan.getVersion());
        assertEquals(1800, plan.getDailyCalories());
        assertEquals("方案摘要", plan.getSummary());
        assertTrue(plan.getPlanId().startsWith("plan_"));
        verify(planDetailPersistence).saveDetails(anyString(), eq(content));
        assertNotNull(saved);
    }

    @Test
    void savePlan_nullProfileIds() throws Exception {
        when(healthPlanMapper.countByUserId("u_1")).thenReturn(0);
        Map<String, Object> content = Map.of(
                "dietPlan", Map.of(), "exercisePlan", Map.of(), "restPlan", Map.of());

        planPersistenceService.savePlan("u_1", Map.of(), 1500, content);

        ArgumentCaptor<HealthPlan> captor = ArgumentCaptor.forClass(HealthPlan.class);
        verify(healthPlanMapper).insert(captor.capture());
        assertNull(captor.getValue().getHealthRecordId());
        assertEquals(1, captor.getValue().getVersion());
    }
}
