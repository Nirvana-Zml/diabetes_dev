package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinExerciseDetail;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CheckinExerciseDetailMapper {

    void insert(CheckinExerciseDetail detail);

    List<CheckinExerciseDetail> findByUserAndDate(@Param("userId") String userId,
                                                  @Param("checkinDate") LocalDate checkinDate);
}
