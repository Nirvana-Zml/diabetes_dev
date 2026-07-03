package com.diabetes.user.service;

import com.diabetes.user.mapper.AdminStatsMapper;
import com.diabetes.user.mapper.AdminMapper;
import com.diabetes.user.mapper.UserMapper;
import com.diabetes.user.entity.Admin;
import com.diabetes.user.entity.User;
import com.diabetes.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AdminStatsService {

    private static final Map<Object, String> GENDER_LABELS = Map.of(
            0, "未知", 1, "男", 2, "女");
    private static final Map<Object, String> DIABETES_TYPE_LABELS = Map.of(
            0, "无糖尿病", 1, "糖尿病前期", 2, "一型糖尿病", 3, "二型糖尿病", 4, "妊娠糖尿病", 9, "未知");
    private static final Map<Object, String> RISK_LEVEL_LABELS = Map.of(
            1, "低风险", 2, "中风险", 3, "高风险");
    private static final Map<Object, String> CHECKIN_TYPE_LABELS = Map.of(
            1, "饮食", 2, "运动", 3, "用药", 4, "血糖", 5, "血压", 6, "体重", 7, "复诊");
    private static final Map<Object, String> AGE_GROUP_LABELS = Map.of(
            "unknown", "未填写",
            "under_18", "18岁以下",
            "18_29", "18-29岁",
            "30_44", "30-44岁",
            "45_59", "45-59岁",
            "60_plus", "60岁及以上");
    private static final Map<String, String> MESSAGE_TYPE_LABELS = Map.of(
            "risk_assess", "风险评估",
            "plan_generate", "方案生成",
            "consult_reply", "问诊回复",
            "checkin_analysis", "打卡分析");

    private final AdminStatsMapper adminStatsMapper;
    private final UserMapper userMapper;
    private final AdminMapper adminMapper;

    public AdminStatsService(AdminStatsMapper adminStatsMapper,
                             UserMapper userMapper,
                             AdminMapper adminMapper) {
        this.adminStatsMapper = adminStatsMapper;
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", buildUserOverview());
        result.put("health", buildHealthOverview());
        result.put("checkin", buildCheckinOverview());
        result.put("consultation", buildConsultationOverview());
        result.put("plan", buildPlanOverview());
        result.put("content", buildContentOverview());
        result.put("messages", buildMessageOverview());
        result.put("distributions", buildDistributions());
        return result;
    }

    public Map<String, Object> getTrends(int days) {
        int safeDays = Math.min(Math.max(days, 7), 90);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("user_registration", adminStatsMapper.userRegistrationTrend(safeDays));
        result.put("daily_checkin", adminStatsMapper.dailyCheckinTrend(safeDays));
        return result;
    }

    public Map<String, Object> listUsers(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        int total = adminStatsMapper.countUsersForList();
        List<Map<String, Object>> users = adminStatsMapper.listUsersWithStats(offset, safeSize);
        users.forEach(this::normalizeUserRow);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", users);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    public Map<String, Object> getSubjectBrief(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new BusinessException(400, "用户 ID 不能为空");
        }
        String trimmed = subjectId.trim();
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("subject_id", trimmed);

        User user = userMapper.findById(trimmed);
        if (user != null) {
            brief.put("role", "user");
            brief.put("username", user.getUsername());
            brief.put("nickname", user.getNickname());
            brief.put("phone", user.getPhone());
            brief.put("email", user.getEmail());
            return brief;
        }

        Admin admin = adminMapper.findById(trimmed);
        if (admin != null) {
            brief.put("role", "admin");
            brief.put("username", admin.getUsername());
            brief.put("nickname", admin.getUsername());
            brief.put("phone", null);
            brief.put("email", null);
            return brief;
        }

        throw new BusinessException(404, "用户不存在");
    }

    private Map<String, Object> buildUserOverview() {
        String today = LocalDate.now().toString();
        String weekAgo = LocalDate.now().minusDays(6).toString();
        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", adminStatsMapper.countUsers());
        users.put("new_today", adminStatsMapper.countNewUsers(today));
        users.put("new_week", adminStatsMapper.countNewUsers(weekAgo));
        users.put("active_week", adminStatsMapper.countActiveUsers(7));
        return users;
    }

    private Map<String, Object> buildHealthOverview() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("total_records", adminStatsMapper.countHealthRecords());
        health.put("total_assessments", adminStatsMapper.countRiskAssessments());
        return health;
    }

    private Map<String, Object> buildCheckinOverview() {
        String today = LocalDate.now().toString();
        Map<String, Object> checkin = new LinkedHashMap<>();
        checkin.put("total", adminStatsMapper.countCheckins());
        checkin.put("today", adminStatsMapper.countCheckinsSince(today));
        return checkin;
    }

    private Map<String, Object> buildConsultationOverview() {
        Map<String, Object> consultation = new LinkedHashMap<>();
        consultation.put("total_sessions", adminStatsMapper.countConsultationSessions());
        consultation.put("active_sessions", adminStatsMapper.countActiveConsultationSessions());
        consultation.put("total_messages", adminStatsMapper.countConsultationMessages());
        Double avgRating = adminStatsMapper.avgConsultationRating();
        consultation.put("avg_rating", avgRating == null ? null : Math.round(avgRating * 10.0) / 10.0);
        return consultation;
    }

    private Map<String, Object> buildPlanOverview() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("total_plans", adminStatsMapper.countHealthPlans());
        plan.put("users_with_plans", adminStatsMapper.countUsersWithPlans());
        return plan;
    }

    private Map<String, Object> buildContentOverview() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("total_articles", adminStatsMapper.countArticles());
        content.put("published_articles", adminStatsMapper.countPublishedArticles());
        content.put("total_reads", adminStatsMapper.countArticleReads());
        content.put("total_videos", adminStatsMapper.countVideos());
        return content;
    }

    private Map<String, Object> buildMessageOverview() {
        Map<String, Object> messages = new LinkedHashMap<>();
        messages.put("total", adminStatsMapper.countUserMessages());
        messages.put("unread", adminStatsMapper.countUnreadUserMessages());
        return messages;
    }

    private Map<String, Object> buildDistributions() {
        Map<String, Object> distributions = new LinkedHashMap<>();
        distributions.put("gender", labelDistribution(adminStatsMapper.countUsersByGender(), GENDER_LABELS));
        distributions.put("age_group", labelDistribution(adminStatsMapper.countUsersByAgeGroup(), AGE_GROUP_LABELS));
        distributions.put("diabetes_type", labelDistribution(adminStatsMapper.countByDiabetesType(), DIABETES_TYPE_LABELS));
        distributions.put("risk_level", labelDistribution(adminStatsMapper.countByRiskLevel(), RISK_LEVEL_LABELS));
        distributions.put("checkin_type", labelDistribution(adminStatsMapper.countCheckinsByType(), CHECKIN_TYPE_LABELS));
        distributions.put("message_type", labelMessageTypes(adminStatsMapper.countUserMessagesByType()));
        return distributions;
    }

    private List<Map<String, Object>> labelDistribution(List<Map<String, Object>> rows,
                                                          Map<Object, String> labels) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("key");
            Object value = row.get("value");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", resolveDistributionLabel(key, labels));
            item.put("value", toInt(value));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> labelMessageTypes(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("key"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", MESSAGE_TYPE_LABELS.getOrDefault(key, key));
            item.put("value", toInt(row.get("value")));
            result.add(item);
        }
        return result;
    }

    private String resolveDistributionLabel(Object key, Map<Object, String> labels) {
        if (key == null) {
            return "未知";
        }
        return labels.getOrDefault(key, String.valueOf(key));
    }

    private void normalizeUserRow(Map<String, Object> row) {
        Object gender = row.get("gender");
        row.put("gender_label", GENDER_LABELS.getOrDefault(gender, "未知"));
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
