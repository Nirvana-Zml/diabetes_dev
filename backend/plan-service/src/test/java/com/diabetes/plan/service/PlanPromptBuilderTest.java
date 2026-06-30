package com.diabetes.plan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanPromptBuilderTest {

    private PlanPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PlanPromptBuilder();
    }

    @Test
    void buildDifyPayload() {
        Map<String, Object> profile = Map.of(
                "user_profile", Map.of("age", 30),
                "health_profile", Map.of("height", 170),
                "checkin_data", Map.of("recent_days", 14));
        Map<String, Object> payload = builder.buildDifyPayload("u_1", profile, 1800);

        assertEquals("u_1", payload.get("user_id"));
        assertEquals(1800, payload.get("daily_calories"));
        assertNotNull(payload.get("query"));
    }
}
