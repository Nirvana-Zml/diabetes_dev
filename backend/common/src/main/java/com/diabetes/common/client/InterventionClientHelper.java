package com.diabetes.common.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主动健康干预 evaluate 触发辅助。
 */
public final class InterventionClientHelper {

    private InterventionClientHelper() {
    }

    public static void triggerEvaluate(UserServiceClient client, String difyKey,
                                       String userId, String trigger,
                                       Map<String, Object> context) {
        if (client == null || userId == null || userId.isBlank()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("trigger", trigger == null ? "manual_refresh" : trigger);
        body.put("context", context == null ? Map.of() : context);
        client.evaluateIntervention(difyKey, body);
    }
}
