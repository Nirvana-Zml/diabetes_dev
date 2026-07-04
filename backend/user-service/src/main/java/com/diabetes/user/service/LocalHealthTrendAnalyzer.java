package com.diabetes.user.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 不可用时的本地健康趋势分析：基于历史记录生成默认解读，供趋势卡片与预警条共用。
 */
final class LocalHealthTrendAnalyzer {

    private static final double GLUCOSE_NORMAL_MAX = 6.1;
    private static final double GLUCOSE_WARNING = 7.0;
    private static final double GLUCOSE_CRITICAL = 11.1;
    private static final double BMI_OVERWEIGHT = 24.0;
    private static final double BP_SYSTOLIC_HIGH = 140.0;
    private static final double BP_DIASTOLIC_HIGH = 90.0;

    private LocalHealthTrendAnalyzer() {
    }

    record LocalTrendResult(String summary, String riskLevel, String suggestion,
                            List<Map<String, Object>> anomalies) {
    }

    static Map<String, Object> toTrendMap(LocalTrendResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("summary", result.summary());
        map.put("riskLevel", result.riskLevel());
        map.put("suggestion", result.suggestion());
        map.put("anomalies", result.anomalies());
        map.put("source", "local");
        return map;
    }

    static LocalTrendResult analyze(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return new LocalTrendResult(
                    "暂无健康数据，请先记录身高、体重、血糖等指标。",
                    "normal",
                    "建议定期记录健康指标，便于跟踪变化趋势。",
                    List.of());
        }

        Map<String, Object> latest = history.get(0);
        int count = history.size();

        double latestGlucose = glucoseOf(latest);
        double latestBmi = numberValue(latest.get("bmi"));
        double latestSystolic = numberValue(latest.get("systolicBp"), latest.get("systolic_bp"));
        double latestDiastolic = numberValue(latest.get("diastolicBp"), latest.get("diastolic_bp"));

        List<Map<String, Object>> anomalies = new ArrayList<>();
        String riskLevel = "normal";

        if (latestGlucose >= GLUCOSE_CRITICAL) {
            riskLevel = maxRisk(riskLevel, "critical");
            addAnomaly(anomalies, "glucose", "critical", latestGlucose,
                    "空腹血糖 " + format1(latestGlucose) + " mmol/L 明显偏高",
                    "建议尽快就医复查");
        } else if (latestGlucose >= GLUCOSE_WARNING) {
            riskLevel = maxRisk(riskLevel, "warning");
            addAnomaly(anomalies, "glucose", "warning", latestGlucose,
                    "空腹血糖 " + format1(latestGlucose) + " mmol/L 超过正常上限",
                    "建议加强血糖监测并咨询医生");
        } else if (latestGlucose >= GLUCOSE_NORMAL_MAX) {
            riskLevel = maxRisk(riskLevel, "attention");
            addAnomaly(anomalies, "glucose", "warning", latestGlucose,
                    "空腹血糖 " + format1(latestGlucose) + " mmol/L 处于偏高区间",
                    "建议关注饮食并定期复查");
        }

        if (latestBmi >= BMI_OVERWEIGHT) {
            riskLevel = maxRisk(riskLevel, "attention");
            addAnomaly(anomalies, "bmi", "warning", latestBmi,
                    "BMI " + format1(latestBmi) + " 属于超重范围",
                    "建议控制总热量并增加运动");
        }

        if (latestSystolic >= BP_SYSTOLIC_HIGH || latestDiastolic >= BP_DIASTOLIC_HIGH) {
            riskLevel = maxRisk(riskLevel, "warning");
            addAnomaly(anomalies, "bp", "warning", latestSystolic,
                    "血压 " + (int) latestSystolic + "/" + (int) latestDiastolic + " mmHg 偏高",
                    "建议监测血压并咨询医生");
        }

        String glucoseTrendText = "";
        if (count >= 2) {
            double oldestGlucose = glucoseOf(history.get(count - 1));
            if (oldestGlucose > 0 && latestGlucose > 0) {
                double change = latestGlucose - oldestGlucose;
                if (change >= 0.5) {
                    riskLevel = maxRisk(riskLevel, "attention");
                    glucoseTrendText = "，较最早记录上升约 " + format1(change) + " mmol/L";
                    if (anomalies.stream().noneMatch(a -> "glucose".equals(a.get("type")))) {
                        addAnomaly(anomalies, "glucose", "warning", latestGlucose,
                                "近阶段空腹血糖呈上升趋势",
                                "建议加强血糖监测");
                    }
                } else if (change <= -0.5) {
                    glucoseTrendText = "，较最早记录下降约 " + format1(Math.abs(change)) + " mmol/L";
                } else {
                    glucoseTrendText = "，整体波动较小";
                }
            }
        }

        String summary = buildSummaryText(count, latestGlucose, latestBmi, latestSystolic, latestDiastolic,
                glucoseTrendText, anomalies);
        String suggestion = firstSuggestion(anomalies);
        return new LocalTrendResult(summary, riskLevel, suggestion, anomalies);
    }

    private static String buildSummaryText(int count, double glucose, double bmi,
                                           double systolic, double diastolic,
                                           String glucoseTrendText,
                                           List<Map<String, Object>> anomalies) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据近 ").append(count).append(" 条健康记录：");

        if (glucose > 0) {
            sb.append("最新空腹血糖 ").append(format1(glucose)).append(" mmol/L（")
                    .append(glucoseLabel(glucose)).append(')');
            if (!glucoseTrendText.isBlank()) {
                sb.append(glucoseTrendText);
            }
            sb.append('。');
        }

        if (bmi > 0) {
            sb.append(" BMI ").append(format1(bmi)).append("（").append(bmiLabel(bmi)).append("）。");
        }

        if (systolic > 0 && diastolic > 0) {
            sb.append(" 血压 ").append((int) systolic).append('/').append((int) diastolic)
                    .append(" mmHg（").append(bpLabel(systolic, diastolic)).append("）。");
        }

        if (anomalies.isEmpty()) {
            sb.append(" 整体指标在可接受范围内，请继续保持规律记录。");
        } else {
            sb.append(" 建议加强自我监测，必要时咨询内分泌科医生。");
        }
        return sb.toString().trim();
    }

    private static String firstSuggestion(List<Map<String, Object>> anomalies) {
        for (Map<String, Object> anomaly : anomalies) {
            Object suggestion = anomaly.get("suggestion");
            if (suggestion != null && !String.valueOf(suggestion).isBlank()) {
                return String.valueOf(suggestion).trim();
            }
        }
        return "建议复查相关指标，必要时咨询内分泌科医生。";
    }

    private static void addAnomaly(List<Map<String, Object>> anomalies, String type, String severity,
                                   double value, String description, String suggestion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("severity", severity);
        item.put("value", value);
        item.put("description", description);
        item.put("alert", description);
        item.put("suggestion", suggestion);
        anomalies.add(item);
    }

    private static double glucoseOf(Map<String, Object> record) {
        if (record == null) {
            return 0;
        }
        return numberValue(record.get("fastingGlucose"), record.get("fasting_glucose"));
    }

    private static String glucoseLabel(double value) {
        if (value >= GLUCOSE_CRITICAL) {
            return "明显偏高";
        }
        if (value >= GLUCOSE_WARNING) {
            return "偏高";
        }
        if (value >= GLUCOSE_NORMAL_MAX) {
            return "偏高区间";
        }
        return "正常";
    }

    private static String bmiLabel(double bmi) {
        if (bmi >= BMI_OVERWEIGHT) {
            return "超重";
        }
        if (bmi < 18.5) {
            return "偏瘦";
        }
        return "正常";
    }

    private static String bpLabel(double systolic, double diastolic) {
        if (systolic >= BP_SYSTOLIC_HIGH || diastolic >= BP_DIASTOLIC_HIGH) {
            return "偏高";
        }
        if (systolic >= 130 || diastolic >= 85) {
            return "正常偏高";
        }
        return "正常";
    }

    private static String maxRisk(String current, String candidate) {
        return riskRank(candidate) > riskRank(current) ? candidate : current;
    }

    private static int riskRank(String riskLevel) {
        if (riskLevel == null) {
            return 0;
        }
        return switch (riskLevel.toLowerCase()) {
            case "critical" -> 3;
            case "warning" -> 2;
            case "attention" -> 1;
            default -> 0;
        };
    }

    private static String format1(double value) {
        return String.format("%.1f", value);
    }

    private static double numberValue(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // try next
            }
        }
        return 0;
    }
}
