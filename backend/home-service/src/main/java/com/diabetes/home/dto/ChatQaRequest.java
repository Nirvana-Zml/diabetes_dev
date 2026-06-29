package com.diabetes.home.dto;

public class ChatQaRequest {

    private String query;
    private String conversationId;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
