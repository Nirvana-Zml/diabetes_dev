package com.diabetes.checkin.controller;

import com.diabetes.checkin.dto.ExerciseCheckinRequest;
import com.diabetes.checkin.dto.FoodCheckinRequest;
import com.diabetes.checkin.dto.GlucoseCheckinRequest;
import com.diabetes.checkin.dto.ImageUploadResponse;
import com.diabetes.checkin.dto.MedicationCheckinRequest;
import com.diabetes.checkin.entity.CheckinRecord;
import com.diabetes.checkin.mapper.CheckinRecordMapper;
import com.diabetes.checkin.service.CheckinImageService;
import com.diabetes.checkin.service.CheckinMgmtService;
import com.diabetes.checkin.service.CheckinPresetService;
import com.diabetes.checkin.service.CheckinService;
import com.diabetes.checkin.service.ExerciseCheckinService;
import com.diabetes.checkin.service.FoodCheckinService;
import com.diabetes.checkin.service.GlucoseCheckinService;
import com.diabetes.checkin.service.MedicationCheckinService;
import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CheckinControllersTest {

    @Test
    void checkinControllerWrapsServiceResponses() {
        CheckinService service = mock(CheckinService.class);
        CheckinController controller = new CheckinController(service);
        when(service.getTodayStatus("u1")).thenReturn(Map.of("todayPoints", 1));
        when(service.getStats("u1", "weekly")).thenReturn(Map.of("totalCheckins", 2));
        when(service.getAchievements("u1")).thenReturn(List.of(Map.of("name", "首次打卡")));

        assertEquals(1, controller.today("u1").data().get("todayPoints"));
        assertEquals(2, controller.stats("u1", "weekly").data().get("totalCheckins"));
        assertEquals(1, ((List<?>) controller.achievements("u1").data().get("achievements")).size());
    }

    @Test
    void moduleControllerDelegatesAllEndpointsAndParsesHistoryDates() {
        CheckinImageService imageService = mock(CheckinImageService.class);
        CheckinPresetService presetService = mock(CheckinPresetService.class);
        FoodCheckinService foodService = mock(FoodCheckinService.class);
        MedicationCheckinService medicationService = mock(MedicationCheckinService.class);
        ExerciseCheckinService exerciseService = mock(ExerciseCheckinService.class);
        GlucoseCheckinService glucoseService = mock(GlucoseCheckinService.class);
        CheckinModuleController controller = new CheckinModuleController(
                imageService, presetService, foodService, medicationService, exerciseService, glucoseService);
        MultipartFile file = mock(MultipartFile.class);

        when(imageService.upload("u1", "food", file)).thenReturn(new ImageUploadResponse("id", "key", "url"));
        when(presetService.listFoodCategories()).thenReturn(List.of(Map.of("categoryId", "cat")));
        when(presetService.listFoodPresets("cat")).thenReturn(List.of(Map.of("foodId", "food")));
        when(presetService.listMedicationPresets()).thenReturn(List.of(Map.of("drugId", "drug")));
        when(presetService.listExercisePresets()).thenReturn(List.of(Map.of("exerciseId", "ex")));
        when(foodService.createCheckin(eq("u1"), any(FoodCheckinRequest.class))).thenReturn(Map.of("checkinId", "food"));
        when(foodService.listRecords("u1", "2024-01-01")).thenReturn(List.of(Map.of("checkinId", "food")));
        when(medicationService.createCheckin(eq("u1"), any(MedicationCheckinRequest.class))).thenReturn(Map.of("checkinId", "med"));
        when(medicationService.listRecords("u1", "2024-01-01")).thenReturn(List.of(Map.of("checkinId", "med")));
        when(exerciseService.createCheckin(eq("u1"), any(ExerciseCheckinRequest.class))).thenReturn(Map.of("checkinId", "ex"));
        when(exerciseService.listRecords("u1", "2024-01-01")).thenReturn(List.of(Map.of("checkinId", "ex")));
        when(glucoseService.createCheckin(eq("u1"), any(GlucoseCheckinRequest.class))).thenReturn(Map.of("checkinId", "glu"));
        when(glucoseService.listRecords("u1", "2024-01-01")).thenReturn(List.of(Map.of("checkinId", "glu")));
        when(glucoseService.getHistory(eq("u1"), any(LocalDate.class), any(LocalDate.class))).thenReturn(Map.of("records", List.of()));

        assertEquals("key", controller.uploadImage("u1", "food", file).data().getObjectKey());
        assertEquals("cat", controller.foodCategories().data().get(0).get("categoryId"));
        assertEquals("food", controller.foodPresets("cat").data().get(0).get("foodId"));
        assertEquals("food", controller.createFoodCheckin("u1", new FoodCheckinRequest()).data().get("checkinId"));
        assertEquals("food", controller.foodRecords("u1", "2024-01-01").data().get(0).get("checkinId"));
        assertEquals("drug", controller.medicationPresets().data().get(0).get("drugId"));
        assertEquals("med", controller.createMedicationCheckin("u1", new MedicationCheckinRequest()).data().get("checkinId"));
        assertEquals("med", controller.medicationRecords("u1", "2024-01-01").data().get(0).get("checkinId"));
        assertEquals("ex", controller.exercisePresets().data().get(0).get("exerciseId"));
        assertEquals("ex", controller.createExerciseCheckin("u1", new ExerciseCheckinRequest()).data().get("checkinId"));
        assertEquals("ex", controller.exerciseRecords("u1", "2024-01-01").data().get(0).get("checkinId"));
        assertEquals("glu", controller.createGlucoseCheckin("u1", new GlucoseCheckinRequest()).data().get("checkinId"));
        assertEquals("glu", controller.glucoseRecords("u1", "2024-01-01").data().get(0).get("checkinId"));
        assertTrue(((List<?>) controller.glucoseHistory("u1", "2024-01-01", "2024-01-02", 14).data().get("records")).isEmpty());
        assertTrue(((List<?>) controller.glucoseHistory("u1", null, "2024-01-02", 0).data().get("records")).isEmpty());
        assertTrue(((List<?>) controller.glucoseHistory("u1", null, null, 14).data().get("records")).isEmpty());
        assertTrue(((List<?>) controller.glucoseHistory("u1", " ", " ", 14).data().get("records")).isEmpty());
        assertThrows(BusinessException.class, () -> controller.glucoseHistory("u1", "bad", "2024-01-02", 14));
    }

    @Test
    void managementControllerParsesDatesAndDefaults() {
        CheckinMgmtService service = mock(CheckinMgmtService.class);
        CheckinMgmtController controller = new CheckinMgmtController(service);
        when(service.getStats(eq("u1"), any(), any())).thenReturn(Map.of("stats", true));
        when(service.getTrends(eq("u1"), any(), any())).thenReturn(Map.of("trends", true));
        when(service.getAiSummary(eq("u1"), any(), any())).thenReturn(Map.of("source", "local"));
        when(service.getDifyWorkflowSpec()).thenReturn(Map.of("workflowUrl", "url"));
        when(service.exportReport(eq("u1"), any(), any(), anyString())).thenReturn(Map.of("format", "pdf"));

        assertTrue((Boolean) controller.stats("u1", "2024-01-01", "2024-01-02").data().get("stats"));
        assertTrue((Boolean) controller.trends("u1", "2024-01-01", "2024-01-02").data().get("trends"));
        assertEquals("local", controller.aiSummary("u1", null, null).data().get("source"));
        assertEquals("local", controller.aiSummary("u1", "2024-01-01", "2024-01-02").data().get("source"));
        assertEquals("url", controller.difyWorkflowSpec().data().get("workflowUrl"));
        assertEquals("pdf", controller.export("u1", Map.of("startDate", "2024-01-01", "endDate", "2024-01-02")).data().get("format"));
        assertThrows(BusinessException.class, () -> controller.stats("u1", "bad", "2024-01-02"));
    }

    @Test
    void internalControllerValidatesDifyKeyAndMapsRecentRecords() {
        CheckinRecordMapper mapper = mock(CheckinRecordMapper.class);
        InternalCheckinController secured = new InternalCheckinController(mapper, "secret");
        InternalCheckinController open = new InternalCheckinController(mapper, "");
        when(mapper.findRecentByUser(eq("u1"), any())).thenReturn(List.of(
                record("a", 1), record("b", 2), record("c", 3), record("d", 4), record("e", 99), record("f", null)
        ));

        assertThrows(BusinessException.class, () -> secured.recent("u1", 14, "bad"));
        assertThrows(BusinessException.class, () -> secured.recent("u1", 14, null));
        List<Map<String, Object>> records = secured.recent("u1", 0, "secret").data();
        assertEquals(List.of("diet", "exercise", "medication", "glucose", "unknown", "unknown"),
                records.stream().map(r -> r.get("checkinType")).toList());
        assertEquals(6, open.recent("u1", 1, null).data().size());
        assertEquals(6, new InternalCheckinController(mapper, null).recent("u1", 1, "any").data().size());
    }

    private static CheckinRecord record(String id, Integer type) {
        CheckinRecord record = new CheckinRecord();
        record.setCheckinId(id);
        record.setCheckinType(type);
        record.setCheckinDate(LocalDate.of(2024, 1, 1));
        record.setPointsEarned(1);
        record.setStreakDays(2);
        return record;
    }
}
