package com.diabetes.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminStatsMapper {

    int countUsers();

    int countNewUsers(@Param("since") String since);

    int countActiveUsers(@Param("days") int days);

    List<Map<String, Object>> countUsersByGender();

    List<Map<String, Object>> countUsersByAgeGroup();

    List<Map<String, Object>> userRegistrationTrend(@Param("days") int days);

    int countHealthRecords();

    int countRiskAssessments();

    List<Map<String, Object>> countByDiabetesType();

    List<Map<String, Object>> countByRiskLevel();

    int countCheckins();

    int countCheckinsSince(@Param("since") String since);

    List<Map<String, Object>> countCheckinsByType();

    List<Map<String, Object>> dailyCheckinTrend(@Param("days") int days);

    int countConsultationSessions();

    int countActiveConsultationSessions();

    int countConsultationMessages();

    Double avgConsultationRating();

    int countHealthPlans();

    int countUsersWithPlans();

    int countArticles();

    int countPublishedArticles();

    int countArticleReads();

    int countVideos();

    int countUserMessages();

    int countUnreadUserMessages();

    List<Map<String, Object>> countUserMessagesByType();

    int countUsersForList();

    List<Map<String, Object>> listUsersWithStats(@Param("offset") int offset, @Param("limit") int limit);
}
