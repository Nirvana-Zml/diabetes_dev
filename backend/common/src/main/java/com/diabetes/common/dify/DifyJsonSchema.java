package com.diabetes.common.dify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 工作流入参/出参统一 JSON Schema 构建工具。
 *
 * <p>所有工作流 inputs 变量均遵循：</p>
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": { ... },
 *   "required": [],
 *   "additionalProperties": true
 * }
 * }</pre>
 */
public final class DifyJsonSchema {

    private DifyJsonSchema() {
    }

    public static Map<String, Object> rootObject(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? Map.of() : properties);
        schema.put("required", List.of());
        schema.put("additionalProperties", true);
        return schema;
    }

    public static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties == null ? Map.of() : properties);
        schema.put("required", List.of());
        schema.put("additionalProperties", true);
        return schema;
    }

    public static Map<String, Object> stringType() {
        return Map.of("type", "string");
    }

    public static Map<String, Object> numberType() {
        return Map.of("type", "number");
    }

    public static Map<String, Object> integerType() {
        return Map.of("type", "integer");
    }

    public static Map<String, Object> booleanType() {
        return Map.of("type", "boolean");
    }

    public static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    /**
     * 平铺传入 Dify Workflow API 的 inputs（开始节点各变量与 payload 字段一一对应）。
     */
    public static Map<String, Object> flatWorkflowInputs(Map<String, Object> payload) {
        return sanitize(payload);
    }

    /** 构建 Dify Workflow API 的 inputs 包装层（单变量 {@code inputVarName} 包裹整个 payload）。 */
    public static Map<String, Object> wrapWorkflowInputs(String inputVarName,
                                                         Map<String, Object> payload,
                                                         String inputFormat,
                                                         com.fasterxml.jackson.databind.ObjectMapper mapper) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        try {
            Object value = "string".equalsIgnoreCase(inputFormat)
                    ? mapper.writeValueAsString(sanitize(payload))
                    : sanitize(payload);
            inputs.put(inputVarName, value);
        } catch (Exception e) {
            throw new IllegalStateException("构建 Dify inputs 失败: " + e.getMessage(), e);
        }
        return inputs;
    }

    public static Map<String, Object> sanitize(Map<String, Object> payload) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (payload == null) return clean;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                clean.put(entry.getKey(), entry.getValue());
            }
        }
        return clean;
    }

    public static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 将对象序列化为 JSON 字符串；null 或空字符串时返回 {@code "{}"}。 */
    public static String asJsonString(Object value, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (value == null) {
            return "{}";
        }
        if (value instanceof String s) {
            return s.isBlank() ? "{}" : s;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 将列表序列化为 JSON 数组字符串；null 时返回 {@code "[]"}。 */
    public static String asJsonArrayString(Object value, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        if (value == null) {
            return "[]";
        }
        if (value instanceof String s) {
            return s.isBlank() ? "[]" : s;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    public static int asInteger(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double asNumber(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> asObjectList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) result.add(String.valueOf(item));
        }
        return result;
    }
}
