package com.diabetes.checkin.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CheckinGlucoseDetail {

    private String checkinId;
    private BigDecimal glucoseValue;
    private Integer measureContext;
    private Integer unit;
    private LocalDate checkinDate;
    private LocalDateTime recordTime;

    public String getCheckinId() { return checkinId; }
    public void setCheckinId(String checkinId) { this.checkinId = checkinId; }
    public BigDecimal getGlucoseValue() { return glucoseValue; }
    public void setGlucoseValue(BigDecimal glucoseValue) { this.glucoseValue = glucoseValue; }
    public Integer getMeasureContext() { return measureContext; }
    public void setMeasureContext(Integer measureContext) { this.measureContext = measureContext; }
    public Integer getUnit() { return unit; }
    public void setUnit(Integer unit) { this.unit = unit; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
}
