package com.diabetes.audit.entity;

import java.time.LocalDateTime;

public class AuditActionDefinition {

    private String actionCode;
    private String labelZh;
    private String category;
    private Integer isSystem;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public void setLabelZh(String labelZh) {
        this.labelZh = labelZh;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
