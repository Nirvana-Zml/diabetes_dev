package com.diabetes.audit.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityTest {

    @Test
    void auditLogGettersAndSetters() {
        AuditLog log = new AuditLog();
        LocalDateTime now = LocalDateTime.of(2024, 6, 1, 10, 30);

        log.setLogId("aud_1");
        log.setUserId("u_1");
        log.setAction("user.login");
        log.setResource("alice");
        log.setDetail("{\"role\":\"user\"}");
        log.setIpAddress("127.0.0.1");
        log.setUserAgent("JUnit");
        log.setResult(1);
        log.setCreatedAt(now);

        assertEquals("aud_1", log.getLogId());
        assertEquals("u_1", log.getUserId());
        assertEquals("user.login", log.getAction());
        assertEquals("alice", log.getResource());
        assertEquals("{\"role\":\"user\"}", log.getDetail());
        assertEquals("127.0.0.1", log.getIpAddress());
        assertEquals("JUnit", log.getUserAgent());
        assertEquals(1, log.getResult());
        assertEquals(now, log.getCreatedAt());
    }

    @Test
    void auditActionDefinitionGettersAndSetters() {
        AuditActionDefinition definition = new AuditActionDefinition();
        LocalDateTime now = LocalDateTime.of(2024, 6, 1, 8, 0);

        definition.setActionCode("user.login");
        definition.setLabelZh("用户登录");
        definition.setCategory("user");
        definition.setIsSystem(1);
        definition.setEnabled(1);
        definition.setSortOrder(10);
        definition.setCreatedAt(now);

        assertEquals("user.login", definition.getActionCode());
        assertEquals("用户登录", definition.getLabelZh());
        assertEquals("user", definition.getCategory());
        assertEquals(1, definition.getIsSystem());
        assertEquals(1, definition.getEnabled());
        assertEquals(10, definition.getSortOrder());
        assertEquals(now, definition.getCreatedAt());
    }

    @Test
    void auditArchivedLogGettersAndSetters() {
        AuditArchivedLog archived = new AuditArchivedLog();
        LocalDateTime createdAt = LocalDateTime.of(2024, 5, 1, 9, 0);
        LocalDateTime archivedAt = LocalDateTime.of(2024, 6, 1, 9, 0);

        archived.setArchiveId("arc_1");
        archived.setOriginalLogId("aud_1");
        archived.setUserId("u_1");
        archived.setAction("user.login");
        archived.setResource("alice");
        archived.setDetail("{\"role\":\"user\"}");
        archived.setIpAddress("127.0.0.1");
        archived.setUserAgent("JUnit");
        archived.setResult(1);
        archived.setCreatedAt(createdAt);
        archived.setArchivedAt(archivedAt);
        archived.setArchivedBy("adm_001");
        archived.setArchiveReason("admin_delete");

        assertEquals("arc_1", archived.getArchiveId());
        assertEquals("aud_1", archived.getOriginalLogId());
        assertEquals("u_1", archived.getUserId());
        assertEquals("user.login", archived.getAction());
        assertEquals("alice", archived.getResource());
        assertEquals("{\"role\":\"user\"}", archived.getDetail());
        assertEquals("127.0.0.1", archived.getIpAddress());
        assertEquals("JUnit", archived.getUserAgent());
        assertEquals(1, archived.getResult());
        assertEquals(createdAt, archived.getCreatedAt());
        assertEquals(archivedAt, archived.getArchivedAt());
        assertEquals("adm_001", archived.getArchivedBy());
        assertEquals("admin_delete", archived.getArchiveReason());
    }
}
