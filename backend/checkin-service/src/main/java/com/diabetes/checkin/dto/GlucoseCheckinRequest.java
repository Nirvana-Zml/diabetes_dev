package com.diabetes.checkin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GlucoseCheckinRequest {

    @NotBlank(message = "打卡日期不能为空")
    @JsonAlias("checkin_date")
    private String checkinDate;

    @NotNull(message = "血糖值不能为空")
    @DecimalMin(value = "2.0", message = "血糖值不能低于 2.0 mmol/L")
    @DecimalMax(value = "30.0", message = "血糖值不能高于 30.0 mmol/L")
    @JsonAlias("glucose_value")
    private BigDecimal glucoseValue;

    @Min(value = 1, message = "测量时段无效")
    @Max(value = 4, message = "测量时段无效")
    @JsonAlias("measure_context")
    private Integer measureContext = 4;

    @Min(value = 1, message = "单位无效")
    @Max(value = 2, message = "单位无效")
    private Integer unit = 1;

    public String getCheckinDate() { return checkinDate; }
    public void setCheckinDate(String checkinDate) { this.checkinDate = checkinDate; }
    public BigDecimal getGlucoseValue() { return glucoseValue; }
    public void setGlucoseValue(BigDecimal glucoseValue) { this.glucoseValue = glucoseValue; }
    public Integer getMeasureContext() { return measureContext; }
    public void setMeasureContext(Integer measureContext) { this.measureContext = measureContext; }
    public Integer getUnit() { return unit; }
    public void setUnit(Integer unit) { this.unit = unit; }
}
