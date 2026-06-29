package com.diabetes.consultation.entity;

import java.time.LocalDateTime;

public class ConsultationMessage {

    private String messageId;
    private String sessionId;
    private Integer senderType;
    private String senderId;
    private Integer messageType;
    private String content;
    private Integer isAiGenerated;
    private String aiMetadata;
    private Integer isRead;
    private LocalDateTime sentAt;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getSenderType() { return senderType; }
    public void setSenderType(Integer senderType) { this.senderType = senderType; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public Integer getMessageType() { return messageType; }
    public void setMessageType(Integer messageType) { this.messageType = messageType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getIsAiGenerated() { return isAiGenerated; }
    public void setIsAiGenerated(Integer isAiGenerated) { this.isAiGenerated = isAiGenerated; }
    public String getAiMetadata() { return aiMetadata; }
    public void setAiMetadata(String aiMetadata) { this.aiMetadata = aiMetadata; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
