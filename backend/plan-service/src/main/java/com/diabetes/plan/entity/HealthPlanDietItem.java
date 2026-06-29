package com.diabetes.plan.entity;

import java.math.BigDecimal;

public class HealthPlanDietItem {

    private String itemId;
    private String planId;
    private Integer mealPeriod;
    private String foodName;
    private String portion;
    private Integer calories;
    private BigDecimal giValue;
    private String note;
    private Integer sortOrder;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public Integer getMealPeriod() { return mealPeriod; }
    public void setMealPeriod(Integer mealPeriod) { this.mealPeriod = mealPeriod; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public String getPortion() { return portion; }
    public void setPortion(String portion) { this.portion = portion; }
    public Integer getCalories() { return calories; }
    public void setCalories(Integer calories) { this.calories = calories; }
    public BigDecimal getGiValue() { return giValue; }
    public void setGiValue(BigDecimal giValue) { this.giValue = giValue; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
