package com.diabetes.audit.service;

import com.diabetes.audit.dto.CreateAuditLogRequest;
import com.diabetes.audit.entity.AuditActionDefinition;
import com.diabetes.audit.entity.AuditArchivedLog;
import com.diabetes.audit.entity.AuditLog;
import com.diabetes.audit.mapper.AuditActionDefinitionMapper;
import com.diabetes.audit.mapper.AuditArchivedLogMapper;
import com.diabetes.audit.mapper.AuditLogMapper;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final List<String> DEFAULT_ACTIONS = List.of(
            "user.login",
            "user.logout",
            "user.register",
            "user.password.change",
            "user.password.reset",
            "admin.login",
            "admin.logout",
            "admin.password.change",
            "data.export",
            "article.create",
            "article.update",
            "article.delete",
            "article.cover.upload",
            "article.publish",
            "article.review",
            "video.create",
            "video.update",
            "video.delete",
            "video.cover.upload",
            "video.file.upload",
            "audit.delete",
            "audit.export"
    );

    private static final int EXPORT_MAX_ROWS = 10_000;

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("user.login", "用户登录"),
            Map.entry("user.logout", "用户登出"),
            Map.entry("user.register", "用户注册"),
            Map.entry("user.password.change", "用户修改密码"),
            Map.entry("user.password.reset", "用户重置密码"),
            Map.entry("admin.login", "管理员登录"),
            Map.entry("admin.logout", "管理员登出"),
            Map.entry("admin.password.change", "管理员修改密码"),
            Map.entry("data.export", "数据导出"),
            Map.entry("article.create", "资讯创建"),
            Map.entry("article.update", "资讯编辑"),
            Map.entry("article.delete", "资讯删除"),
            Map.entry("article.cover.upload", "资讯封面上传"),
            Map.entry("article.publish", "资讯发布"),
            Map.entry("article.review", "资讯审核"),
            Map.entry("video.create", "视频创建"),
            Map.entry("video.update", "视频编辑"),
            Map.entry("video.delete", "视频删除"),
            Map.entry("video.cover.upload", "视频封面上传"),
            Map.entry("video.file.upload", "视频文件上传"),
            Map.entry("audit.delete", "审计日志删除"),
            Map.entry("audit.export", "审计日志导出")
    );

    private final AuditLogMapper auditLogMapper;
    private final AuditActionDefinitionMapper auditActionDefinitionMapper;
    private final AuditArchivedLogMapper auditArchivedLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper auditLogMapper,
                           AuditActionDefinitionMapper auditActionDefinitionMapper,
                           AuditArchivedLogMapper auditArchivedLogMapper,
                           ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.auditActionDefinitionMapper = auditActionDefinitionMapper;
        this.auditArchivedLogMapper = auditArchivedLogMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> create(CreateAuditLogRequest request) {
        if (request.result() != 0 && request.result() != 1) {
            throw new BusinessException(400, "result 仅支持 0（失败）或 1（成功）");
        }
        AuditLog log = buildLog(request);
        auditLogMapper.insert(log);
        return toResponse(log);
    }

    public Map<String, Object> adminList(String userId,
                                         String action,
                                         List<String> actions,
                                         String keyword,
                                         Integer result,
                                         String startTime,
                                         String endTime,
                                         int page,
                                         int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        LocalDateTime start = parseDateTime(startTime, true);
        LocalDateTime end = parseDateTime(endTime, false);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(400, "开始时间不能晚于结束时间");
        }
        List<String> normalizedActions = normalizeActions(actions);
        String normalizedAction = normalizedActions.isEmpty() ? blankToNull(action) : null;
        List<AuditLog> logs = auditLogMapper.findAdminList(
                blankToNull(userId),
                normalizedAction,
                normalizedActions,
                blankToNull(keyword),
                result,
                start,
                end,
                offset,
                safeSize
        );
        return Map.of(
                "logs", logs.stream().map(this::toResponse).toList(),
                "total", auditLogMapper.countAdminList(
                        blankToNull(userId),
                        normalizedAction,
                        normalizedActions,
                        blankToNull(keyword),
                        result,
                        start,
                        end
                ),
                "page", safePage,
                "size", safeSize
        );
    }

    public String exportCsv(String userId,
                            String action,
                            List<String> actions,
                            String keyword,
                            Integer result,
                            String startTime,
                            String endTime) {
        LocalDateTime start = parseDateTime(startTime, true);
        LocalDateTime end = parseDateTime(endTime, false);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(400, "开始时间不能晚于结束时间");
        }
        List<String> normalizedActions = normalizeActions(actions);
        String normalizedAction = normalizedActions.isEmpty() ? blankToNull(action) : null;
        List<AuditLog> logs = auditLogMapper.findAdminList(
                blankToNull(userId),
                normalizedAction,
                normalizedActions,
                blankToNull(keyword),
                result,
                start,
                end,
                0,
                EXPORT_MAX_ROWS
        );
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("日志ID,用户ID,操作类型,操作类型(中文),操作资源,结果,IP,操作时间,详情\n");
        for (AuditLog log : logs) {
            csv.append(csvCell(log.getLogId())).append(',')
                    .append(csvCell(log.getUserId())).append(',')
                    .append(csvCell(log.getAction())).append(',')
                    .append(csvCell(actionLabel(log.getAction()))).append(',')
                    .append(csvCell(log.getResource())).append(',')
                    .append(csvCell(log.getResult() != null && log.getResult() == 1 ? "成功" : "失败")).append(',')
                    .append(csvCell(log.getIpAddress())).append(',')
                    .append(csvCell(log.getCreatedAt() == null ? null : log.getCreatedAt().format(DATE_TIME))).append(',')
                    .append(csvCell(log.getDetail())).append('\n');
        }
        return csv.toString();
    }

    public Map<String, Object> adminDetail(String logId) {
        AuditLog log = requireLog(logId);
        return toResponse(log);
    }

    @Transactional
    public void delete(String logId, String adminId, String ipAddress, String userAgent) {
        AuditLog log = requireLog(logId);
        archiveLogs(List.of(log), adminId, "admin_delete");
        recordDeleteAudit(adminId, List.of(logId), ipAddress, userAgent, log.getAction());
        auditLogMapper.deleteById(logId);
    }

    @Transactional
    public Map<String, Object> batchDelete(List<String> logIds, String adminId, String ipAddress, String userAgent) {
        List<String> ids = logIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new BusinessException(400, "logIds 不能为空");
        }
        List<AuditLog> logs = auditLogMapper.findByIds(ids);
        archiveLogs(logs, adminId, "admin_batch_delete");
        recordDeleteAudit(adminId, ids, ipAddress, userAgent, null);
        int deleted = auditLogMapper.deleteByIds(ids);
        return Map.of("deleted", deleted);
    }

    public List<String> listActions() {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        List<AuditActionDefinition> definitions = auditActionDefinitionMapper.findAllEnabled();
        if (definitions != null) {
            for (AuditActionDefinition definition : definitions) {
                if (StringUtils.hasText(definition.getActionCode())) {
                    merged.add(definition.getActionCode().trim());
                }
            }
        }
        if (merged.isEmpty()) {
            merged.addAll(DEFAULT_ACTIONS);
        }
        List<String> fromDb = auditLogMapper.findDistinctActions();
        if (fromDb != null) {
            merged.addAll(fromDb);
        }
        return List.copyOf(merged);
    }

    @Transactional
    public void createExportAudit(String adminId, String ipAddress, String userAgent) {
        if (!StringUtils.hasText(adminId)) {
            return;
        }
        CreateAuditLogRequest request = new CreateAuditLogRequest(
                adminId.trim(),
                "audit.export",
                "audit_logs",
                Map.of("format", "csv"),
                ipAddress,
                userAgent,
                1
        );
        auditLogMapper.insert(buildLog(request));
    }

    public Map<String, Object> adminOverview(int trendDays) {
        int safeDays = Math.min(Math.max(trendDays, 7), 30);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);
        LocalDateTime since = today.minusDays(safeDays - 1L).atStartOfDay();

        int todayTotal = auditLogMapper.countBetween(todayStart, todayEnd, null);
        int todayFailed = auditLogMapper.countBetween(todayStart, todayEnd, 0);

        Map<String, Object> overview = new LinkedHashMap<>();
        Map<String, Object> todayStats = new LinkedHashMap<>();
        todayStats.put("total", todayTotal);
        todayStats.put("failed", todayFailed);
        todayStats.put("success", Math.max(todayTotal - todayFailed, 0));
        overview.put("today", todayStats);
        overview.put("trend_days", safeDays);
        overview.put("action_distribution", normalizeActionDistribution(
                auditLogMapper.countGroupByAction(since, 10)));
        overview.put("login_failure_trend", fillLoginFailureTrend(
                auditLogMapper.loginFailureTrend(since), since, today));
        overview.put("top_users", normalizeTopSubjects(auditLogMapper.topSubjects(since, "u_", 5)));
        overview.put("top_admins", normalizeTopSubjects(auditLogMapper.topSubjects(since, "adm_", 5)));
        return overview;
    }

    private void archiveLogs(List<AuditLog> logs, String adminId, String reason) {
        if (logs == null || logs.isEmpty() || !StringUtils.hasText(adminId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String archivedBy = adminId.trim();
        List<AuditArchivedLog> archivedLogs = logs.stream()
                .map(log -> toArchivedLog(log, archivedBy, reason, now))
                .toList();
        if (archivedLogs.size() == 1) {
            auditArchivedLogMapper.insert(archivedLogs.get(0));
        } else {
            auditArchivedLogMapper.insertBatch(archivedLogs);
        }
    }

    private AuditArchivedLog toArchivedLog(AuditLog log, String archivedBy, String reason, LocalDateTime archivedAt) {
        AuditArchivedLog archived = new AuditArchivedLog();
        archived.setArchiveId(IdGenerator.nextId("arc_"));
        archived.setOriginalLogId(log.getLogId());
        archived.setUserId(log.getUserId());
        archived.setAction(log.getAction());
        archived.setResource(log.getResource());
        archived.setDetail(log.getDetail());
        archived.setIpAddress(log.getIpAddress());
        archived.setUserAgent(log.getUserAgent());
        archived.setResult(log.getResult());
        archived.setCreatedAt(log.getCreatedAt());
        archived.setArchivedAt(archivedAt);
        archived.setArchivedBy(archivedBy);
        archived.setArchiveReason(reason);
        return archived;
    }

    private Map<String, String> loadActionLabels() {
        List<AuditActionDefinition> definitions = auditActionDefinitionMapper.findAllEnabled();
        if (definitions == null || definitions.isEmpty()) {
            return ACTION_LABELS;
        }
        Map<String, String> labels = new LinkedHashMap<>(ACTION_LABELS);
        for (AuditActionDefinition definition : definitions) {
            if (StringUtils.hasText(definition.getActionCode()) && StringUtils.hasText(definition.getLabelZh())) {
                labels.put(definition.getActionCode().trim(), definition.getLabelZh().trim());
            }
        }
        return labels;
    }

    private void recordDeleteAudit(String adminId,
                                   List<String> logIds,
                                   String ipAddress,
                                   String userAgent,
                                   String sampleAction) {
        if (!StringUtils.hasText(adminId)) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("deletedLogIds", logIds);
        detail.put("count", logIds.size());
        if (StringUtils.hasText(sampleAction)) {
            detail.put("sampleAction", sampleAction);
        }
        CreateAuditLogRequest request = new CreateAuditLogRequest(
                adminId.trim(),
                "audit.delete",
                "audit_logs",
                detail,
                ipAddress,
                userAgent,
                1
        );
        AuditLog log = buildLog(request);
        auditLogMapper.insert(log);
    }

    private AuditLog buildLog(CreateAuditLogRequest request) {
        AuditLog log = new AuditLog();
        log.setLogId(IdGenerator.nextId("aud_"));
        log.setUserId(request.userId().trim());
        log.setAction(request.action().trim());
        log.setResource(request.resource().trim());
        log.setDetail(toJson(request.detail()));
        log.setIpAddress(blankToNull(request.ipAddress()));
        log.setUserAgent(blankToNull(request.userAgent()));
        log.setResult(request.result());
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private AuditLog requireLog(String logId) {
        AuditLog log = auditLogMapper.findById(logId);
        if (log == null) {
            throw new BusinessException(404, "审计日志不存在");
        }
        return log;
    }

    private Map<String, Object> toResponse(AuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logId", log.getLogId());
        map.put("userId", log.getUserId());
        map.put("action", log.getAction());
        map.put("resource", log.getResource());
        map.put("detail", parseDetail(log.getDetail()));
        map.put("ipAddress", log.getIpAddress());
        map.put("userAgent", log.getUserAgent());
        map.put("result", log.getResult());
        map.put("createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().format(ISO_DATE_TIME));
        return map;
    }

    private Object parseDetail(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        try {
            return objectMapper.readValue(detail, Object.class);
        } catch (Exception e) {
            return detail;
        }
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "detail 必须是可序列化的 JSON 对象");
        }
    }

    private LocalDateTime parseDateTime(String value, boolean startOfDayIfDateOnly) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDateTime date = LocalDateTime.parse(trimmed + " 00:00:00", DATE_TIME);
                return startOfDayIfDateOnly ? date : date.withHour(23).withMinute(59).withSecond(59);
            }
            return LocalDateTime.parse(trimmed, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeActions(List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        return actions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String actionLabel(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        return loadActionLabels().getOrDefault(action.trim(), action.trim());
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private List<Map<String, Object>> normalizeActionDistribution(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(row -> {
            String action = mapString(row, "action", "ACTION");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", action);
            item.put("label", actionLabel(action));
            item.put("count", mapInt(row, "count", "COUNT"));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> normalizeTopSubjects(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("user_id", mapString(row, "user_id", "USER_ID", "userId"));
            item.put("count", mapInt(row, "count", "COUNT"));
            return item;
        }).toList();
    }

    private List<Map<String, Object>> fillLoginFailureTrend(List<Map<String, Object>> rows,
                                                            LocalDateTime since,
                                                            LocalDate today) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String date = mapString(row, "date", "DATE");
                if (StringUtils.hasText(date)) {
                    counts.put(date, mapInt(row, "count", "COUNT"));
                }
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate start = since.toLocalDate();
        for (LocalDate cursor = start; !cursor.isAfter(today); cursor = cursor.plusDays(1)) {
            String key = cursor.toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("count", counts.getOrDefault(key, 0));
            trend.add(item);
        }
        return trend;
    }

    private String mapString(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private int mapInt(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.parseInt(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    // try next key
                }
            }
        }
        return 0;
    }
}
