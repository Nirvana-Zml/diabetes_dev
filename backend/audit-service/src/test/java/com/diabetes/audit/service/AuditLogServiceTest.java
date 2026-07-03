package com.diabetes.audit.service;

import com.diabetes.audit.dto.CreateAuditLogRequest;
import com.diabetes.audit.entity.AuditActionDefinition;
import com.diabetes.audit.entity.AuditArchivedLog;
import com.diabetes.audit.entity.AuditLog;
import com.diabetes.audit.mapper.AuditActionDefinitionMapper;
import com.diabetes.audit.mapper.AuditArchivedLogMapper;
import com.diabetes.audit.mapper.AuditLogMapper;
import com.diabetes.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private AuditLogMapper auditLogMapper;
    private AuditActionDefinitionMapper auditActionDefinitionMapper;
    private AuditArchivedLogMapper auditArchivedLogMapper;
    private ObjectMapper objectMapper;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        auditActionDefinitionMapper = mock(AuditActionDefinitionMapper.class);
        auditArchivedLogMapper = mock(AuditArchivedLogMapper.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of());
        service = new AuditLogService(
                auditLogMapper, auditActionDefinitionMapper, auditArchivedLogMapper, objectMapper);
    }

    @Test
    void createPersistsAuditLogWithDetail() {
        CreateAuditLogRequest request = new CreateAuditLogRequest(
                " u_1 ", " user.login ", " alice ", Map.of("role", "user"), "127.0.0.1", "JUnit", 1);

        Map<String, Object> response = service.create(request);

        assertEquals("u_1", response.get("userId"));
        assertEquals("user.login", response.get("action"));
        assertNotNull(response.get("logId"));
        assertNotNull(response.get("createdAt"));
        assertEquals(Map.of("role", "user"), response.get("detail"));
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void createAcceptsFailureResult() {
        CreateAuditLogRequest request = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", Map.of(), null, null, 0);

        Map<String, Object> response = service.create(request);

        assertEquals(0, response.get("result"));
        assertNull(response.get("detail"));
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void createRejectsInvalidResult() {
        CreateAuditLogRequest request = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 2);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals(400, ex.getCode());
        verify(auditLogMapper, never()).insert(any());
    }

    @Test
    void createRejectsUnserializableDetail() throws Exception {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});
        AuditLogService brokenService = new AuditLogService(
                auditLogMapper, auditActionDefinitionMapper, auditArchivedLogMapper, brokenMapper);

        CreateAuditLogRequest request = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", Map.of("bad", "value"), null, null, 1);

        BusinessException ex = assertThrows(BusinessException.class, () -> brokenService.create(request));
        assertTrue(ex.getMessage().contains("detail"));
    }

    @Test
    void adminListReturnsPagedDataAndNormalizesFilters() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        when(auditLogMapper.findAdminList(eq("u_1"), eq("user.login"), anyList(), eq("kw"), eq(1),
                any(), any(), eq(0), eq(100)))
                .thenReturn(List.of(log));
        when(auditLogMapper.countAdminList(eq("u_1"), eq("user.login"), anyList(), eq("kw"), eq(1), any(), any()))
                .thenReturn(1);

        Map<String, Object> result = service.adminList(" u_1 ", " user.login ", List.of(), " kw ", 1,
                "2024-06-01", "2024-06-02", 0, 200);

        assertEquals(1, result.get("page"));
        assertEquals(100, result.get("size"));
        assertEquals(1, result.get("total"));
    }

    @Test
    void adminListParsesDateOnlyRange() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(auditLogMapper.countAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), any()))
                .thenReturn(0);

        Map<String, Object> result = service.adminList(null, null, List.of(), null, null,
                "2024-06-01", "2024-06-01", 1, 20);

        assertEquals(0, result.get("total"));
    }

    @Test
    void adminListWithOnlyStartTime() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(auditLogMapper.countAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), isNull()))
                .thenReturn(0);

        Map<String, Object> result = service.adminList(null, null, List.of(), null, null, "2024-06-01 08:00:00", null, 1, 20);

        assertEquals(0, result.get("total"));
    }

    @Test
    void adminListWithFullDateTimeRange() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(auditLogMapper.countAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), any()))
                .thenReturn(0);

        Map<String, Object> result = service.adminList(null, null, List.of(), null, null,
                "2024-06-01 08:00:00", "2024-06-01 18:00:00", 1, 20);

        assertEquals(0, result.get("total"));
    }

    @Test
    void adminListRejectsInvalidDateRange() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.adminList(null, null, List.of(), null, null, "2024-06-02", "2024-06-01", 1, 20));
        assertEquals(400, ex.getCode());
    }

    @Test
    void adminListRejectsInvalidDateFormat() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.adminList(null, null, List.of(), null, null, "bad-date", null, 1, 20));
        assertEquals(400, ex.getCode());
    }

    @Test
    void adminDetailReturnsExistingLogAndParsesDetail() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setDetail("{\"role\":\"user\"}");
        when(auditLogMapper.findById("aud_1")).thenReturn(log);

        Map<String, Object> detail = service.adminDetail("aud_1");

        assertEquals("aud_1", detail.get("logId"));
        assertEquals(Map.of("role", "user"), detail.get("detail"));
    }

    @Test
    void adminDetailReturnsRawDetailWhenJsonInvalid() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setDetail("{invalid");
        when(auditLogMapper.findById("aud_1")).thenReturn(log);

        Map<String, Object> detail = service.adminDetail("aud_1");

        assertEquals("{invalid", detail.get("detail"));
    }

    @Test
    void adminDetailHandlesNullCreatedAt() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setCreatedAt(null);
        when(auditLogMapper.findById("aud_1")).thenReturn(log);

        Map<String, Object> detail = service.adminDetail("aud_1");

        assertNull(detail.get("createdAt"));
    }

    @Test
    void adminDetailNotFound() {
        when(auditLogMapper.findById("missing")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.adminDetail("missing"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void deleteRecordsAuditBeforeRemovingTargetLog() {
        AuditLog target = sampleLog("aud_target", "u_1", "user.login");
        when(auditLogMapper.findById("aud_target")).thenReturn(target);

        service.delete("aud_target", "adm_001", "127.0.0.1", "JUnit");

        verify(auditArchivedLogMapper).insert(any(AuditArchivedLog.class));
        verify(auditLogMapper).insert(any(AuditLog.class));
        verify(auditLogMapper).deleteById("aud_target");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog deleteAudit = captor.getValue();
        assertEquals("audit.delete", deleteAudit.getAction());
        assertEquals("adm_001", deleteAudit.getUserId());
    }

    @Test
    void deleteSkipsAuditWhenAdminIdBlank() {
        AuditLog target = sampleLog("aud_target", "u_1", "user.login");
        when(auditLogMapper.findById("aud_target")).thenReturn(target);

        service.delete("aud_target", "  ", "127.0.0.1", "JUnit");

        verify(auditArchivedLogMapper, never()).insert(any());
        verify(auditLogMapper, never()).insert(any());
        verify(auditLogMapper).deleteById("aud_target");
    }

    @Test
    void batchDeleteRecordsAuditAndRemovesLogs() {
        AuditLog log1 = sampleLog("aud_1", "u_1", "user.login");
        AuditLog log2 = sampleLog("aud_2", "u_2", "user.logout");
        when(auditLogMapper.findByIds(List.of("aud_1", "aud_2"))).thenReturn(List.of(log1, log2));
        when(auditLogMapper.deleteByIds(List.of("aud_1", "aud_2"))).thenReturn(2);

        Map<String, Object> result = service.batchDelete(
                List.of(" aud_1 ", "aud_1", "aud_2"), "adm_001", "127.0.0.1", "JUnit");

        assertEquals(2, result.get("deleted"));
        verify(auditArchivedLogMapper).insertBatch(anyList());
        verify(auditLogMapper).insert(any(AuditLog.class));
        verify(auditLogMapper).deleteByIds(List.of("aud_1", "aud_2"));
    }

    @Test
    void batchDeleteSkipsAuditWhenAdminIdBlank() {
        when(auditLogMapper.findByIds(List.of("aud_1"))).thenReturn(List.of(sampleLog("aud_1", "u_1", "user.login")));
        when(auditLogMapper.deleteByIds(List.of("aud_1"))).thenReturn(1);

        service.batchDelete(List.of("aud_1"), null, null, null);

        verify(auditArchivedLogMapper, never()).insert(any());
        verify(auditArchivedLogMapper, never()).insertBatch(anyList());
        verify(auditLogMapper, never()).insert(any());
        verify(auditLogMapper).deleteByIds(List.of("aud_1"));
    }

    @Test
    void listActionsFallsBackToDefaultsWhenDefinitionsEmpty() {
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of());
        when(auditLogMapper.findDistinctActions()).thenReturn(null);

        List<String> actions = service.listActions();

        assertTrue(actions.contains("audit.delete"));
        assertTrue(actions.size() >= DEFAULT_ACTIONS.size());
    }

    @Test
    void listActionsUsesDatabaseDefinitions() {
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of(
                actionDefinition("user.login", "用户登录"),
                actionDefinition("article.create", "资讯创建")
        ));
        when(auditLogMapper.findDistinctActions()).thenReturn(List.of("custom.action"));

        List<String> actions = service.listActions();

        assertTrue(actions.contains("user.login"));
        assertTrue(actions.contains("article.create"));
        assertTrue(actions.contains("custom.action"));
        assertFalse(actions.contains("data.export"));
    }

    @Test
    void actionLabelUsesDatabaseDefinitionWhenPresent() {
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of(
                actionDefinition("user.login", "数据库标签")
        ));
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(sampleLog("aud_1", "u_1", "user.login")));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains("数据库标签"));
    }

    @Test
    void batchDeleteRejectsEmptyIds() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.batchDelete(List.of("  "), "adm_001", null, null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void listActionsMergesDefaultsAndDatabaseValues() {
        when(auditLogMapper.findDistinctActions()).thenReturn(List.of("custom.action", "user.login"));

        List<String> actions = service.listActions();

        assertTrue(actions.contains("user.login"));
        assertTrue(actions.contains("custom.action"));
    }

    @Test
    void listActionsHandlesNullDatabaseResult() {
        when(auditLogMapper.findDistinctActions()).thenReturn(null);

        List<String> actions = service.listActions();

        assertTrue(actions.contains("audit.delete"));
        assertTrue(actions.contains("user.login"));
    }

    @Test
    void adminOverviewAggregatesStatsAndFillsTrend() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);
        LocalDateTime since = today.minusDays(6).atStartOfDay();

        when(auditLogMapper.countBetween(todayStart, todayEnd, null)).thenReturn(12);
        when(auditLogMapper.countBetween(todayStart, todayEnd, 0)).thenReturn(2);
        when(auditLogMapper.countGroupByAction(since, 10)).thenReturn(List.of(
                Map.of("action", "user.login", "count", 5),
                Map.of("action", "admin.login", "count", 3)
        ));
        when(auditLogMapper.loginFailureTrend(since)).thenReturn(List.of(
                Map.of("date", today.toString(), "count", 2)
        ));
        when(auditLogMapper.topSubjects(since, "u_", 5)).thenReturn(List.of(
                Map.of("user_id", "u_1", "count", 4)
        ));
        when(auditLogMapper.topSubjects(since, "adm_", 5)).thenReturn(List.of(
                Map.of("user_id", "adm_001", "count", 6)
        ));

        Map<String, Object> overview = service.adminOverview(7);

        assertEquals(12, ((Map<?, ?>) overview.get("today")).get("total"));
        assertEquals(2, ((Map<?, ?>) overview.get("today")).get("failed"));
        assertEquals(10, ((Map<?, ?>) overview.get("today")).get("success"));
        assertEquals(7, ((List<?>) overview.get("login_failure_trend")).size());
        assertEquals(2, ((List<?>) overview.get("action_distribution")).size());
        assertEquals("用户登录",
                ((Map<?, ?>) ((List<?>) overview.get("action_distribution")).get(0)).get("label"));
    }

    @Test
    void exportCsvAcceptsValidDateRange() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), any(), eq(0), eq(10000)))
                .thenReturn(List.of());

        String csv = service.exportCsv(null, null, List.of(), null, null, "2024-06-01", "2024-06-02");

        assertTrue(csv.contains("日志ID"));
    }

    @Test
    void exportCsvSupportsEndTimeOnlyFilter() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), any(), eq(0), eq(10000)))
                .thenReturn(List.of());

        String csv = service.exportCsv(null, null, List.of(), null, null, null, "2024-06-01");

        assertTrue(csv.startsWith("\uFEFF"));
    }

    @Test
    void actionLabelIgnoresDefinitionWithBlankActionCode() {
        AuditActionDefinition invalid = new AuditActionDefinition();
        invalid.setActionCode("  ");
        invalid.setLabelZh("无效标签");
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of(invalid));
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(sampleLog("aud_1", "u_1", "user.login")));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains("用户登录"));
    }

    @Test
    void exportCsvEscapesQuoteOnlyCells() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setResource("text\"only");
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(log));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains("\"text\"\"only\""));
    }

    @Test
    void exportCsvHandlesNullResultAndPartialDateRange() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setResult(null);
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), any(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(log));

        String csv = service.exportCsv(null, null, null, null, null, "2024-06-01 08:00:00", null);

        assertTrue(csv.contains("失败"));
    }

    @Test
    void exportCsvEscapesCarriageReturnOnlyCells() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setDetail("a\rb");
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(log));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains("\"a\rb\""));
    }

    @Test
    void batchDeleteSkipsArchiveWhenMapperReturnsNull() {
        when(auditLogMapper.findByIds(List.of("aud_1"))).thenReturn(null);
        when(auditLogMapper.deleteByIds(List.of("aud_1"))).thenReturn(1);

        service.batchDelete(List.of("aud_1"), "adm_001", null, null);

        verify(auditArchivedLogMapper, never()).insert(any());
        verify(auditArchivedLogMapper, never()).insertBatch(anyList());
    }

    @Test
    void actionLabelUsesFallbackWhenDefinitionsNull() {
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(null);
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(sampleLog("aud_1", "u_1", "user.login")));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains("用户登录"));
    }

    @Test
    void adminOverviewHandlesEmptyDistributionLists() {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(6).atStartOfDay();

        when(auditLogMapper.countBetween(any(), any(), isNull())).thenReturn(0);
        when(auditLogMapper.countBetween(any(), any(), eq(0))).thenReturn(0);
        when(auditLogMapper.countGroupByAction(since, 10)).thenReturn(List.of());
        when(auditLogMapper.loginFailureTrend(since)).thenReturn(List.of());
        when(auditLogMapper.topSubjects(since, "u_", 5)).thenReturn(null);
        when(auditLogMapper.topSubjects(since, "adm_", 5)).thenReturn(null);

        Map<String, Object> overview = service.adminOverview(7);

        assertTrue(((List<?>) overview.get("action_distribution")).isEmpty());
        assertTrue(((List<?>) overview.get("top_users")).isEmpty());
        assertTrue(((List<?>) overview.get("top_admins")).isEmpty());
    }

    @Test
    void createUsesNullDetailJson() {
        CreateAuditLogRequest request = new CreateAuditLogRequest(
                "u_1", "user.login", "alice", null, null, null, 1);

        Map<String, Object> response = service.create(request);

        assertNull(response.get("detail"));
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getDetail());
    }

    @Test
    void exportCsvRejectsInvalidDateRange() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.exportCsv(null, null, List.of(), null, null, "2024-06-02", "2024-06-01"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void exportCsvUsesActionsFilterAndEscapesSpecialCharacters() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        log.setResult(0);
        log.setCreatedAt(null);
        log.setDetail("line1\nline2");
        log.setResource("res,with\"quote");
        when(auditLogMapper.findAdminList(isNull(), isNull(), eq(List.of("user.login", "audit.delete")),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(log));

        String csv = service.exportCsv(null, null, List.of(" user.login ", "", "audit.delete"),
                null, null, null, null);

        assertTrue(csv.contains("失败"));
        assertTrue(csv.contains("\"res,with\"\"quote\""));
        assertTrue(csv.contains("\"line1\nline2\""));
    }

    @Test
    void adminListUsesActionsFilterWhenProvided() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), eq(List.of("audit.delete")),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(auditLogMapper.countAdminList(isNull(), isNull(), eq(List.of("audit.delete")),
                isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0);

        Map<String, Object> result = service.adminList(null, "ignored", List.of("audit.delete"),
                null, null, null, null, 1, 20);

        assertEquals(0, result.get("total"));
    }

    @Test
    void createExportAuditPersistsWhenAdminPresent() {
        service.createExportAudit(" adm_001 ", "127.0.0.1", "JUnit");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertEquals("audit.export", captor.getValue().getAction());
        assertEquals("adm_001", captor.getValue().getUserId());
    }

    @Test
    void createExportAuditSkipsWhenAdminBlank() {
        service.createExportAudit("  ", "127.0.0.1", "JUnit");
        verify(auditLogMapper, never()).insert(any());
    }

    @Test
    void deleteNotFound() {
        when(auditLogMapper.findById("missing")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.delete("missing", "adm_001", "127.0.0.1", "JUnit"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void archiveLogsSkipsWhenNoLogsFound() {
        when(auditLogMapper.findByIds(List.of("aud_2"))).thenReturn(List.of());
        when(auditLogMapper.deleteByIds(List.of("aud_2"))).thenReturn(0);

        service.batchDelete(List.of("aud_2"), "adm_001", null, null);

        verify(auditArchivedLogMapper, never()).insert(any());
        verify(auditArchivedLogMapper, never()).insertBatch(anyList());
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void listActionsIgnoresBlankDefinitionCodes() {
        AuditActionDefinition blank = new AuditActionDefinition();
        blank.setActionCode("  ");
        blank.setLabelZh("忽略");
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of(
                actionDefinition("user.login", "用户登录"),
                blank
        ));
        when(auditLogMapper.findDistinctActions()).thenReturn(List.of());

        List<String> actions = service.listActions();

        assertEquals(1, actions.size());
        assertEquals("user.login", actions.get(0));
    }

    @Test
    void listActionsHandlesNullDefinitions() {
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(null);
        when(auditLogMapper.findDistinctActions()).thenReturn(List.of("custom.action"));

        List<String> actions = service.listActions();

        assertTrue(actions.contains("user.login"));
        assertTrue(actions.contains("custom.action"));
    }

    @Test
    void actionLabelSkipsBlankDefinitionFields() {
        AuditActionDefinition missingLabel = new AuditActionDefinition();
        missingLabel.setActionCode("custom.action");
        missingLabel.setLabelZh("  ");
        when(auditActionDefinitionMapper.findAllEnabled()).thenReturn(List.of(
                actionDefinition("user.login", "用户登录"),
                missingLabel
        ));
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(sampleLog("aud_1", "u_1", "custom.action")));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains("custom.action"));
    }

    @Test
    void actionLabelReturnsEmptyForBlankAction() {
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(sampleLog("aud_1", "u_1", " ")));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.contains(",,") || csv.contains(",resource,"));
    }

    @Test
    void adminOverviewClampsDaysAndHandlesEmptyStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(29).atStartOfDay();

        when(auditLogMapper.countBetween(any(), any(), isNull())).thenReturn(0);
        when(auditLogMapper.countBetween(any(), any(), eq(0))).thenReturn(0);
        when(auditLogMapper.countGroupByAction(since, 10)).thenReturn(null);
        when(auditLogMapper.loginFailureTrend(since)).thenReturn(null);
        when(auditLogMapper.topSubjects(since, "u_", 5)).thenReturn(List.of());
        when(auditLogMapper.topSubjects(since, "adm_", 5)).thenReturn(List.of());

        Map<String, Object> overview = service.adminOverview(40);

        assertEquals(30, overview.get("trend_days"));
        assertEquals(30, ((List<?>) overview.get("login_failure_trend")).size());
        assertTrue(((List<?>) overview.get("action_distribution")).isEmpty());
        assertTrue(((List<?>) overview.get("top_users")).isEmpty());
    }

    @Test
    void adminOverviewUsesAlternateMapKeysAndStringCounts() {
        LocalDate today = LocalDate.now();
        LocalDateTime since = today.minusDays(6).atStartOfDay();

        when(auditLogMapper.countBetween(any(), any(), isNull())).thenReturn(1);
        when(auditLogMapper.countBetween(any(), any(), eq(0))).thenReturn(0);
        when(auditLogMapper.countGroupByAction(since, 10)).thenReturn(List.of(
                Map.of("ACTION", "admin.login", "COUNT", "bad")
        ));
        when(auditLogMapper.loginFailureTrend(since)).thenReturn(List.of(
                Map.of("DATE", "", "COUNT", 1),
                Map.of("DATE", today.toString(), "COUNT", "3")
        ));
        when(auditLogMapper.topSubjects(since, "u_", 5)).thenReturn(List.of(
                Map.of("USER_ID", "u_9", "COUNT", 2)
        ));
        when(auditLogMapper.topSubjects(since, "adm_", 5)).thenReturn(List.of(
                Map.of("userId", "adm_9", "count", 5)
        ));

        Map<String, Object> overview = service.adminOverview(5);

        assertEquals(7, overview.get("trend_days"));
        assertEquals("u_9", ((Map<?, ?>) ((List<?>) overview.get("top_users")).get(0)).get("user_id"));
        assertEquals(3, ((Map<?, ?>) ((List<?>) overview.get("login_failure_trend")).get(6)).get("count"));
    }

    @Test
    void exportCsvIncludesHeaderAndLabel() {
        AuditLog log = sampleLog("aud_1", "u_1", "user.login");
        when(auditLogMapper.findAdminList(isNull(), isNull(), anyList(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of(log));

        String csv = service.exportCsv(null, null, List.of(), null, null, null, null);

        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("user.login"));
        assertTrue(csv.contains("用户登录"));
    }

    private static final List<String> DEFAULT_ACTIONS = List.of(
            "user.login", "user.logout", "user.register", "user.password.change", "user.password.reset",
            "admin.login", "admin.logout", "admin.password.change", "data.export",
            "article.create", "article.update", "article.delete", "article.cover.upload",
            "article.publish", "article.review", "video.create", "video.update", "video.delete",
            "video.cover.upload", "video.file.upload", "audit.delete", "audit.export");

    private static AuditActionDefinition actionDefinition(String code, String label) {
        AuditActionDefinition definition = new AuditActionDefinition();
        definition.setActionCode(code);
        definition.setLabelZh(label);
        definition.setEnabled(1);
        return definition;
    }

    private AuditLog sampleLog(String logId, String userId, String action) {
        AuditLog log = new AuditLog();
        log.setLogId(logId);
        log.setUserId(userId);
        log.setAction(action);
        log.setResource("resource");
        log.setResult(1);
        log.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        return log;
    }
}
