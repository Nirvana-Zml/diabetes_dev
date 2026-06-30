package com.diabetes.plan.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class HealthPlanDietItemTest {

    @Test
    void gettersAndSetters() {
        HealthPlanDietItem item = new HealthPlanDietItem();

        item.setItemId("pdi_1");
        item.setPlanId("plan_1");
        item.setMealPeriod(1);
        item.setFoodName("鸡蛋");
        item.setPortion("1个");
        item.setCalories(70);
        item.setGiValue(new BigDecimal("55.5"));
        item.setNote("gi_level:low");
        item.setSortOrder(0);

        assertEquals("pdi_1", item.getItemId());
        assertEquals("plan_1", item.getPlanId());
        assertEquals(1, item.getMealPeriod());
        assertEquals("鸡蛋", item.getFoodName());
        assertEquals("1个", item.getPortion());
        assertEquals(70, item.getCalories());
        assertEquals(new BigDecimal("55.5"), item.getGiValue());
        assertEquals("gi_level:low", item.getNote());
        assertEquals(0, item.getSortOrder());
    }
}
