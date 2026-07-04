package com.diabetes.user.service;

import java.util.List;
import java.util.Map;

/**
 * 识别 Dify / 后端降级占位文案，避免将其当作有效 AI 趋势解读展示或写入预警。
 */
final class HealthTrendSummaryHelper {

    private HealthTrendSummaryHelper() {
    }

    static String resolveSummary(String summary, List<Map<String, Object>> history) {
        if (!isUnavailablePlaceholder(summary)) {
            return summary == null ? "" : summary.trim();
        }
        if (history == null || history.isEmpty()) {
            return summary == null ? "" : summary.trim();
        }
        return LocalHealthTrendAnalyzer.analyze(history).summary();
    }

    static String resolveSuggestion(String suggestion, List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return suggestion == null || suggestion.isBlank()
                    ? "建议复查相关指标，必要时咨询内分泌科医生。"
                    : suggestion.trim();
        }
        LocalHealthTrendAnalyzer.LocalTrendResult local = LocalHealthTrendAnalyzer.analyze(history);
        if (suggestion == null || suggestion.isBlank()) {
            return local.suggestion();
        }
        return suggestion.trim();
    }

    static boolean isUnavailablePlaceholder(String summary) {
        if (summary == null || summary.isBlank()) {
            return false;
        }
        String text = summary.trim();
        return text.contains("AI分析暂时不可用")
                || text.contains("AI 分析暂时不可用")
                || text.contains("AI 分析暂不可用")
                || text.contains("AI 解读暂不可用")
                || text.contains("请稍后重试或查看原始数据");
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
