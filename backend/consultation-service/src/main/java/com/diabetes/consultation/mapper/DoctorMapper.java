package com.diabetes.consultation.mapper;

import com.diabetes.consultation.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DoctorMapper {

    List<Doctor> findAll(@Param("department") String department,
                         @Param("keyword") String keyword,
                         @Param("status") Integer status);

    Doctor findById(@Param("doctorId") String doctorId);

    int incrementConsultationCount(@Param("doctorId") String doctorId);
}
