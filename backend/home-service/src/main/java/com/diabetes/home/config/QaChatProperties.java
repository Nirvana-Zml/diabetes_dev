package com.diabetes.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dify.chatbots.qa")
public class QaChatProperties {

    private String apiKey = "";
    private int queryMaxLength = 500;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getQueryMaxLength() { return queryMaxLength; }
    public void setQueryMaxLength(int queryMaxLength) { this.queryMaxLength = queryMaxLength; }
}
