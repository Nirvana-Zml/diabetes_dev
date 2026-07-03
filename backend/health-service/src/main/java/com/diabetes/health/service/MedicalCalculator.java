package com.diabetes.health.service;

import com.diabetes.health.dto.RiskAssessRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MedicalCalculator {

    public record BmiResult(BigDecimal bmi, String bmiLevel) {}

    public record GlucoseResult(int glucoseLevel, String glucoseLevelName) {}

    public record BaseRiskResult(int baseRiskScore, List<Map<String, Object>> riskFactors) {}

    public BmiResult calculateBmi(float heightCm, float weightKg) {
        float heightM = heightCm / 100f;
        BigDecimal bmi = BigDecimal.valueOf(weightKg / (heightM * heightM))
                .setScale(1, RoundingMode.HALF_UP);
        String level;
        float bmiVal = bmi.floatValue();
        if (bmiVal < 18.5f) {
            level = "underweight";
        } else if (bmiVal < 24f) {
            level = "normal";
        } else if (bmiVal < 28f) {
            level = "overweight";
        } else {
            level = "obese";
        }
        return new BmiResult(bmi, level);
    }

    public GlucoseResult evaluateGlucose(float fastingGlucose) {
        if (fastingGlucose < 6.1f) {
            return new GlucoseResult(1, "normal");
        }
        if (fastingGlucose < 7.0f) {
            return new GlucoseResult(2, "prediabetes");
        }
        return new GlucoseResult(3, "diabetes");
    }

    public BaseRiskResult calculateBaseRisk(RiskAssessRequest request, BmiResult bmi, GlucoseResult glucose, int age) {
        int score = 0;
        List<Map<String, Object>> factors = new ArrayList<>();

        if (!"normal".equals(bmi.bmiLevel())) {
            score += 20;
            factors.add(factor("BMI超标", 20, "BMI=" + bmi.bmi() + "，属于" + bmi.bmiLevel()));
        }
        if (glucose.glucoseLevel() >= 2) {
            score += 25;
            factors.add(factor("血糖偏高", 25, "空腹血糖" + request.getFastingGlucose() + "mmol/L"));
        }
        if (Boolean.TRUE.equals(request.getFamilyHistory())) {
            score += 15;
            factors.add(factor("家族遗传史", 15, "有糖尿病家族史"));
        }
        if (request.getSmoking() != null && request.getSmoking() >= 1) {
            score += 10;
            factors.add(factor("吸烟", 10, "吸烟增加胰岛素抵抗风险"));
        }
        if (request.getExerciseFreq() == null || request.getExerciseFreq() <= 1) {
            score += 10;
            factors.add(factor("缺乏运动", 10, "运动频率不足"));
        }
        if (isUnhealthyDiet(request.getDietType())) {
            score += 10;
            factors.add(factor("不良饮食习惯", 10, "饮食模式不利于血糖控制"));
        }
        if (age >= 45) {
            score += 10;
            factors.add(factor("年龄因素", 10, age + "岁，糖尿病风险升高"));
        }
        if (Boolean.TRUE.equals(request.getIsPregnant())) {
            score += 15;
            factors.add(factor("妊娠期", 15, "妊娠期间需关注妊娠糖尿病风险"));
        }
        return new BaseRiskResult(Math.min(score, 100), factors);
    }

    public int mapRiskLevel(int score) {
        if (score < 30) return 1;
        if (score < 60) return 2;
        return 3;
    }

    public String riskLevelName(int level) {
        return switch (level) {
            case 1 -> "low";
            case 3 -> "high";
            default -> "medium";
        };
    }

    /** 兼容 high_sugar / high-sugar、high_fat / high-fat 等写法 */
    private boolean isUnhealthyDiet(String dietType) {
        if (dietType == null || dietType.isBlank()) {
            return false;
        }
        String normalized = dietType.trim().toLowerCase().replace('_', '-');
        return "high-sugar".equals(normalized) || "high-fat".equals(normalized);
    }

    private Map<String, Object> factor(String name, int weight, String description) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("weight", weight);
        map.put("description", description);
        return map;
    }
}
