package com.diabetes.plan.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CalorieCalculator {

    public int calculateDailyCalories(Map<String, Object> profile) {
        int age = parseInt(profile.get("age"), 35);
        String gender = String.valueOf(profile.getOrDefault("gender", "male"));
        double weight = parseDouble(profile.get("weight"), 70);
        double height = parseDouble(profile.get("height"), 170);
        String exercise = String.valueOf(profile.getOrDefault("exerciseFrequency", "occasional"));

        double bmr;
        if ("female".equalsIgnoreCase(gender)) {
            bmr = 10 * weight + 6.25 * height - 5 * age - 161;
        } else {
            bmr = 10 * weight + 6.25 * height - 5 * age + 5;
        }

        double factor = switch (exercise) {
            case "daily", "regular" -> 1.55;
            case "occasional" -> 1.375;
            default -> 1.2;
        };
        return (int) Math.round(bmr * factor);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
