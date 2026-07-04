package com.diabetes.checkin.entity;

import java.math.BigDecimal;

public class UserFoodPreset {

    private String foodId;
    private String userId;
    private String categoryId;
    private String foodName;
    private BigDecimal caloriesPerGram;
    private Integer isLiquid;
    private BigDecimal mlToGRatio;
    private String imageObjectKey;

    public String getFoodId() { return foodId; }
    public void setFoodId(String foodId) { this.foodId = foodId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public BigDecimal getCaloriesPerGram() { return caloriesPerGram; }
    public void setCaloriesPerGram(BigDecimal caloriesPerGram) { this.caloriesPerGram = caloriesPerGram; }
    public Integer getIsLiquid() { return isLiquid; }
    public void setIsLiquid(Integer isLiquid) { this.isLiquid = isLiquid; }
    public BigDecimal getMlToGRatio() { return mlToGRatio; }
    public void setMlToGRatio(BigDecimal mlToGRatio) { this.mlToGRatio = mlToGRatio; }
    public String getImageObjectKey() { return imageObjectKey; }
    public void setImageObjectKey(String imageObjectKey) { this.imageObjectKey = imageObjectKey; }
}
