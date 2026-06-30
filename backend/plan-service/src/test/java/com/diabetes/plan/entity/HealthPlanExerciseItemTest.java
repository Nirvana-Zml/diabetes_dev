package com.diabetes.plan.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthPlanExerciseItemTest {

    @Test
    void gettersAndSetters() {
        HealthPlanExerciseItem item = new HealthPlanExerciseItem();

        item.setItemId("pei_1");
        item.setPlanId("plan_1");
        item.setExerciseType("快走");
        item.setDurationMinutes(30);
        item.setFrequency("每日");
        item.setIntensity(2);
        item.setCaloriesBurned(150);
        item.setCaution("餐后进行");
        item.setSortOrder(1);

        assertEquals("pei_1", item.getItemId());
        assertEquals("plan_1", item.getPlanId());
        assertEquals("快走", item.getExerciseType());
        assertEquals(30, item.getDurationMinutes());
        assertEquals("每日", item.getFrequency());
        assertEquals(2, item.getIntensity());
        assertEquals(150, item.getCaloriesBurned());
        assertEquals("餐后进行", item.getCaution());
        assertEquals(1, item.getSortOrder());
    }
}
