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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private HealthServiceClient healthServiceClient;
    @Mock
    private CheckinServiceClient checkinServiceClient;
    @Mock
    private PlanServiceClient planServiceClient;
    @Mock
    private ConsultationServiceClient consultationServiceClient;
    @Mock
    private ExportFileGenerator exportFileGenerator;
    @Mock
    private MinioStorageService minioStorageService;

    private DataExportService dataExportService;

    @BeforeEach
    void setUp() {
        dataExportService = new DataExportService(
                userProfileService, healthServiceClient, checkinServiceClient,
                planServiceClient, consultationServiceClient,
                exportFileGenerator, minioStorageService, "internal-key");
    }

    @Test
    void submitExport_excelDefault() {
        stubProfile();
        when(exportFileGenerator.generate(eq("excel"), any())).thenReturn(new byte[]{1, 2});
        when(exportFileGenerator.fileExtension("excel")).thenReturn("xlsx");
        when(exportFileGenerator.contentType("excel")).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/export/file.xlsx");

        ExportDataRequest request = new ExportDataRequest(List.of("health"), null, null, null);
        ExportTaskResponse response = dataExportService.submitExport("u_1", request);

        assertEquals("completed", response.status());
        assertEquals("http://minio/export/file.xlsx", response.download_url());
        assertNotNull(response.task_id());
    }

    @Test
    void submitExport_pdfAllTypes() {
        stubProfile();
        when(healthServiceClient.getLatestHealthProfile("u_1", "internal-key")).thenReturn(Map.of("bmi", 22));
        when(healthServiceClient.getRiskHistory(eq("u_1"), eq("internal-key"), eq(1), anyInt()))
                .thenReturn(Map.of("records", List.of(Map.of("assessedAt", "2024-06-01"))));
        when(consultationServiceClient.listSessions(eq("u_1"), eq("internal-key"), eq(1), anyInt()))
                .thenReturn(Map.of("sessions", List.of(Map.of("startedAt", "2024-06-02"))));
        when(planServiceClient.getPlanHistory(eq("u_1"), eq("internal-key"), eq(1), anyInt()))
                .thenReturn(Map.of("plans", List.of(Map.of("generatedAt", "2024-06-03"))));
        when(checkinServiceClient.getRecentCheckins(eq("u_1"), eq("internal-key"), anyInt()))
                .thenReturn(List.of(Map.of("checkinDate", "2024-06-04")));

        when(exportFileGenerator.generate(eq("pdf"), any())).thenReturn(new byte[]{3});
        when(exportFileGenerator.fileExtension("pdf")).thenReturn("pdf");
        when(exportFileGenerator.contentType("pdf")).thenReturn("application/pdf");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/export/file.pdf");

        ExportDataRequest request = new ExportDataRequest(
                List.of("health", "risk", "consultation", "plan", "checkin"),
                "pdf", "2024-06-01", "2024-06-30");
        ExportTaskResponse response = dataExportService.submitExport("u_1", request);

        assertEquals("completed", response.status());
        verify(healthServiceClient).getLatestHealthProfile("u_1", "internal-key");
    }

    @Test
    void submitExport_withDateFiltering() {
        stubProfile();
        when(consultationServiceClient.listSessions(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("list", List.of(
                        Map.of("started_at", "2024-05-01"),
                        Map.of("started_at", "2024-06-15"))));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(
                List.of("consultation"), "excel", "2024-06-01", "2024-06-30");
        dataExportService.submitExport("u_1", request);

        verify(consultationServiceClient).listSessions("u_1", "internal-key", 1, 100);
    }

    @Test
    void submitExport_checkinInvalidDateRange() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), eq(90)))
                .thenReturn(List.of(Map.of("checkin_date", "2024-01-01")));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(
                List.of("checkin"), "excel", "bad-date", "2024-06-30");
        assertDoesNotThrow(() -> dataExportService.submitExport("u_1", request));
    }

    @Test
    void submitExport_emptyConsultationSource() {
        stubProfile();
        when(consultationServiceClient.listSessions(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of());
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(
                List.of("consultation"), "excel", "2024-06-01", "2024-06-30");
        assertDoesNotThrow(() -> dataExportService.submitExport("u_1", request));
    }

    @Test
    void submitExport_planWithoutDateFilter() {
        stubProfile();
        when(planServiceClient.getPlanHistory(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("plans", List.of(Map.of("generated_at", "2024-01-01"))));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(List.of("plan"), "excel", null, null);
        dataExportService.submitExport("u_1", request);
        verify(planServiceClient).getPlanHistory("u_1", "internal-key", 1, 100);
    }

    @Test
    void submitExport_consultationNonListValue() {
        stubProfile();
        when(consultationServiceClient.listSessions(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("sessions", "not-a-list"));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(
                List.of("consultation"), "excel", "2024-06-01", "2024-06-30");
        assertDoesNotThrow(() -> dataExportService.submitExport("u_1", request));
    }

    @Test
    void submitExport_checkinNoDateFilter() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), eq(90)))
                .thenReturn(List.of(Map.of("checkin_date", "2024-01-01")));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(List.of("checkin"), "excel", null, null);
        dataExportService.submitExport("u_1", request);
        verify(checkinServiceClient).getRecentCheckins("u_1", "internal-key", 90);
    }

    @Test
    void submitExport_riskWithDateFilter() {
        stubProfile();
        when(healthServiceClient.getRiskHistory(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("records", List.of(Map.of("assessed_at", "2024-06-10"))));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(
                List.of("risk"), "excel", "2024-06-01", "2024-06-30");
        dataExportService.submitExport("u_1", request);
    }

    @Test
    void getTask_notFound() {
        assertThrows(BusinessException.class,
                () -> dataExportService.getTask("u_1", "missing"));
    }

    @Test
    void getTask_wrongUser() {
        ExportTaskResponse submitted = submitSampleExport();
        assertThrows(BusinessException.class,
                () -> dataExportService.getTask("u_2", submitted.task_id()));
    }

    @Test
    void getTask_expired() throws Exception {
        ExportTaskResponse submitted = submitSampleExport();
        replaceTaskExpiry(submitted.task_id(), LocalDateTime.now().minusHours(1));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> dataExportService.getTask("u_1", submitted.task_id()));
        assertEquals(410, ex.getCode());
    }

    @Test
    void getTask_success() {
        ExportTaskResponse submitted = submitSampleExport();
        ExportTaskResponse response = dataExportService.getTask("u_1", submitted.task_id());
        assertEquals(submitted.task_id(), response.task_id());
        assertEquals("completed", response.status());
        assertEquals("导出已完成", response.message());
    }

    @Test
    void getTask_processingStatus() throws Exception {
        putTask("task_4", "u_1", "processing", LocalDateTime.now().plusHours(1));
        ExportTaskResponse response = dataExportService.getTask("u_1", "task_4");
        assertEquals("处理中", response.message());
    }

    @Test
    void submitExport_checkinFiltersBlankCheckinDate() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        Map.of("note", "no-date"),
                        Map.of("checkin_date", "2024-06-15")));
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        ExportDataRequest request = new ExportDataRequest(
                List.of("checkin"), "excel", "2024-06-01", "2024-06-30");
        assertDoesNotThrow(() -> dataExportService.submitExport("u_1", request));
    }

    @Test
    void submitExport_blankFormatDefaultsToExcel() {
        stubProfile();
        when(exportFileGenerator.generate(eq("excel"), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension("excel")).thenReturn("xlsx");
        when(exportFileGenerator.contentType("excel")).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("health"), "  ", null, null));

        verify(exportFileGenerator).generate(eq("excel"), any());
    }

    @Test
    void submitExport_checkinOnlyStartDate() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), eq(90)))
                .thenReturn(List.of(Map.of("checkin_date", "2024-06-15")));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("checkin"), "excel", "2024-06-01", null));
        verify(checkinServiceClient).getRecentCheckins("u_1", "internal-key", 90);
    }

    @Test
    void submitExport_checkinOnlyEndDate() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), eq(90)))
                .thenReturn(List.of(Map.of("checkin_date", "2024-06-15")));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("checkin"), "excel", null, "2024-06-30"));
        verify(checkinServiceClient).getRecentCheckins("u_1", "internal-key", 90);
    }

    @Test
    void submitExport_checkinBlankStartDate() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), eq(90)))
                .thenReturn(List.of(Map.of("checkin_date", "2024-06-15")));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("checkin"), "excel", "  ", "2024-06-30"));
        verify(checkinServiceClient).getRecentCheckins("u_1", "internal-key", 90);
    }

    @Test
    void submitExport_checkinValidDateRangeDays() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), eq(30)))
                .thenReturn(List.of(Map.of("checkin_date", "2024-06-15")));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("checkin"), "excel", "2024-06-01", "2024-06-30"));
        verify(checkinServiceClient).getRecentCheckins("u_1", "internal-key", 30);
    }

    @Test
    void submitExport_consultationNullSource() {
        stubProfile();
        when(consultationServiceClient.listSessions(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(null);
        stubExportUpload();

        assertDoesNotThrow(() -> dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("consultation"), "excel", "2024-06-01", "2024-06-30")));
    }

    @Test
    void submitExport_planPartialDateRange() {
        stubProfile();
        when(planServiceClient.getPlanHistory(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("plans", List.of(Map.of("generated_at", "2024-06-15"))));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("plan"), "excel", "2024-06-01", null));
        verify(planServiceClient).getPlanHistory("u_1", "internal-key", 1, 100);
    }

    @Test
    void submitExport_planBlankEndDate() {
        stubProfile();
        when(planServiceClient.getPlanHistory(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("plans", List.of(Map.of("generated_at", "2024-06-15"))));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("plan"), "excel", "2024-06-01", "  "));
        verify(planServiceClient).getPlanHistory("u_1", "internal-key", 1, 100);
    }

    @Test
    void submitExport_checkinDateOutsideRange() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        Map.of("checkin_date", "2024-05-01"),
                        Map.of("checkin_date", "2024-07-01"),
                        Map.of("checkin_date", "2024-06-15")));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("checkin"), "excel", "2024-06-01", "2024-06-30"));
        verify(checkinServiceClient).getRecentCheckins("u_1", "internal-key", 30);
    }

    @Test
    void submitExport_checkinShortDateValue() {
        stubProfile();
        when(checkinServiceClient.getRecentCheckins(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(Map.of("checkin_date", "2024-6-5")));
        stubExportUpload();

        assertDoesNotThrow(() -> dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("checkin"), "excel", "2024-06-01", "2024-06-30")));
    }

    @Test
    void submitExport_consultationUsesPrimaryKey() {
        stubProfile();
        when(consultationServiceClient.listSessions(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(Map.of("sessions", List.of(Map.of("started_at", "2024-06-15"))));
        stubExportUpload();

        dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("consultation"), "excel", "2024-06-01", "2024-06-30"));
        verify(consultationServiceClient).listSessions("u_1", "internal-key", 1, 100);
    }

    @Test
    void withinDateRange_nullValue() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "withinDateRange", String.class, String.class, String.class);
        method.setAccessible(true);
        assertEquals(false, method.invoke(dataExportService, null, "2024-06-01", "2024-06-30"));
    }

    @Test
    void withinDateRange_blankValue() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "withinDateRange", String.class, String.class, String.class);
        method.setAccessible(true);
        assertEquals(false, method.invoke(dataExportService, "  ", "2024-06-01", "2024-06-30"));
    }

    @Test
    void resolveCheckinDays_blankEndDate() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "resolveCheckinDays", String.class, String.class);
        method.setAccessible(true);
        assertEquals(90, method.invoke(dataExportService, "2024-06-01", "  "));
    }

    @Test
    void filterCheckins_startBlankEndValid() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "filterCheckins", List.class, String.class, String.class);
        method.setAccessible(true);
        List<Map<String, Object>> records = List.of(Map.of("checkin_date", "2024-06-15"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(
                dataExportService, records, "  ", "2024-06-30");
        assertEquals(1, result.size());
    }

    @Test
    void filterCheckins_validStartBlankEnd() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "filterCheckins", List.class, String.class, String.class);
        method.setAccessible(true);
        List<Map<String, Object>> records = List.of(Map.of("checkin_date", "2024-06-15"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(
                dataExportService, records, "2024-06-01", "  ");
        assertEquals(1, result.size());
    }

    @Test
    void filterCheckins_nullStartBlankEnd() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "filterCheckins", List.class, String.class, String.class);
        method.setAccessible(true);
        List<Map<String, Object>> records = List.of(Map.of("checkin_date", "2024-06-15"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) method.invoke(
                dataExportService, records, null, "  ");
        assertEquals(1, result.size());
    }

    @Test
    void filterByDateRange_blankEndDateReturnsSource() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "filterByDateRange", Map.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        Map<String, Object> source = Map.of("plans", List.of(Map.of("generated_at", "2024-06-15")));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
                dataExportService, source, "plans", null,
                "generatedAt", "generated_at", "2024-06-01", "  ");
        assertSame(source, result);
    }

    @Test
    void filterByDateRange_blankStartValidEnd() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "filterByDateRange", Map.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        Map<String, Object> source = Map.of("plans", List.of(Map.of("generated_at", "2024-06-15")));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
                dataExportService, source, "plans", null,
                "generatedAt", "generated_at", "  ", "2024-06-30");
        assertSame(source, result);
    }

    @Test
    void filterByDateRange_nullPrimaryWithoutSecondaryKey() throws Exception {
        var method = DataExportService.class.getDeclaredMethod(
                "filterByDateRange", Map.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        Map<String, Object> source = Map.of("total", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
                dataExportService, source, "plans", null,
                "generatedAt", "generated_at", "2024-06-01", "2024-06-30");
        assertSame(source, result);
    }

    private void stubExportUpload() {
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");
    }

    private ExportTaskResponse submitSampleExport() {
        stubProfile();
        when(exportFileGenerator.generate(anyString(), any())).thenReturn(new byte[]{1});
        when(exportFileGenerator.fileExtension(anyString())).thenReturn("xlsx");
        when(exportFileGenerator.contentType(anyString())).thenReturn("application/vnd.ms-excel");
        when(minioStorageService.uploadExportFile(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn("http://minio/file");
        return dataExportService.submitExport("u_1",
                new ExportDataRequest(List.of("health"), "excel", null, null));
    }

    private void stubProfile() {
        when(userProfileService.getProfile("u_1")).thenReturn(
                new UserProfileResponse("u_1", "alice", "138", "a@b.com", "",
                        "A", 1, "1990-01-01", 10, null, "2024-01-01"));
    }

    private void putTask(String taskId, String userId, String status, LocalDateTime expiresAt)
            throws Exception {
        Field field = DataExportService.class.getDeclaredField("tasks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> tasks = (Map<String, Object>) field.get(dataExportService);
        tasks.put(taskId, newTaskRecord(taskId, userId, status,
                "http://download", "export.xlsx", expiresAt));
    }

    private void replaceTaskExpiry(String taskId, LocalDateTime expiresAt) throws Exception {
        Field field = DataExportService.class.getDeclaredField("tasks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> tasks = (Map<String, Object>) field.get(dataExportService);
        Object current = tasks.get(taskId);
        Class<?> recordClass = current.getClass();
        String userId = (String) recordClass.getDeclaredMethod("userId").invoke(current);
        String status = (String) recordClass.getDeclaredMethod("status").invoke(current);
        String downloadUrl = (String) recordClass.getDeclaredMethod("downloadUrl").invoke(current);
        String fileName = (String) recordClass.getDeclaredMethod("fileName").invoke(current);
        tasks.put(taskId, newTaskRecord(taskId, userId, status, downloadUrl, fileName, expiresAt));
    }

    private Object newTaskRecord(String taskId, String userId, String status,
                                 String url, String fileName, LocalDateTime expiresAt) throws Exception {
        Class<?> recordClass = Class.forName("com.diabetes.user.service.DataExportService$ExportTaskRecord");
        var ctor = recordClass.getDeclaredConstructor(String.class, String.class, String.class,
                String.class, String.class, LocalDateTime.class);
        ctor.setAccessible(true);
        return ctor.newInstance(taskId, userId, status, url, fileName, expiresAt);
    }
}
