package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinReminderRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CheckinReminderRuleMapper {

    List<CheckinReminderRule> findByUserId(@Param("userId") String userId);

    int deleteByUserId(@Param("userId") String userId);

    int deleteByUserIdAndSource(@Param("userId") String userId, @Param("ruleSource") String ruleSource);

    int deleteExpiredSystemRules(@Param("userId") String userId, @Param("now") java.time.LocalDateTime now);

    CheckinReminderRule findUserRule(@Param("userId") String userId,
                                     @Param("checkinType") int checkinType,
                                     @Param("remindTime") java.time.LocalTime remindTime);

    int insert(CheckinReminderRule rule);
}
