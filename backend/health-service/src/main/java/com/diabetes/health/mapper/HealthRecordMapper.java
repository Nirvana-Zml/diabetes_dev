package com.diabetes.health.mapper;

import com.diabetes.health.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HealthRecordMapper {

    int insert(HealthRecord record);

    List<HealthRecord> findByUserId(@Param("userId") String userId, @Param("limit") int limit);

    HealthRecord findLatestByUserId(@Param("userId") String userId);

    int insertMedicalHistory(HealthRecordMedicalHistory history);

    int insertMedication(HealthRecordMedication medication);

    int insertFamilyHistory(HealthRecordFamilyHistory familyHistory);

    List<HealthRecordMedicalHistory> findMedicalHistoriesByRecordId(@Param("recordId") String recordId);

    List<HealthRecordMedication> findMedicationsByRecordId(@Param("recordId") String recordId);

    List<HealthRecordFamilyHistory> findFamilyHistoriesByRecordId(@Param("recordId") String recordId);
}
