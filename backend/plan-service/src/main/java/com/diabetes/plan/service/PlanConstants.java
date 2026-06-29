package com.diabetes.plan.service;

final class PlanConstants {

    static final int MEAL_BREAKFAST = 1;
    static final int MEAL_LUNCH = 2;
    static final int MEAL_DINNER = 3;
    static final int MEAL_SNACK = 5;

    static final int SCHEDULE_WAKE_UP = 1;
    static final int SCHEDULE_SLEEP = 2;
    static final int SCHEDULE_NAP = 3;
    static final int SCHEDULE_GLUCOSE_MONITOR = 4;
    static final int SCHEDULE_ROUTINE_TIP = 5;

    static final int INTENSITY_LOW = 1;
    static final int INTENSITY_MEDIUM = 2;
    static final int INTENSITY_HIGH = 3;

    private PlanConstants() {
    }

    static int parseIntensity(String raw) {
        if (raw == null || raw.isBlank()) {
            return INTENSITY_MEDIUM;
        }
        String value = raw.trim().toLowerCase();
        if (value.contains("低") || value.contains("轻") || "low".equals(value)) {
            return INTENSITY_LOW;
        }
        if (value.contains("高") || "high".equals(value)) {
            return INTENSITY_HIGH;
        }
        if (value.contains("中") || "medium".equals(value)) {
            return INTENSITY_MEDIUM;
        }
        return INTENSITY_MEDIUM;
    }

    static Integer parseDurationMinutes(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits.append(ch);
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        return Integer.parseInt(digits.toString());
    }
}
