package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinReminderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface CheckinReminderLogMapper {

    CheckinReminderLog findByRuleAndDate(@Param("ruleId") String ruleId,
                                         @Param("remindDate") LocalDate remindDate);

    int insert(CheckinReminderLog log);

    int updateStatus(@Param("logId") String logId,
                     @Param("userId") String userId,
                     @Param("status") int status);

    int updateSnooze(@Param("logId") String logId,
                     @Param("userId") String userId,
                     @Param("status") int status,
                     @Param("snoozeUntil") java.time.LocalDateTime snoozeUntil,
                     @Param("snoozeCount") int snoozeCount);

    CheckinReminderLog findByIdAndUser(@Param("logId") String logId,
                                       @Param("userId") String userId);
}
