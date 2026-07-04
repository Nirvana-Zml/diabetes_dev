package com.diabetes.checkin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class FoodRecognitionRequest {

    @NotBlank
    private String imageObjectKey;

    @Min(1)
    @Max(6)
    private Integer mealPeriod;

    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public Integer getMealPeriod() { return mealPeriod; }
    public void setMealPeriod(Integer mealPeriod) { this.mealPeriod = mealPeriod; }
}
