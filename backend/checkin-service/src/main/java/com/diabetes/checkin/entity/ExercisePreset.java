package com.diabetes.checkin.entity;

import java.math.BigDecimal;

public class ExercisePreset {

    private String exerciseId;
    private String exerciseName;
    private BigDecimal caloriesPerMinute;

    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }
    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String exerciseName) { this.exerciseName = exerciseName; }
    public BigDecimal getCaloriesPerMinute() { return caloriesPerMinute; }
    public void setCaloriesPerMinute(BigDecimal caloriesPerMinute) { this.caloriesPerMinute = caloriesPerMinute; }
}
