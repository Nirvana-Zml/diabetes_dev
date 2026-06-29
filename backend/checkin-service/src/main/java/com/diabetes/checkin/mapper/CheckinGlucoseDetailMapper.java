package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinGlucoseDetail;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CheckinGlucoseDetailMapper {

    void insert(CheckinGlucoseDetail detail);

    List<CheckinGlucoseDetail> findByUserAndDate(@Param("userId") String userId,
                                                 @Param("checkinDate") LocalDate checkinDate);

    List<CheckinGlucoseDetail> findByUserAndDateRange(@Param("userId") String userId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);
}
