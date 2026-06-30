package com.diabetes.plan.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanConstantsTest {

    @Test
    void parseIntensity_variants() {
        assertEquals(PlanConstants.INTENSITY_LOW, PlanConstants.parseIntensity("低强度"));
        assertEquals(PlanConstants.INTENSITY_LOW, PlanConstants.parseIntensity("轻度"));
        assertEquals(PlanConstants.INTENSITY_LOW, PlanConstants.parseIntensity("low"));
        assertEquals(PlanConstants.INTENSITY_HIGH, PlanConstants.parseIntensity("高强度"));
        assertEquals(PlanConstants.INTENSITY_HIGH, PlanConstants.parseIntensity("high"));
        assertEquals(PlanConstants.INTENSITY_MEDIUM, PlanConstants.parseIntensity("中等"));
        assertEquals(PlanConstants.INTENSITY_MEDIUM, PlanConstants.parseIntensity("medium"));
        assertEquals(PlanConstants.INTENSITY_MEDIUM, PlanConstants.parseIntensity(null));
        assertEquals(PlanConstants.INTENSITY_MEDIUM, PlanConstants.parseIntensity("unknown"));
    }

    @Test
    void parseDurationMinutes_variants() {
        assertEquals(30, PlanConstants.parseDurationMinutes("30分钟"));
        assertNull(PlanConstants.parseDurationMinutes(null));
        assertNull(PlanConstants.parseDurationMinutes("无数字"));
        assertEquals(45, PlanConstants.parseDurationMinutes("约45min"));
    }
}
