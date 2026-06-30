package com.diabetes.user.service;

import com.diabetes.common.client.CheckinServiceClient;
import com.diabetes.common.client.ConsultationServiceClient;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.client.PlanServiceClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.user.dto.ExportDataRequest;
import com.diabetes.user.dto.ExportTaskResponse;
import com.diabetes.user.dto.UserProfileResponse;
import com.diabetes.user.export.ExportFileGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int EXPORT_LIST_SIZE = 100;

    private final UserProfileService userProfileService;
    private final HealthServiceClient healthServiceClient;
    private final CheckinServiceClient checkinServiceClient;
    private final PlanServiceClient planServiceClient;
    private final ConsultationServiceClient consultationServiceClient;
    private final ExportFileGenerator exportFileGenerator;
    private final MinioStorageService minioStorageService;
    private final String difyInternalKey;
    private final Map<String, ExportTaskRecord> tasks = new ConcurrentHashMap<>();

    public DataExportService(UserProfileService userProfileService,
                             HealthServiceClient healthServiceClient,
                             CheckinServiceClient checkinServiceClient,
                             PlanServiceClient planServiceClient,
                             ConsultationServiceClient consultationServiceClient,
                             ExportFileGenerator exportFileGenerator,
                             MinioStorageService minioStorageService,
                             @Value("${dify-internal.key:${dify.internal-key:}}") String difyInternalKey) {
        this.userProfileService = userProfileService;
        this.healthServiceClient = healthServiceClient;
        this.checkinServiceClient = checkinServiceClient;
        this.planServiceClient = planServiceClient;
        this.consultationServiceClient = consultationServiceClient;
        this.exportFileGenerator = exportFileGenerator;
        this.minioStorageService = minioStorageService;
        this.difyInternalKey = difyInternalKey;
    }

    public ExportTaskResponse submitExport(String userId, ExportDataRequest request) {
        String format = request.format() == null || request.format().isBlank() ? "excel" : request.format().trim();
        String taskId = "export_" + System.currentTimeMillis();
        Map<String, Object> payload = buildPayload(userId, request);
        byte[] fileBytes = exportFileGenerator.generate(format, payload);
        String ext = exportFileGenerator.fileExtension(format);
        String fileName = "health_export_" + LocalDateTime.now().format(FILE_TS) + "." + ext;
        String downloadUrl = minioStorageService.uploadExportFile(
                userId,
                fileName,
                new ByteArrayInputStream(fileBytes),
                fileBytes.length,
                exportFileGenerator.contentType(format));

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        ExportTaskRecord record = new ExportTaskRecord(
                taskId, userId, "completed", downloadUrl, fileName, expiresAt);
        tasks.put(taskId, record);

        return new ExportTaskResponse(
                taskId,
                "completed",
                "导出成功，请点击下载",
                downloadUrl,
                fileName,
                expiresAt.toString());
    }

    public ExportTaskResponse getTask(String userId, String taskId) {
        ExportTaskRecord record = tasks.get(taskId);
        if (record == null || !record.userId().equals(userId)) {
            throw new BusinessException(404, "导出任务不存在");
        }
        if (record.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(410, "导出文件已过期，请重新导出");
        }
        return new ExportTaskResponse(
                record.taskId(),
                record.status(),
                "completed".equals(record.status()) ? "导出已完成" : "处理中",
                record.downloadUrl(),
                record.fileName(),
                record.expiresAt().toString());
    }

    private Map<String, Object> buildPayload(String userId, ExportDataRequest request) {
        Set<String> types = new LinkedHashSet<>(request.types());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exported_at", LocalDateTime.now().toString());
        payload.put("start_date", request.start_date());
        payload.put("end_date", request.end_date());

        UserProfileResponse profile = userProfileService.getProfile(userId);
        payload.put("profile", profileToMap(profile));

        if (types.contains("health")) {
            payload.put("health", healthServiceClient.getLatestHealthProfile(userId, difyInternalKey));
        }
        if (types.contains("risk")) {
            payload.put("risk", healthServiceClient.getRiskHistory(userId, difyInternalKey, 1, EXPORT_LIST_SIZE));
        }
        if (types.contains("consultation")) {
            Map<String, Object> sessions = consultationServiceClient.listSessions(
                    userId, difyInternalKey, 1, EXPORT_LIST_SIZE);
            payload.put("consultation", filterByDateRange(sessions, "sessions", "list",
                    "startedAt", "started_at", request.start_date(), request.end_date()));
        }
        if (types.contains("plan")) {
            Map<String, Object> plans = planServiceClient.getPlanHistory(userId, difyInternalKey, 1, EXPORT_LIST_SIZE);
            payload.put("plan", filterByDateRange(plans, "plans",
                    "generatedAt", "generated_at", request.start_date(), request.end_date()));
        }
        if (types.contains("checkin")) {
            int days = resolveCheckinDays(request.start_date(), request.end_date());
            List<Map<String, Object>> records = checkinServiceClient.getRecentCheckins(userId, difyInternalKey, days);
            payload.put("checkin", Map.of("records", filterCheckins(records, request.start_date(), request.end_date())));
        }
        return payload;
    }

    private Map<String, Object> profileToMap(UserProfileResponse profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", profile.user_id());
        map.put("username", profile.username());
        map.put("nickname", profile.nickname());
        map.put("phone", profile.phone());
        map.put("email", profile.email());
        map.put("points", profile.points());
        map.put("gender", profile.gender());
        map.put("birthDate", profile.birth_date());
        return map;
    }

    private int resolveCheckinDays(String startDate, String endDate) {
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            try {
                LocalDate start = LocalDate.parse(startDate);
                LocalDate end = LocalDate.parse(endDate);
                return (int) Math.min(Math.max(ChronoUnit.DAYS.between(start, end) + 1, 1), 365);
            } catch (Exception ignored) {
                return 90;
            }
        }
        return 90;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterCheckins(List<Map<String, Object>> records,
                                                      String startDate, String endDate) {
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return records;
        }
        return records.stream()
                .filter(item -> withinDateRange(stringField(item, "checkinDate", "checkin_date"), startDate, endDate))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> filterByDateRange(Map<String, Object> source,
                                                  String primaryKey,
                                                  String secondaryKey,
                                                  String dateFieldCamel,
                                                  String dateFieldSnake,
                                                  String startDate,
                                                  String endDate) {
        if (source == null || source.isEmpty()) {
            return Map.of(primaryKey, List.of(), "total", 0);
        }
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return source;
        }
        Object listObj = source.get(primaryKey);
        if (listObj == null && secondaryKey != null) {
            listObj = source.get(secondaryKey);
        }
        if (!(listObj instanceof List<?> list)) {
            return source;
        }
        List<Map<String, Object>> filtered = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> withinDateRange(stringField(item, dateFieldCamel, dateFieldSnake), startDate, endDate))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put(primaryKey, filtered);
        if (secondaryKey != null) {
            result.put(secondaryKey, filtered);
        }
        result.put("total", filtered.size());
        return result;
    }

    private Map<String, Object> filterByDateRange(Map<String, Object> source,
                                                  String listKey,
                                                  String dateFieldCamel,
                                                  String dateFieldSnake,
                                                  String startDate,
                                                  String endDate) {
        return filterByDateRange(source, listKey, null, dateFieldCamel, dateFieldSnake, startDate, endDate);
    }

    private boolean withinDateRange(String value, String startDate, String endDate) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String datePart = value.length() >= 10 ? value.substring(0, 10) : value;
        return datePart.compareTo(startDate) >= 0 && datePart.compareTo(endDate) <= 0;
    }

    private String stringField(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private record ExportTaskRecord(
            String taskId,
            String userId,
            String status,
            String downloadUrl,
            String fileName,
            LocalDateTime expiresAt
    ) {}
}
