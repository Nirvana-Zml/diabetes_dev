package com.diabetes.health.dto;

import jakarta.validation.constraints.NotBlank;

public class MedicalHistoryItem {

    private String diseaseCode;
    @NotBlank
    private String diseaseName;
    private String diagnosedDate;
    private Integer status;
    private String note;

    public String getDiseaseCode() { return diseaseCode; }
    public void setDiseaseCode(String diseaseCode) { this.diseaseCode = diseaseCode; }
    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }
    public String getDiagnosedDate() { return diagnosedDate; }
    public void setDiagnosedDate(String diagnosedDate) { this.diagnosedDate = diagnosedDate; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
