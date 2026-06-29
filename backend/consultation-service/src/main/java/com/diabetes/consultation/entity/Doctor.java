package com.diabetes.consultation.entity;

import java.math.BigDecimal;

public class Doctor {

    private String doctorId;
    private String name;
    private String title;
    private String department;
    private String hospital;
    private String avatarId;
    private String introduction;
    private String specialties;
    private BigDecimal rating;
    private Integer consultationCount;
    private Integer status;

    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }
    public String getAvatarId() { return avatarId; }
    public void setAvatarId(String avatarId) { this.avatarId = avatarId; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getSpecialties() { return specialties; }
    public void setSpecialties(String specialties) { this.specialties = specialties; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getConsultationCount() { return consultationCount; }
    public void setConsultationCount(Integer consultationCount) { this.consultationCount = consultationCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
