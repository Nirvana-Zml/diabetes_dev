package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinDietDetail;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CheckinDietDetailMapper {

    void insert(CheckinDietDetail detail);

    List<CheckinDietDetail> findByUserAndDate(@Param("userId") String userId,
                                              @Param("checkinDate") LocalDate checkinDate);
}
