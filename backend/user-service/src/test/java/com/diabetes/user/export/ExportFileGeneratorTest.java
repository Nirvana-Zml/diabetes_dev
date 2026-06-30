package com.diabetes.user.export;

import com.diabetes.common.exception.BusinessException;
import com.lowagie.text.Document;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExportFileGeneratorTest {

    private ExportFileGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ExportFileGenerator();
    }

    @Test
    void contentTypeAndExtension() {
        assertEquals("application/pdf", generator.contentType("pdf"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", generator.contentType("excel"));
        assertEquals("pdf", generator.fileExtension("PDF"));
        assertEquals("xlsx", generator.fileExtension("excel"));
    }

    @Test
    void generateExcel_fullPayload() {
        Map<String, Object> payload = fullPayload();
        byte[] bytes = generator.generate("excel", payload);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void generatePdf_fullPayload() {
        Map<String, Object> payload = fullPayload();
        byte[] bytes = generator.generate("pdf", payload);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void generateExcel_minimalPayload() {
        Map<String, Object> payload = Map.of(
                "exported_at", "2024-01-01",
                "profile", Map.of("userId", "u_1", "username", "alice"));
        byte[] bytes = generator.generate("xlsx", payload);
        assertTrue(bytes.length > 0);
    }

    @Test
    void generatePdf_emptySections() {
        Map<String, Object> payload = Map.of(
                "exported_at", "2024-01-01",
                "profile", Map.of(),
                "risk", Map.of("records", List.of()),
                "consultation", Map.of("sessions", List.of()),
                "plan", Map.of("plans", List.of()),
                "checkin", Map.of("records", List.of()));
        byte[] bytes = generator.generate("pdf", payload);
        assertTrue(bytes.length > 0);
    }

    @Test
    void generatePdf_profileAndHealthOnly() {
        Map<String, Object> payload = Map.of(
                "exported_at", "2024-01-01",
                "profile", Map.of("userId", "u_1", "username", "alice"),
                "health", Map.of("bmi", 22));
        byte[] bytes = generator.generate("pdf", payload);
        assertTrue(bytes.length > 0);
    }

    @Test
    void generateExcel_withListData() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", "u_1");
        row.put("fasting_glucose", 5.6);
        row.put("risk_level", "low");
        row.put("session_id", "s_1");
        row.put("doctor_name", "Dr");
        row.put("plan_id", "p_1");
        row.put("checkin_date", "2024-01-01");
        row.put("unknown_field", "x");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exported_at", "2024-01-01");
        payload.put("profile", Map.of("userId", "u_1"));
        payload.put("health", Map.of("height", 170, "weight", 65));
        payload.put("risk", Map.of("records", List.of(row)));
        payload.put("consultation", Map.of("list", List.of(row)));
        payload.put("plan", Map.of("plans", List.of(row)));
        payload.put("checkin", Map.of("records", List.of(row)));

        assertTrue(generator.generate("excel", payload).length > 0);
        assertTrue(generator.generate("pdf", payload).length > 0);
    }

    @Test
    void generateExcel_nonMapSources() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exported_at", "2024-01-01");
        payload.put("profile", "not-a-map");
        payload.put("risk", "bad");
        payload.put("consultation", Map.of());
        payload.put("plan", Map.of());
        payload.put("checkin", Map.of());
        assertDoesNotThrow(() -> generator.generate("excel", payload));
    }

    @Test
    void generateExcel_failure() {
        Map<String, Object> payload = Map.of(
                "exported_at", "2024-01-01",
                "profile", Map.of("userId", "u_1"));
        try (MockedConstruction<XSSFWorkbook> ignored = org.mockito.Mockito.mockConstruction(
                XSSFWorkbook.class,
                (mock, context) -> org.mockito.Mockito.doThrow(new IOException("disk full"))
                        .when(mock).write(org.mockito.ArgumentMatchers.any()))) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> generator.generate("excel", payload));
            assertTrue(ex.getMessage().contains("Excel 生成失败"));
        }
    }

    @Test
    void generatePdf_failure() {
        Map<String, Object> payload = Map.of(
                "exported_at", "2024-01-01",
                "profile", Map.of("userId", "u_1"));
        try (MockedConstruction<Document> ignored = org.mockito.Mockito.mockConstruction(
                Document.class,
                (mock, context) -> org.mockito.Mockito.doThrow(new RuntimeException("pdf err"))
                        .when(mock).open())) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> generator.generate("pdf", payload));
            assertTrue(ex.getMessage().contains("PDF 生成失败"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "user_id, 用户ID",
            "userId, 用户ID",
            "username, 用户名",
            "nickname, 昵称",
            "phone, 手机号",
            "email, 邮箱",
            "points, 积分",
            "height, 身高(cm)",
            "weight, 体重(kg)",
            "bmi, BMI",
            "fastingGlucose, 空腹血糖",
            "fasting_glucose, 空腹血糖",
            "postprandialGlucose, 餐后血糖",
            "postprandial_glucose, 餐后血糖",
            "hba1c, 糖化血红蛋白",
            "systolicBp, 收缩压",
            "systolic_bp, 收缩压",
            "diastolicBp, 舒张压",
            "diastolic_bp, 舒张压",
            "recordedAt, 记录时间",
            "recorded_at, 记录时间",
            "riskLevel, 风险等级",
            "risk_level, 风险等级",
            "riskScore, 风险评分",
            "risk_score, 风险评分",
            "assessedAt, 评估时间",
            "assessed_at, 评估时间",
            "reportSummary, 报告摘要",
            "report_summary, 报告摘要",
            "sessionId, 会话ID",
            "session_id, 会话ID",
            "doctorName, 医生",
            "doctor_name, 医生",
            "doctorTitle, 职称",
            "doctor_title, 职称",
            "department, 科室",
            "status, 状态",
            "startedAt, 开始时间",
            "started_at, 开始时间",
            "endedAt, 结束时间",
            "ended_at, 结束时间",
            "lastMessage, 最后消息",
            "last_message, 最后消息",
            "rating, 评分",
            "feedback, 评价",
            "planId, 方案ID",
            "plan_id, 方案ID",
            "version, 版本",
            "dailyCalories, 每日热量",
            "daily_calories, 每日热量",
            "generatedAt, 生成时间",
            "generated_at, 生成时间",
            "checkinType, 打卡类型",
            "checkin_type, 打卡类型",
            "checkinDate, 打卡日期",
            "checkin_date, 打卡日期",
            "pointsEarned, 获得积分",
            "points_earned, 获得积分",
            "streakDays, 连续天数",
            "streak_days, 连续天数",
            "custom_field, custom_field"
    })
    void labelOf_mapsKnownKeys(String key, String expected) throws Exception {
        Method method = ExportFileGenerator.class.getDeclaredMethod("labelOf", String.class);
        method.setAccessible(true);
        assertEquals(expected, method.invoke(generator, key));
    }

    @Test
    void stringValue_nullReturnsEmpty() throws Exception {
        Method method = ExportFileGenerator.class.getDeclaredMethod("stringValue", Object.class);
        method.setAccessible(true);
        assertEquals("", method.invoke(generator, (Object) null));
    }

    @Test
    void generateExcel_allLabelKeysInRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", "u_1");
        row.put("fasting_glucose", 5.6);
        row.put("postprandial_glucose", 7.8);
        row.put("systolic_bp", 120);
        row.put("diastolic_bp", 80);
        row.put("recorded_at", "2024-01-01");
        row.put("risk_score", 10);
        row.put("assessed_at", "2024-01-02");
        row.put("report_summary", "ok");
        row.put("doctor_title", "主任");
        row.put("department", "内分泌");
        row.put("status", "done");
        row.put("ended_at", "2024-01-03");
        row.put("last_message", "hi");
        row.put("rating", 5);
        row.put("feedback", "good");
        row.put("version", 1);
        row.put("daily_calories", 1800);
        row.put("checkin_type", "diet");
        row.put("points_earned", 10);
        row.put("streak_days", 3);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exported_at", "2024-01-01");
        payload.put("profile", Map.of(
                "userId", "u_1", "username", "a", "nickname", "n", "phone", "138",
                "email", "a@b.com", "points", 1, "height", 170, "weight", 65));
        payload.put("health", Map.of("bmi", 22, "hba1c", 6.1, "postprandialGlucose", 8.0));
        payload.put("risk", Map.of("records", List.of(row)));
        payload.put("consultation", Map.of("sessions", List.of(row)));
        payload.put("plan", Map.of("plans", List.of(row)));
        payload.put("checkin", Map.of("records", List.of(row)));

        assertTrue(generator.generate("excel", payload).length > 0);
        assertTrue(generator.generate("pdf", payload).length > 0);
    }

    private Map<String, Object> fullPayload() {
        Map<String, Object> row = Map.of(
                "userId", "u_1",
                "fastingGlucose", 5.6,
                "riskLevel", "low",
                "sessionId", "s_1",
                "doctorName", "Dr",
                "planId", "p_1",
                "checkinDate", "2024-01-01");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exported_at", "2024-06-01T10:00:00");
        payload.put("profile", Map.of("userId", "u_1", "username", "alice", "nickname", "A",
                "phone", "138", "email", "a@b.com", "points", 1, "gender", 1, "birthDate", "1990-01-01"));
        payload.put("health", Map.of("bmi", 22, "hba1c", 6.1));
        payload.put("risk", Map.of("records", List.of(row)));
        payload.put("consultation", Map.of("sessions", List.of(row)));
        payload.put("plan", Map.of("plans", List.of(row)));
        payload.put("checkin", Map.of("records", List.of(row)));
        return payload;
    }
}
