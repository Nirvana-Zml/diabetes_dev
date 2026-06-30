package com.diabetes.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ExportDataRequest(
        @NotEmpty(message = "请至少选择一项导出范围")
        List<@Pattern(regexp = "health|consultation|plan|checkin|risk") String> types,

        @Pattern(regexp = "pdf|excel", message = "导出格式无效")
        String format,

        String start_date,

        String end_date
) {}
