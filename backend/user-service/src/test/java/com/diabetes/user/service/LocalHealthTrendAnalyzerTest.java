package com.diabetes.user.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalHealthTrendAnalyzerTest {

    @Test
    void analyze_emptyHistory() {
        LocalHealthTrendAnalyzer.LocalTrendResult result = LocalHealthTrendAnalyzer.analyze(List.of());

        assertTrue(result.summary().contains("暂无健康数据"));
        assertEquals("normal", result.riskLevel());
    }

    @Test
    void analyze_highGlucoseWithTrend() {
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("fastingGlucose", 8.0);
        latest.put("bmi", 21.3);
        latest.put("systolicBp", 128);
        latest.put("diastolicBp", 82);
        Map<String, Object> older = new LinkedHashMap<>();
        older.put("fastingGlucose", 7.3);

        LocalHealthTrendAnalyzer.LocalTrendResult result =
                LocalHealthTrendAnalyzer.analyze(List.of(latest, older));

        assertTrue(result.summary().contains("根据近 2 条健康记录"));
        assertTrue(result.summary().contains("空腹血糖"));
        assertFalse(result.summary().contains("AI分析暂时不可用"));
        assertEquals("warning", result.riskLevel());
        assertFalse(result.anomalies().isEmpty());
    }

    @Test
    void resolveSummary_replacesPlaceholder() {
        Map<String, Object> latest = Map.of("fastingGlucose", 5.5, "bmi", 22.0);
        String resolved = HealthTrendSummaryHelper.resolveSummary(
                "已记录7条健康数据，但AI分析暂时不可用，请稍后重试或查看原始数据。",
                List.of(latest, latest));

        assertTrue(resolved.contains("根据近"));
        assertFalse(resolved.contains("AI分析暂时不可用"));
    }
}
