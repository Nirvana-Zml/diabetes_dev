package com.diabetes.checkin.mapper;

import com.diabetes.checkin.entity.CheckinMedicationDetail;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

public interface CheckinMedicationDetailMapper {

    void insert(CheckinMedicationDetail detail);

    List<CheckinMedicationDetail> findByUserAndDate(@Param("userId") String userId,
                                                    @Param("checkinDate") LocalDate checkinDate);
}
