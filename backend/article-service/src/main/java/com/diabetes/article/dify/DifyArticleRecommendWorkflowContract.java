package com.diabetes.article.dify;

import com.diabetes.common.dify.DifyJsonSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资讯个性化推荐 Dify 工作流 JSON 契约。
 *
 * <p>开始节点 4 变量平铺传入，结构化字段均为 JSON 文本字符串。</p>
 */
public final class DifyArticleRecommendWorkflowContract {

    public static final String INPUT_VARIABLE_NAME = "flat";
    public static final String OUTPUT_KEY = "article_info";

    private static final Map<Integer, String> CATEGORY_SLUGS = Map.of(
            1, "diabetes_basics",
            2, "diet",
            3, "exercise",
            4, "medication",
            5, "complications"
    );

    private DifyArticleRecommendWorkflowContract() {
    }

    public static Map<String, Object> buildInputObject(String userId,
                                                       List<String> interestTags,
                                                       Map<Integer, Double> categoryWeights,
                                                       Map<String, Object> healthProfile,
                                                       Map<String, Object> riskProfile,
                                                       List<Map<String, Object>> candidateArticles,
                                                       ObjectMapper mapper) {
        Map<String, Object> userProfileObj = new LinkedHashMap<>();
        userProfileObj.put("user_id", DifyJsonSchema.asString(userId));
        userProfileObj.put("interest_tags", interestTags == null ? List.of() : interestTags);
        userProfileObj.put("category_weights", toNormalizedCategoryWeights(categoryWeights));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_profile", DifyJsonSchema.asJsonString(userProfileObj, mapper));
        payload.put("health_profile", DifyJsonSchema.asJsonString(
                toDifyHealthProfile(userId, healthProfile), mapper));
        payload.put("risk_profile", DifyJsonSchema.asJsonString(
                toDifyRiskProfile(userId, riskProfile), mapper));
        payload.put("candidate_articles", DifyJsonSchema.asJsonArrayString(
                toSchemaCompliantCandidates(candidateArticles), mapper));
        return ensureStringEncodedInputs(payload, mapper);
    }

    public static Map<String, Object> ensureStringEncodedInputs(Map<String, Object> payload, ObjectMapper mapper) {
        if (payload == null || payload.isEmpty()) {
            return payload == null ? Map.of() : new LinkedHashMap<>(payload);
        }
        Map<String, Object> out = new LinkedHashMap<>(payload);
        stringifyInputField(out, "user_profile", mapper, false);
        stringifyInputField(out, "health_profile", mapper, false);
        stringifyInputField(out, "risk_profile", mapper, false);
        stringifyInputField(out, "candidate_articles", mapper, true);
        return out;
    }

    private static void stringifyInputField(Map<String, Object> map,
                                            String key,
                                            ObjectMapper mapper,
                                            boolean array) {
        Object value = map.get(key);
        if (value instanceof String s) {
            if (s.isBlank()) {
                map.put(key, array ? "[]" : "{}");
            }
            return;
        }
        if (value == null) {
            map.put(key, array ? "[]" : "{}");
            return;
        }
        map.put(key, array
                ? DifyJsonSchema.asJsonArrayString(value, mapper)
                : DifyJsonSchema.asJsonString(value, mapper));
    }

    public static Map<String, Object> inputJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("user_profile", DifyJsonSchema.stringType());
        properties.put("health_profile", DifyJsonSchema.stringType());
        properties.put("risk_profile", DifyJsonSchema.stringType());
        properties.put("candidate_articles", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> outputJsonSchema() {
        Map<String, Object> recItemProps = new LinkedHashMap<>();
        recItemProps.put("article_id", DifyJsonSchema.stringType());
        recItemProps.put("rec_reason", DifyJsonSchema.stringType());

        Map<String, Object> articleInfoProps = new LinkedHashMap<>();
        articleInfoProps.put("recommendations", DifyJsonSchema.array(DifyJsonSchema.object(recItemProps)));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("article_info", DifyJsonSchema.object(articleInfoProps));
        properties.put("has_error", DifyJsonSchema.booleanType());
        properties.put("error_message", DifyJsonSchema.stringType());
        properties.put("source", DifyJsonSchema.stringType());
        return DifyJsonSchema.rootObject(properties);
    }

    public static Map<String, Object> workflowSpec(String baseUrl,
                                                   String apiKey,
                                                   String inputFormat,
                                                   String responseMode,
                                                   ObjectMapper mapper) {
        String normalizedBase = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("workflowUrl", normalizedBase + "/v1/workflows/run");
        spec.put("apiKey", apiKey);
        spec.put("responseMode", responseMode);
        spec.put("inputFormat", inputFormat);
        spec.put("inputVariableName", INPUT_VARIABLE_NAME);
        spec.put("inputVariables", List.of(
                "user_profile", "health_profile", "risk_profile", "candidate_articles"));
        spec.put("inputJsonSchema", inputJsonSchema());
        spec.put("outputJsonSchema", outputJsonSchema());
        spec.put("inputExample", buildInputObject("user_001",
                List.of("饮食管理", "血糖监测"),
                Map.of(2, 3.0, 3, 2.0),
                Map.of("diabetesType", 3, "bmi", 26.4),
                Map.of("riskLevel", "high", "riskScore", 72),
                List.of(),
                mapper));
        spec.put("outputKey", OUTPUT_KEY);
        return spec;
    }

    public static String categorySlug(Object category) {
        if (category == null) {
            return "diabetes_basics";
        }
        if (category instanceof Number n) {
            return CATEGORY_SLUGS.getOrDefault(n.intValue(), "diabetes_basics");
        }
        String raw = category.toString().trim();
        if (CATEGORY_SLUGS.containsValue(raw)) {
            return raw;
        }
        return switch (raw) {
            case "diet", "饮食", "饮食管理", "2" -> "diet";
            case "exercise", "运动", "运动康复", "3" -> "exercise";
            case "medication", "用药", "用药指导", "4" -> "medication";
            case "complications", "并发症", "5" -> "complications";
            case "glucose", "血糖监测" -> "glucose";
            case "risk" -> "risk";
            default -> "diabetes_basics";
        };
    }

    private static Map<String, Object> toNormalizedCategoryWeights(Map<Integer, Double> weights) {
        Map<String, Double> slugWeights = new LinkedHashMap<>();
        if (weights != null) {
            weights.forEach((catId, w) -> {
                if (w != null && w > 0) {
                    slugWeights.merge(categorySlug(catId), w, Double::sum);
                }
            });
        }
        double sum = slugWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (sum <= 0) {
            return normalized;
        }
        slugWeights.forEach((slug, w) ->
                normalized.put(slug, Math.round(w / sum * 1000.0) / 1000.0));
        return normalized;
    }

    private static Map<String, Object> toDifyHealthProfile(String userId, Map<String, Object> health) {
        Map<String, Object> src = DifyJsonSchema.asObject(health);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user_id", DifyJsonSchema.asString(userId));
        putIfPresentNumber(out, "height", src.get("height"));
        putIfPresentNumber(out, "weight", src.get("weight"));
        putIfPresentNumber(out, "bmi", src.get("bmi"));
        putIfPresentNumber(out, "fasting_glucose", firstPresent(src, "fastingGlucose", "fasting_glucose"));
        putIfPresentNumber(out, "postprandial_glucose", firstPresent(src, "postprandialGlucose", "postprandial_glucose"));
        putIfPresentNumber(out, "hba1c", firstPresent(src, "hba1c", "hbA1c"));
        putIfPresentNumber(out, "systolic_bp", firstPresent(src, "systolicBp", "systolic_bp"));
        putIfPresentNumber(out, "diastolic_bp", firstPresent(src, "diastolicBp", "diastolic_bp"));
        Object age = firstPresent(src, "age");
        if (age != null) {
            out.put("age", DifyJsonSchema.asInteger(age));
        }
        Object gender = firstPresent(src, "gender");
        if (gender != null) {
            out.put("gender", DifyJsonSchema.asString(gender));
        }
        Object diabetesType = firstPresent(src, "diabetesType", "diabetes_type");
        if (diabetesType != null) {
            out.put("diabetes_type", diabetesTypeName(diabetesType));
        }
        Object exercise = firstPresent(src, "exerciseFreq", "exercise_freq", "activity_level");
        if (exercise != null) {
            out.put("activity_level", activityLevelName(exercise));
        }
        Object diet = firstPresent(src, "dietType", "diet_type", "diet_habit");
        if (diet != null) {
            out.put("diet_habit", DifyJsonSchema.asString(diet));
        }
        return out;
    }

    private static Map<String, Object> toDifyRiskProfile(String userId, Map<String, Object> risk) {
        Map<String, Object> src = DifyJsonSchema.asObject(risk);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user_id", DifyJsonSchema.asString(userId));
        Object riskLevel = firstPresent(src, "riskLevel", "risk_level");
        if (riskLevel != null) {
            out.put("risk_level", normalizeRiskLevel(riskLevel));
        }
        Object riskScore = firstPresent(src, "riskScore", "risk_score");
        if (riskScore != null) {
            out.put("risk_score", DifyJsonSchema.asInteger(riskScore));
        }
        List<String> factorTexts = extractRiskFactorTexts(src.get("factors"));
        if (!factorTexts.isEmpty()) {
            out.put("risk_factors", factorTexts);
            out.put("primary_risk", factorTexts.get(0));
            if (factorTexts.size() > 1) {
                out.put("secondary_risk", factorTexts.get(1));
            }
        }
        Object summary = firstPresent(src, "reportSummary", "report_summary");
        if (summary != null && !String.valueOf(summary).isBlank()) {
            out.put("report_summary", DifyJsonSchema.asString(summary));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractRiskFactorTexts(Object factors) {
        if (!(factors instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object desc = ((Map<String, Object>) map).get("description");
                Object name = ((Map<String, Object>) map).get("name");
                if (desc != null && !String.valueOf(desc).isBlank()) {
                    texts.add(String.valueOf(desc));
                } else if (name != null) {
                    texts.add(String.valueOf(name));
                }
            } else if (item != null) {
                texts.add(String.valueOf(item));
            }
        }
        return texts;
    }

    private static String diabetesTypeName(Object value) {
        if (value instanceof Number n) {
            return switch (n.intValue()) {
                case 1 -> "前期";
                case 2 -> "1型";
                case 3 -> "2型";
                case 4 -> "妊娠";
                case 0 -> "无";
                default -> "未知";
            };
        }
        return DifyJsonSchema.asString(value);
    }

    private static String activityLevelName(Object value) {
        if (value instanceof Number n) {
            return switch (n.intValue()) {
                case 1 -> "sedentary";
                case 2 -> "light";
                case 3 -> "moderate";
                case 4 -> "active";
                default -> "unknown";
            };
        }
        return DifyJsonSchema.asString(value);
    }

    private static String normalizeRiskLevel(Object value) {
        String raw = DifyJsonSchema.asString(value).trim().toLowerCase();
        if (raw.contains("高") || "high".equals(raw)) {
            return "high";
        }
        if (raw.contains("中") || "medium".equals(raw)) {
            return "medium";
        }
        if (raw.contains("低") || "low".equals(raw)) {
            return "low";
        }
        return raw.isBlank() ? "unknown" : raw;
    }

    private static void putIfPresentNumber(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        target.put(key, DifyJsonSchema.asNumber(value));
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toSchemaCompliantCandidates(List<Map<String, Object>> candidates) {
        if (candidates == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("article_id", DifyJsonSchema.asString(c.get("article_id")));
            item.put("title", DifyJsonSchema.asString(c.get("title")));
            item.put("summary", DifyJsonSchema.asString(c.get("summary")));
            item.put("category", categorySlug(c.get("category")));
            Object tags = c.get("tags");
            item.put("tags", tags instanceof List ? tags : List.of());
            item.put("score", Math.round(DifyJsonSchema.asNumber(c.get("score"))));
            result.add(item);
        }
        return result;
    }
}
