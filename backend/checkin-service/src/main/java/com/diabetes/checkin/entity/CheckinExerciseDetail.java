package com.diabetes.checkin.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CheckinExerciseDetail {

    private String checkinId;
    private Integer sourceType;
    private String exerciseId;
    private String exerciseName;
    private BigDecimal caloriesPerMinute;
    private Integer durationMinutes;
    private Integer caloriesBurned;
    private LocalDateTime recordTime;

    public String getCheckinId() { return checkinId; }
    public void setCheckinId(String checkinId) { this.checkinId = checkinId; }
    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }
    public BigDecimal getCaloriesPerMinute() { return caloriesPerMinute; }
    public void setCaloriesPerMinute(BigDecimal caloriesPerMinute) { this.caloriesPerMinute = caloriesPerMinute; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getCaloriesBurned() { return caloriesBurned; }
    public void setCaloriesBurned(Integer caloriesBurned) { this.caloriesBurned = caloriesBurned; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
}
