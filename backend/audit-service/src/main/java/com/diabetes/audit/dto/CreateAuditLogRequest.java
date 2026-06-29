package com.diabetes.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateAuditLogRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String action;

    @NotBlank
    private String resource;

    private String detail;

    private String ipAddress;

    private String userAgent;

    @NotNull
    private Integer result;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }
}
