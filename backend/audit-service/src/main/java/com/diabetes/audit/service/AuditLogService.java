package com.diabetes.audit.service;

import com.diabetes.audit.dto.CreateAuditLogRequest;
import com.diabetes.audit.entity.AuditLog;
import com.diabetes.audit.mapper.AuditLogMapper;
import com.diabetes.common.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public AuditLog create(CreateAuditLogRequest request) {
        AuditLog log = new AuditLog();
        log.setLogId(IdGenerator.nextId("alog_"));
        log.setUserId(request.getUserId());
        log.setAction(request.getAction());
        log.setResource(request.getResource());
        try {
            log.setDetail(request.getDetail() == null ? null : objectMapper.writeValueAsString(request.getDetail()));
        } catch (Exception e) {
            log.setDetail(null);
        }
        log.setIpAddress(request.getIpAddress());
        log.setUserAgent(request.getUserAgent());
        log.setResult(request.getResult() == null ? 1 : request.getResult());
        auditLogMapper.insert(log);
        return log;
    }

    public List<AuditLog> listByUser(String userId, int page, int size) {
        return auditLogMapper.findByUserId(userId, (page - 1) * size, size);
    }
}
