package com.diabetes.checkin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FoodCheckinRequest {

    @NotBlank
    private String checkinDate;

    @NotNull
    @Min(1)
    @Max(6)
    private Integer mealPeriod;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer sourceType;

    private String foodId;
    private String foodName;

    @DecimalMin("0.0001")
    private BigDecimal caloriesPerGram;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer inputUnit;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal inputAmount;

    private BigDecimal mlToGRatio;

    private String categoryId;
    private String imageObjectKey;

    public String getCheckinDate() { return checkinDate; }
    public void setCheckinDate(String checkinDate) { this.checkinDate = checkinDate; }
    public Integer getMealPeriod() { return mealPeriod; }
    public void setMealPeriod(Integer mealPeriod) { this.mealPeriod = mealPeriod; }
    public Integer getSourceType() { return sourceType; }
    public void setSourceType(Integer sourceType) { this.sourceType = sourceType; }
    public String getFoodId() { return foodId; }
    public void setFoodId(String foodId) { this.foodId = foodId; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public BigDecimal getCaloriesPerGram() { return caloriesPerGram; }
    public void setCaloriesPerGram(BigDecimal caloriesPerGram) { this.caloriesPerGram = caloriesPerGram; }
    public Integer getInputUnit() { return inputUnit; }
    public void setInputUnit(Integer inputUnit) { this.inputUnit = inputUnit; }
    public BigDecimal getInputAmount() { return inputAmount; }
    public void setInputAmount(BigDecimal inputAmount) { this.inputAmount = inputAmount; }
    public BigDecimal getMlToGRatio() { return mlToGRatio; }
    public void setMlToGRatio(BigDecimal mlToGRatio) { this.mlToGRatio = mlToGRatio; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
}
