package com.diabetes.consultation.entity;

import java.time.LocalDateTime;

public class ConsultationSession {

    private String sessionId;
    private String userId;
    private String doctorId;
    private String difyConversationId;
    private String difyWorkflowId;
    private Integer status;
    private String title;
    private String initialMessage;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Integer messageCount;
    private Integer rating;
    private String feedback;
    private Integer closeReason;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public String getDifyConversationId() { return difyConversationId; }
    public void setDifyConversationId(String difyConversationId) { this.difyConversationId = difyConversationId; }
    public String getDifyWorkflowId() { return difyWorkflowId; }
    public void setDifyWorkflowId(String difyWorkflowId) { this.difyWorkflowId = difyWorkflowId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInitialMessage() { return initialMessage; }
    public void setInitialMessage(String initialMessage) { this.initialMessage = initialMessage; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Integer getCloseReason() { return closeReason; }
    public void setCloseReason(Integer closeReason) { this.closeReason = closeReason; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
