package com.diabetes.plan.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthPlanRestItemTest {

    @Test
    void gettersAndSetters() {
        HealthPlanRestItem item = new HealthPlanRestItem();

        item.setItemId("pri_1");
        item.setPlanId("plan_1");
        item.setScheduleType(1);
        item.setTimePoint("06:30");
        item.setSuggestion("建议起床时间");
        item.setSortOrder(0);

        assertEquals("pri_1", item.getItemId());
        assertEquals("plan_1", item.getPlanId());
        assertEquals(1, item.getScheduleType());
        assertEquals("06:30", item.getTimePoint());
        assertEquals("建议起床时间", item.getSuggestion());
        assertEquals(0, item.getSortOrder());
    }
}
