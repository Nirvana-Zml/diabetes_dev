package com.diabetes.plan.service;

import com.diabetes.common.client.CheckinServiceClient;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private HealthServiceClient healthServiceClient;
    @Mock
    private CheckinServiceClient checkinServiceClient;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userServiceClient, healthServiceClient, checkinServiceClient, "internal-key");
    }

    @Test
    void buildUserProfile() {
        when(userServiceClient.getUserProfile("u_1", "internal-key")).thenReturn(new HashMap<>(Map.of(
                "nickname", "Alice", "birth_date", "1990-01-15", "gender", 1)));
        when(healthServiceClient.getLatestHealthProfile("u_1", "internal-key"))
                .thenReturn(Map.of("height", 170, "weight", 65, "exerciseFreq", 2, "dietType", "low_carb"));
        when(healthServiceClient.getLatestRiskAssessment("u_1", "internal-key"))
                .thenReturn(Map.of("assessmentId", "ra_1", "riskLevel", "low"));
        when(checkinServiceClient.getRecentCheckins("u_1", "internal-key", 14))
                .thenReturn(List.of(Map.of("checkinDate", "2024-01-01")));

        Map<String, Object> profile = userProfileService.buildUserProfile("u_1");

        assertEquals("u_1", profile.get("user_id"));
        assertEquals("male", profile.get("gender"));
        assertTrue((Integer) profile.get("age") > 0);
        assertEquals(170.0, profile.get("height"));
        assertEquals(65.0, profile.get("weight"));
        assertEquals("regular", profile.get("exerciseFrequency"));
        assertEquals("ra_1", profile.get("risk_assessment_id"));
    }

    @Test
    void buildUserProfile_emptyHealthProfile() {
        when(userServiceClient.getUserProfile("u_1", "internal-key")).thenReturn(new HashMap<>(Map.of(
                "birthDate", "invalid", "gender", "female")));
        when(healthServiceClient.getLatestHealthProfile("u_1", "internal-key")).thenReturn(Map.of());
        when(healthServiceClient.getLatestRiskAssessment("u_1", "internal-key")).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins("u_1", "internal-key", 14)).thenReturn(List.of());

        Map<String, Object> profile = userProfileService.buildUserProfile("u_1");

        assertEquals(35, profile.get("age"));
        assertEquals("female", profile.get("gender"));
        assertNull(profile.get("risk_data"));
        assertEquals("occasional", profile.get("exerciseFrequency"));
    }

    @Test
    void buildUserProfile_genderVariants() {
        when(userServiceClient.getUserProfile(eq("u_1"), anyString())).thenReturn(new HashMap<>(Map.of("gender", 2)));
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of("height", 160, "weight", 55));
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt())).thenReturn(List.of());
        assertEquals("female", userProfileService.buildUserProfile("u_1").get("gender"));

        when(userServiceClient.getUserProfile(eq("u_2"), anyString())).thenReturn(new HashMap<>(Map.of("gender", "unknown")));
        assertEquals("unknown", userProfileService.buildUserProfile("u_2").get("gender"));

        when(userServiceClient.getUserProfile(eq("u_3"), anyString())).thenReturn(new HashMap<>(Map.of("gender", 9)));
        assertEquals("unknown", userProfileService.buildUserProfile("u_3").get("gender"));
    }

    @Test
    void buildUserProfile_exerciseFreqMapping() {
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(new HashMap<>());
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt())).thenReturn(List.of());

        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 60, "exerciseFreq", 0));
        assertEquals("none", userProfileService.buildUserProfile("u_1").get("exerciseFrequency"));

        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 60, "exerciseFreq", 3));
        assertEquals("daily", userProfileService.buildUserProfile("u_1").get("exerciseFrequency"));

        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 60, "exerciseFreq", "bad"));
        assertEquals("occasional", userProfileService.buildUserProfile("u_1").get("exerciseFrequency"));
    }

    @Test
    void validateProfileForPlan_successFromProfile() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("height", 170);
        profile.put("weight", 65);
        assertDoesNotThrow(() -> userProfileService.validateProfileForPlan(profile));
    }

    @Test
    void validateProfileForPlan_fromHealthProfile() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("health_profile", Map.of("height", "170", "weight", "65.5"));
        userProfileService.validateProfileForPlan(profile);
        assertEquals(170.0, profile.get("height"));
        assertEquals(65.5, profile.get("weight"));
    }

    @Test
    void validateProfileForPlan_emptyHealthProfile() {
        Map<String, Object> profile = Map.of("health_profile", Map.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userProfileService.validateProfileForPlan(profile));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("未能读取健康档案"));
    }

    @Test
    void validateProfileForPlan_missingMetrics() {
        Map<String, Object> profile = Map.of(
                "health_profile", Map.of("height", 0, "weight", -1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userProfileService.validateProfileForPlan(profile));
        assertTrue(ex.getMessage().contains("缺少有效的身高、体重"));
    }

    @Test
    void validateProfileForPlan_invalidMetricStrings() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("height", "abc");
        profile.put("weight", "");
        Map<String, Object> health = new HashMap<>();
        health.put("height", "x");
        health.put("weight", null);
        profile.put("health_profile", health);
        assertThrows(BusinessException.class, () -> userProfileService.validateProfileForPlan(profile));
    }

    @Test
    void buildUserProfile_stringHeightWeight() {
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(new HashMap<>());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", "172", "weight", "68.5", "exerciseFreq", 1));
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt())).thenReturn(List.of());

        Map<String, Object> profile = userProfileService.buildUserProfile("u_str");
        assertEquals(172.0, profile.get("height"));
        assertEquals(68.5, profile.get("weight"));
        assertEquals("occasional", profile.get("exerciseFrequency"));
    }

    @Test
    void buildUserProfile_genderLeadingZeroNumeric() {
        when(userServiceClient.getUserProfile(eq("u_m"), anyString()))
                .thenReturn(new HashMap<>(Map.of("gender", "01")));
        when(userServiceClient.getUserProfile(eq("u_f"), anyString()))
                .thenReturn(new HashMap<>(Map.of("gender", "02")));
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 60));
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt())).thenReturn(List.of());

        assertEquals("male", userProfileService.buildUserProfile("u_m").get("gender"));
        assertEquals("female", userProfileService.buildUserProfile("u_f").get("gender"));
    }

    @Test
    void resolveMetric_positiveString() throws Exception {
        var method = UserProfileService.class.getDeclaredMethod("resolveMetric", Map.class, String.class);
        method.setAccessible(true);
        assertEquals(170.0, method.invoke(userProfileService, Map.of("height", "170"), "height"));
        assertNull(method.invoke(userProfileService, Map.of("height", "0"), "height"));
        assertNull(method.invoke(userProfileService, Map.of("weight", "-1"), "weight"));
    }

    @Test
    void validateProfileForPlan_zeroHeight() {
        Map<String, Object> profile = Map.of(
                "health_profile", Map.of("height", 0, "weight", 65));
        assertThrows(BusinessException.class, () -> userProfileService.validateProfileForPlan(profile));
    }

    @Test
    void resolveMetric_nullSource() throws Exception {
        var method = UserProfileService.class.getDeclaredMethod("resolveMetric", Map.class, String.class);
        method.setAccessible(true);
        assertNull(method.invoke(userProfileService, null, "height"));
    }

    @Test
    void resolveGender_numericDefault() {
        when(userServiceClient.getUserProfile(eq("u_4"), anyString()))
                .thenReturn(new HashMap<>(Map.of("gender", "9")));
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 60));
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt())).thenReturn(List.of());
        assertEquals("unknown", userProfileService.buildUserProfile("u_4").get("gender"));
    }

    @Test
    void buildUserProfile_nullGender() {
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(new HashMap<>());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 60));
        when(healthServiceClient.getLatestRiskAssessment(anyString(), anyString())).thenReturn(Map.of());
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt())).thenReturn(List.of());
        assertEquals("unknown", userProfileService.buildUserProfile("u_5").get("gender"));
    }
}
