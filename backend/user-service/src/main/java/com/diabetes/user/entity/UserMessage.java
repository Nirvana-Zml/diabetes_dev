package com.diabetes.user.entity;

import java.time.LocalDateTime;

public class UserMessage {

    private String messageId;
    private String userId;
    private String messageType;
    private String status;
    private String title;
    private String summary;
    private String bizId;
    private String linkPath;
    private String linkQuery;
    private String extra;
    private Boolean read;
    private LocalDateTime readAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getLinkPath() { return linkPath; }
    public void setLinkPath(String linkPath) { this.linkPath = linkPath; }
    public String getLinkQuery() { return linkQuery; }
    public void setLinkQuery(String linkQuery) { this.linkQuery = linkQuery; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
