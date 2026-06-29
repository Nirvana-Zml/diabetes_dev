package com.diabetes.checkin.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CheckinDietDetail {

    private String checkinId;
    private Integer mealPeriod;
    private Integer sourceType;
    private String foodId;
    private String foodName;
    private String categoryName;
    private BigDecimal caloriesPerGram;
    private Integer inputUnit;
    private BigDecimal inputAmount;
    private BigDecimal grams;
    private Integer totalCalories;
    private String imageObjectKey;
    private LocalDateTime recordTime;

    public String getCheckinId() { return checkinId; }
    public void setCheckinId(String checkinId) { this.checkinId = checkinId; }
    public Integer getMealPeriod() { return mealPeriod; }
    public void setMealPeriod(Integer mealPeriod) { this.mealPeriod = mealPeriod; }
    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
    public String getFoodId() { return foodId; }
    public void setFoodId(String foodId) { this.foodId = foodId; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public BigDecimal getCaloriesPerGram() { return caloriesPerGram; }
    public void setCaloriesPerGram(BigDecimal caloriesPerGram) { this.caloriesPerGram = caloriesPerGram; }
    public Integer getInputUnit() { return inputUnit; }
    public void setInputUnit(Integer inputUnit) { this.inputUnit = inputUnit; }
    public BigDecimal getInputAmount() { return inputAmount; }
    public void setInputAmount(BigDecimal inputAmount) { this.inputAmount = inputAmount; }
    public BigDecimal getGrams() { return grams; }
    public void setGrams(BigDecimal grams) { this.grams = grams; }
    public Integer getTotalCalories() { return totalCalories; }
    public void setTotalCalories(Integer totalCalories) { this.totalCalories = totalCalories; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
}
