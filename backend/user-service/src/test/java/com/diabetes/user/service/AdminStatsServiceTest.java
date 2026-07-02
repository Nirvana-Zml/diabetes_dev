package com.diabetes.user.service;

import com.diabetes.user.mapper.AdminStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock
    private AdminStatsMapper adminStatsMapper;

    private AdminStatsService adminStatsService;

    @BeforeEach
    void setUp() {
        adminStatsService = new AdminStatsService(adminStatsMapper);
    }

    @Test
    void getOverview_aggregatesAllSections() {
        when(adminStatsMapper.countUsers()).thenReturn(100);
        when(adminStatsMapper.countNewUsers(anyString())).thenReturn(5, 20);
        when(adminStatsMapper.countActiveUsers(7)).thenReturn(30);
        when(adminStatsMapper.countHealthRecords()).thenReturn(80);
        when(adminStatsMapper.countRiskAssessments()).thenReturn(60);
        when(adminStatsMapper.countCheckins()).thenReturn(500);
        when(adminStatsMapper.countCheckinsSince(anyString())).thenReturn(12);
        when(adminStatsMapper.countConsultationSessions()).thenReturn(40);
        when(adminStatsMapper.countActiveConsultationSessions()).thenReturn(3);
        when(adminStatsMapper.countConsultationMessages()).thenReturn(200);
        when(adminStatsMapper.avgConsultationRating()).thenReturn(4.56);
        when(adminStatsMapper.countHealthPlans()).thenReturn(25);
        when(adminStatsMapper.countUsersWithPlans()).thenReturn(15);
        when(adminStatsMapper.countArticles()).thenReturn(10);
        when(adminStatsMapper.countPublishedArticles()).thenReturn(8);
        when(adminStatsMapper.countArticleReads()).thenReturn(120);
        when(adminStatsMapper.countVideos()).thenReturn(4);
        when(adminStatsMapper.countUserMessages()).thenReturn(50);
        when(adminStatsMapper.countUnreadUserMessages()).thenReturn(10);
        when(adminStatsMapper.countUsersByGender()).thenReturn(List.of(Map.of("key", 1, "value", 40)));
        when(adminStatsMapper.countUsersByAgeGroup()).thenReturn(List.of(Map.of("key", "30_44", "value", 20)));
        when(adminStatsMapper.countByDiabetesType()).thenReturn(List.of(Map.of("key", 3, "value", 10)));
        when(adminStatsMapper.countByRiskLevel()).thenReturn(List.of(Map.of("key", 2, "value", 8)));
        when(adminStatsMapper.countCheckinsByType()).thenReturn(List.of(Map.of("key", 4, "value", 100)));
        when(adminStatsMapper.countUserMessagesByType()).thenReturn(List.of(Map.of("key", "risk_assess", "value", 5)));

        Map<String, Object> overview = adminStatsService.getOverview();

        assertEquals(100, ((Map<?, ?>) overview.get("users")).get("total"));
        assertEquals(80, ((Map<?, ?>) overview.get("health")).get("total_records"));
        assertEquals(500, ((Map<?, ?>) overview.get("checkin")).get("total"));
        assertEquals(4.6, ((Map<?, ?>) overview.get("consultation")).get("avg_rating"));
        assertEquals(25, ((Map<?, ?>) overview.get("plan")).get("total_plans"));
        assertEquals(8, ((Map<?, ?>) overview.get("content")).get("published_articles"));
        assertEquals(10, ((Map<?, ?>) overview.get("messages")).get("unread"));

        Map<?, ?> distributions = (Map<?, ?>) overview.get("distributions");
        List<?> gender = (List<?>) distributions.get("gender");
        assertEquals("男", ((Map<?, ?>) gender.get(0)).get("label"));
    }

    @Test
    void getOverview_handlesNullRatingAndEmptyDistributions() {
        when(adminStatsMapper.countUsers()).thenReturn(0);
        when(adminStatsMapper.countNewUsers(anyString())).thenReturn(0);
        when(adminStatsMapper.countActiveUsers(7)).thenReturn(0);
        when(adminStatsMapper.countHealthRecords()).thenReturn(0);
        when(adminStatsMapper.countRiskAssessments()).thenReturn(0);
        when(adminStatsMapper.countCheckins()).thenReturn(0);
        when(adminStatsMapper.countCheckinsSince(anyString())).thenReturn(0);
        when(adminStatsMapper.countConsultationSessions()).thenReturn(0);
        when(adminStatsMapper.countActiveConsultationSessions()).thenReturn(0);
        when(adminStatsMapper.countConsultationMessages()).thenReturn(0);
        when(adminStatsMapper.avgConsultationRating()).thenReturn(null);
        when(adminStatsMapper.countHealthPlans()).thenReturn(0);
        when(adminStatsMapper.countUsersWithPlans()).thenReturn(0);
        when(adminStatsMapper.countArticles()).thenReturn(0);
        when(adminStatsMapper.countPublishedArticles()).thenReturn(0);
        when(adminStatsMapper.countArticleReads()).thenReturn(0);
        when(adminStatsMapper.countVideos()).thenReturn(0);
        when(adminStatsMapper.countUserMessages()).thenReturn(0);
        when(adminStatsMapper.countUnreadUserMessages()).thenReturn(0);
        when(adminStatsMapper.countUsersByGender()).thenReturn(null);
        when(adminStatsMapper.countUsersByAgeGroup()).thenReturn(List.of());
        when(adminStatsMapper.countByDiabetesType()).thenReturn(List.of());
        when(adminStatsMapper.countByRiskLevel()).thenReturn(List.of());
        when(adminStatsMapper.countCheckinsByType()).thenReturn(List.of());
        when(adminStatsMapper.countUserMessagesByType()).thenReturn(List.of());

        Map<String, Object> overview = adminStatsService.getOverview();

        assertNull(((Map<?, ?>) overview.get("consultation")).get("avg_rating"));
        assertTrue(((List<?>) ((Map<?, ?>) overview.get("distributions")).get("gender")).isEmpty());
    }

    @Test
    void getTrends_clampsDays() {
        when(adminStatsMapper.userRegistrationTrend(90)).thenReturn(List.of());
        when(adminStatsMapper.dailyCheckinTrend(90)).thenReturn(List.of());

        Map<String, Object> trends = adminStatsService.getTrends(200);

        assertEquals(90, trends.get("days"));
    }

    @Test
    void listUsers_normalizesRowsAndPaginates() {
        when(adminStatsMapper.countUsersForList()).thenReturn(1);
        when(adminStatsMapper.listUsersWithStats(0, 100)).thenReturn(List.of(
                new java.util.LinkedHashMap<>(Map.of(
                        "user_id", "u_1",
                        "username", "alice",
                        "gender", 2,
                        "checkin_count", "3"
                ))
        ));

        Map<String, Object> result = adminStatsService.listUsers(0, 200);

        assertEquals(1, result.get("page"));
        assertEquals(100, result.get("size"));
        List<?> users = (List<?>) result.get("users");
        assertEquals("女", ((Map<?, ?>) users.get(0)).get("gender_label"));
    }
}
