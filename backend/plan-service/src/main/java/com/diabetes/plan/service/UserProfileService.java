package com.diabetes.plan.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.client.CheckinServiceClient;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.client.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合用户基本信息、健康档案、风险评估与近期打卡，构建方案生成所需的用户画像。
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserServiceClient userServiceClient;
    private final HealthServiceClient healthServiceClient;
    private final CheckinServiceClient checkinServiceClient;
    private final String difyInternalKey;

    public UserProfileService(UserServiceClient userServiceClient,
                              HealthServiceClient healthServiceClient,
                              CheckinServiceClient checkinServiceClient,
                              @Value("${dify-internal.key:}") String difyInternalKey) {
        this.userServiceClient = userServiceClient;
        this.healthServiceClient = healthServiceClient;
        this.checkinServiceClient = checkinServiceClient;
        this.difyInternalKey = difyInternalKey;
    }

    public Map<String, Object> buildUserProfile(String userId) {
        Map<String, Object> userProfile = new LinkedHashMap<>(userServiceClient.getUserProfile(userId, difyInternalKey));
        Map<String, Object> healthProfile = healthServiceClient.getLatestHealthProfile(userId, difyInternalKey);
        Map<String, Object> riskData = healthServiceClient.getLatestRiskAssessment(userId, difyInternalKey);
        List<Map<String, Object>> recentCheckins = checkinServiceClient.getRecentCheckins(userId, difyInternalKey, 14);

        if (healthProfile.isEmpty()) {
            log.warn("plan-service 未从 health-service 读取到用户 {} 的健康档案（内部接口返回空，请检查 DIFY_INTERNAL_KEY 与服务连通性）", userId);
        }

        int age = resolveAge(userProfile);
        String gender = resolveGender(userProfile);
        userProfile.put("age", age);
        userProfile.put("gender", gender);

        Double height = resolveMetric(healthProfile, "height");
        Double weight = resolveMetric(healthProfile, "weight");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("user_id", userId);
        profile.put("user_profile", userProfile);
        profile.put("health_profile", healthProfile);
        profile.put("risk_data", riskData.isEmpty() ? null : riskData);
        profile.put("checkin_data", Map.of(
                "recent_days", 14,
                "records", recentCheckins,
                "total_recent", recentCheckins.size()
        ));
        profile.put("health_record_id", healthProfile.get("recordId"));
        profile.put("risk_assessment_id", riskData.get("assessmentId"));
        profile.put("age", age);
        profile.put("gender", gender);
        profile.put("height", height);
        profile.put("weight", weight);
        profile.put("exerciseFrequency", mapExerciseFreq(healthProfile.get("exerciseFreq")));
        profile.put("dietType", healthProfile.getOrDefault("dietType", "balanced"));
        return profile;
    }

    public void validateProfileForPlan(Map<String, Object> profile) {
        Double height = resolveMetric(profile, "height");
        Double weight = resolveMetric(profile, "weight");

        if (height == null || weight == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> healthProfile = (Map<String, Object>) profile.get("health_profile");
            if (height == null && healthProfile != null) {
                height = resolveMetric(healthProfile, "height");
            }
            if (weight == null && healthProfile != null) {
                weight = resolveMetric(healthProfile, "weight");
            }
        }

        if (height != null && weight != null) {
            profile.put("height", height);
            profile.put("weight", weight);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> healthProfile = (Map<String, Object>) profile.get("health_profile");
        boolean fetchedEmpty = healthProfile == null || healthProfile.isEmpty();
        if (fetchedEmpty) {
            throw new BusinessException(400,
                    "未能读取健康档案（plan-service 与 health-service 通信异常或 DIFY_INTERNAL_KEY 未配置）。"
                            + "请确认已在「个人中心」保存身高体重，并检查 docker-compose 中 DIFY_INTERNAL_KEY 是否与 .env 一致。");
        }
        throw new BusinessException(400,
                "健康档案中缺少有效的身高、体重，请在「个人中心」重新保存后重试");
    }

    private Double resolveMetric(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            return parsed > 0 ? parsed : null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(text);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int resolveAge(Map<String, Object> profile) {
        Object birthDate = profile.get("birth_date");
        if (birthDate == null) birthDate = profile.get("birthDate");
        if (birthDate == null) return 35;
        try {
            return Period.between(LocalDate.parse(birthDate.toString()), LocalDate.now()).getYears();
        } catch (Exception e) {
            return 35;
        }
    }

    private String resolveGender(Map<String, Object> profile) {
        Object gender = profile.get("gender");
        if (gender == null) return "unknown";
        String text = gender.toString().trim();
        if ("male".equalsIgnoreCase(text) || "1".equals(text)) return "male";
        if ("female".equalsIgnoreCase(text) || "2".equals(text)) return "female";
        try {
            return switch (Integer.parseInt(text)) {
                case 1 -> "male";
                case 2 -> "female";
                default -> "unknown";
            };
        } catch (NumberFormatException e) {
            return "unknown";
        }
    }

    private String mapExerciseFreq(Object freq) {
        if (freq == null) return "occasional";
        try {
            return switch (Integer.parseInt(freq.toString())) {
                case 0 -> "none";
                case 2 -> "regular";
                case 3 -> "daily";
                default -> "occasional";
            };
        } catch (NumberFormatException e) {
            return "occasional";
        }
    }
}
