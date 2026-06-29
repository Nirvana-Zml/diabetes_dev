package com.diabetes.checkin.service;

/**
 * MinIO 打卡图片 Object Key 规范：
 * <ul>
 *   <li>系统预设：{@code food/{presetId}.jpg}、{@code medical/{presetId}.jpg}</li>
 *   <li>用户上传：{@code food/{userId}/upload_{yyyyMMddHHmmss}_{suffix}.jpg}</li>
 * </ul>
 */
final class CheckinImagePathHelper {

    private CheckinImagePathHelper() {
    }

    static boolean isValidPresetKey(String key, String folder) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String prefix = folder + "/";
        if (!key.startsWith(prefix) || !key.endsWith(".jpg")) {
            return false;
        }
        String rest = key.substring(prefix.length());
        return !rest.contains("/");
    }

    static boolean isValidUserUploadKey(String key, String folder, String userId) {
        if (key == null || key.isBlank() || userId == null || userId.isBlank()) {
            return false;
        }
        String prefix = folder + "/" + userId + "/upload_";
        return key.startsWith(prefix) && key.endsWith(".jpg") && key.length() > prefix.length() + 4;
    }

    static String normalize(String key) {
        return key == null ? "" : key.replaceAll("^/+", "");
    }
}
