package com.diabetes.plan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalorieCalculatorTest {

    private CalorieCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CalorieCalculator();
    }

    @Test
    void calculateDailyCalories_maleDaily() {
        Map<String, Object> profile = Map.of(
                "age", 30, "gender", "male", "weight", 70, "height", 175,
                "exerciseFrequency", "daily");
        int calories = calculator.calculateDailyCalories(profile);
        assertTrue(calories > 2000);
    }

    @Test
    void calculateDailyCalories_femaleOccasional() {
        Map<String, Object> profile = Map.of(
                "age", 40, "gender", "female", "weight", 60, "height", 165,
                "exerciseFrequency", "occasional");
        int calories = calculator.calculateDailyCalories(profile);
        assertTrue(calories > 1500);
    }

    @Test
    void calculateDailyCalories_regularExercise() {
        Map<String, Object> profile = Map.of(
                "age", 35, "gender", "male", "weight", 70, "height", 170,
                "exerciseFrequency", "regular");
        assertTrue(calculator.calculateDailyCalories(profile) > 0);
    }

    @Test
    void calculateDailyCalories_defaultsAndInvalidNumbers() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("age", "bad");
        profile.put("weight", null);
        profile.put("height", "invalid");
        profile.put("exerciseFrequency", "unknown");

        assertTrue(calculator.calculateDailyCalories(profile) > 0);
    }

    @Test
    void calculateDailyCalories_nullAge() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("age", null);
        profile.put("gender", "male");
        profile.put("weight", 70);
        profile.put("height", 170);
        assertTrue(calculator.calculateDailyCalories(profile) > 0);
    }
}
