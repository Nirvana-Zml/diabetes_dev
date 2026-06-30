package com.diabetes.plan.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HealthPlanTest {

    @Test
    void gettersAndSetters() {
        HealthPlan plan = new HealthPlan();
        LocalDateTime generatedAt = LocalDateTime.of(2024, 6, 1, 10, 0);

        plan.setPlanId("plan_1");
        plan.setUserId("u_1");
        plan.setHealthRecordId("hr_1");
        plan.setRiskAssessmentId("ra_1");
        plan.setTitle("方案标题");
        plan.setSummary("摘要");
        plan.setDailyCalories(1800);
        plan.setMedicationNote("用药说明");
        plan.setSource(1);
        plan.setVersion(2);
        plan.setIsActive(1);
        plan.setIsFavorite(0);
        plan.setAiRawResponse("{\"dietPlan\":{}}");
        plan.setDelFlag(0);
        plan.setGeneratedAt(generatedAt);

        assertEquals("plan_1", plan.getPlanId());
        assertEquals("u_1", plan.getUserId());
        assertEquals("hr_1", plan.getHealthRecordId());
        assertEquals("ra_1", plan.getRiskAssessmentId());
        assertEquals("方案标题", plan.getTitle());
        assertEquals("摘要", plan.getSummary());
        assertEquals(1800, plan.getDailyCalories());
        assertEquals("用药说明", plan.getMedicationNote());
        assertEquals(1, plan.getSource());
        assertEquals(2, plan.getVersion());
        assertEquals(1, plan.getIsActive());
        assertEquals(0, plan.getIsFavorite());
        assertEquals("{\"dietPlan\":{}}", plan.getAiRawResponse());
        assertEquals(0, plan.getDelFlag());
        assertEquals(generatedAt, plan.getGeneratedAt());
    }
}
