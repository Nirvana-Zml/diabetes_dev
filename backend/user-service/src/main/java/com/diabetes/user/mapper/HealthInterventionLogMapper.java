package com.diabetes.user.mapper;

import com.diabetes.user.entity.HealthInterventionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface HealthInterventionLogMapper {

    int insert(HealthInterventionLog log);

    int updateMessageId(@Param("planId") String planId, @Param("messageId") String messageId);

    HealthInterventionLog findActiveByUserId(@Param("userId") String userId);

    HealthInterventionLog findByPlanId(@Param("planId") String planId);

    int countSince(@Param("userId") String userId, @Param("since") LocalDateTime since);

    int countAlertsToday(@Param("userId") String userId);

    int resolveByPlanId(@Param("planId") String planId);

    int expireResolved(@Param("userId") String userId, @Param("now") LocalDateTime now);

    List<HealthInterventionLog> findRecentByUser(@Param("userId") String userId, @Param("limit") int limit);
}
