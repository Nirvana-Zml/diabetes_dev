package com.diabetes.checkin.service;

import java.util.Map;

final class CheckinConstants {

    static final int SOURCE_PRESET = 1;
    static final int SOURCE_CUSTOM = 2;
    static final int INPUT_UNIT_G = 1;
    static final int INPUT_UNIT_ML = 2;
    static final int TYPE_DIET = 1;
    static final int TYPE_EXERCISE = 2;
    static final int TYPE_MEDICATION = 3;
    static final int TYPE_GLUCOSE = 4;

    private static final Map<Integer, String> MEAL_PERIOD_LABELS = Map.of(
            1, "早餐",
            2, "午餐",
            3, "晚餐",
            4, "上午加餐",
            5, "下午加餐",
            6, "晚上加餐"
    );

    private CheckinConstants() {
    }

    static String mealPeriodLabel(Integer mealPeriod) {
        return MEAL_PERIOD_LABELS.getOrDefault(mealPeriod, "未知");
    }
}
