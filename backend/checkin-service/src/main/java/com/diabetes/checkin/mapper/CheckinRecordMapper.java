package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CheckinRecordMapper {

    int insert(CheckinRecord record);

    int countByUserTypeDate(@Param("userId") String userId,
                            @Param("checkinType") int checkinType,
                            @Param("checkinDate") LocalDate checkinDate);

    List<CheckinRecord> findByUserAndDateRange(@Param("userId") String userId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    List<CheckinRecord> findByUserAndDate(@Param("userId") String userId,
                                          @Param("checkinDate") LocalDate checkinDate);

    int sumPointsByUser(@Param("userId") String userId);

    List<LocalDate> findDistinctDates(@Param("userId") String userId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);

    List<CheckinRecord> findRecentByUser(@Param("userId") String userId,
                                         @Param("startDate") LocalDate startDate);
}
