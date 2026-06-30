package com.diabetes.user.export;

import com.diabetes.common.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class ExportFileGenerator {

    public byte[] generate(String format, Map<String, Object> payload) {
        return "pdf".equalsIgnoreCase(format) ? generatePdf(payload) : generateExcel(payload);
    }

    public String contentType(String format) {
        return "pdf".equalsIgnoreCase(format)
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    public String fileExtension(String format) {
        return "pdf".equalsIgnoreCase(format) ? "pdf" : "xlsx";
    }

    private byte[] generateExcel(Map<String, Object> payload) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeProfileSheet(workbook, payload);
            if (payload.containsKey("health")) {
                writeMapSheet(workbook, "健康档案", asMap(payload.get("health")));
            }
            if (payload.containsKey("risk")) {
                writeListSheet(workbook, "风险评估", extractList(payload.get("risk"), "records"));
            }
            if (payload.containsKey("consultation")) {
                writeListSheet(workbook, "问诊记录", extractList(payload.get("consultation"), "sessions", "list"));
            }
            if (payload.containsKey("plan")) {
                writeListSheet(workbook, "健康方案", extractList(payload.get("plan"), "plans"));
            }
            if (payload.containsKey("checkin")) {
                writeListSheet(workbook, "打卡记录", extractList(payload.get("checkin"), "records"));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 生成失败: " + e.getMessage());
        }
    }

    private void writeProfileSheet(Workbook workbook, Map<String, Object> payload) {
        Sheet sheet = workbook.createSheet("个人资料");
        int rowIdx = 0;
        rowIdx = writeRow(sheet, rowIdx, "导出时间", stringValue(payload.get("exported_at")));
        Map<String, Object> profile = asMap(payload.get("profile"));
        for (Map.Entry<String, Object> entry : profile.entrySet()) {
            rowIdx = writeRow(sheet, rowIdx, labelOf(entry.getKey()), stringValue(entry.getValue()));
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeMapSheet(Workbook workbook, String name, Map<String, Object> data) {
        Sheet sheet = workbook.createSheet(name);
        int rowIdx = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            rowIdx = writeRow(sheet, rowIdx, labelOf(entry.getKey()), stringValue(entry.getValue()));
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    @SuppressWarnings("unchecked")
    private void writeListSheet(Workbook workbook, String name, List<Map<String, Object>> rows) {
        Sheet sheet = workbook.createSheet(name);
        if (rows.isEmpty()) {
            writeRow(sheet, 0, "提示", "暂无数据");
            return;
        }
        List<String> headers = rows.get(0).keySet().stream().map(this::labelOf).toList();
        Row headerRow = sheet.createRow(0);
        int col = 0;
        for (String header : headers) {
            headerRow.createCell(col++).setCellValue(header);
        }
        int rowIdx = 1;
        for (Map<String, Object> item : rows) {
            Row row = sheet.createRow(rowIdx++);
            col = 0;
            for (String key : rows.get(0).keySet()) {
                row.createCell(col++).setCellValue(stringValue(item.get(key)));
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int writeRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    private byte[] generatePdf(Map<String, Object> payload) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font titleFont = createCjkFont(16, Font.BOLD);
            Font bodyFont = createCjkFont(11, Font.NORMAL);
            document.add(new Paragraph("糖尿病预治助手 - 个人健康数据导出", titleFont));
            document.add(new Paragraph("导出时间：" + stringValue(payload.get("exported_at")), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            appendSection(document, "个人资料", asMap(payload.get("profile")), bodyFont);
            if (payload.containsKey("health")) {
                appendSection(document, "健康档案", asMap(payload.get("health")), bodyFont);
            }
            if (payload.containsKey("risk")) {
                appendListSection(document, "风险评估", extractList(payload.get("risk"), "records"), bodyFont);
            }
            if (payload.containsKey("consultation")) {
                appendListSection(document, "问诊记录", extractList(payload.get("consultation"), "sessions", "list"), bodyFont);
            }
            if (payload.containsKey("plan")) {
                appendListSection(document, "健康方案", extractList(payload.get("plan"), "plans"), bodyFont);
            }
            if (payload.containsKey("checkin")) {
                appendListSection(document, "打卡记录", extractList(payload.get("checkin"), "records"), bodyFont);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "PDF 生成失败: " + e.getMessage());
        }
    }

    private void appendSection(Document document, String title, Map<String, Object> data, Font font) throws Exception {
        document.add(new Paragraph(title, font));
        if (data.isEmpty()) {
            document.add(new Paragraph("暂无数据", font));
        } else {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                document.add(new Paragraph(labelOf(entry.getKey()) + "：" + stringValue(entry.getValue()), font));
            }
        }
        document.add(new Paragraph(" ", font));
    }

    private void appendListSection(Document document, String title, List<Map<String, Object>> rows, Font font)
            throws Exception {
        document.add(new Paragraph(title, font));
        if (rows.isEmpty()) {
            document.add(new Paragraph("暂无数据", font));
        } else {
            int index = 1;
            for (Map<String, Object> row : rows) {
                document.add(new Paragraph("【记录 " + index++ + "】", font));
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    document.add(new Paragraph("  " + labelOf(entry.getKey()) + "：" + stringValue(entry.getValue()), font));
                }
            }
        }
        document.add(new Paragraph(" ", font));
    }

    private Font createCjkFont(float size, int style) throws Exception {
        BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        return new Font(baseFont, size, style);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractList(Object source, String... keys) {
        if (!(source instanceof Map<?, ?> map)) {
            return List.of();
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Collection<?> collection) {
                return collection.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> normalizeRow((Map<String, Object>) item))
                        .toList();
            }
        }
        return List.of();
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        row.forEach((k, v) -> normalized.put(k, v));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String labelOf(String key) {
        return switch (key) {
            case "user_id", "userId" -> "用户ID";
            case "username" -> "用户名";
            case "nickname" -> "昵称";
            case "phone" -> "手机号";
            case "email" -> "邮箱";
            case "points" -> "积分";
            case "height" -> "身高(cm)";
            case "weight" -> "体重(kg)";
            case "bmi" -> "BMI";
            case "fastingGlucose", "fasting_glucose" -> "空腹血糖";
            case "postprandialGlucose", "postprandial_glucose" -> "餐后血糖";
            case "hba1c" -> "糖化血红蛋白";
            case "systolicBp", "systolic_bp" -> "收缩压";
            case "diastolicBp", "diastolic_bp" -> "舒张压";
            case "recordedAt", "recorded_at" -> "记录时间";
            case "riskLevel", "risk_level" -> "风险等级";
            case "riskScore", "risk_score" -> "风险评分";
            case "assessedAt", "assessed_at" -> "评估时间";
            case "reportSummary", "report_summary" -> "报告摘要";
            case "sessionId", "session_id" -> "会话ID";
            case "doctorName", "doctor_name" -> "医生";
            case "doctorTitle", "doctor_title" -> "职称";
            case "department" -> "科室";
            case "status" -> "状态";
            case "startedAt", "started_at" -> "开始时间";
            case "endedAt", "ended_at" -> "结束时间";
            case "lastMessage", "last_message" -> "最后消息";
            case "rating" -> "评分";
            case "feedback" -> "评价";
            case "planId", "plan_id" -> "方案ID";
            case "version" -> "版本";
            case "dailyCalories", "daily_calories" -> "每日热量";
            case "generatedAt", "generated_at" -> "生成时间";
            case "checkinType", "checkin_type" -> "打卡类型";
            case "checkinDate", "checkin_date" -> "打卡日期";
            case "pointsEarned", "points_earned" -> "获得积分";
            case "streakDays", "streak_days" -> "连续天数";
            default -> key;
        };
    }
}
