package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinReminderRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CheckinReminderRuleMapper {

    List<CheckinReminderRule> findByUserId(@Param("userId") String userId);

    int deleteByUserId(@Param("userId") String userId);

    int insert(CheckinReminderRule rule);
}
