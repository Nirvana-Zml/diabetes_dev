package com.diabetes.home.stt;

import com.diabetes.common.exception.BusinessException;

/** 识别文本校验（原 Dify 代码节点 validateSttText 的 Java 实现） */
public final class SttTextValidator {

    private SttTextValidator() {
    }

    public static String validateAndNormalize(String rawText, int maxLength, int minLength) {
        if (rawText == null) {
            throw validationError("empty_text", "未识别到有效文字，请重新录制");
        }

        String text = rawText.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            throw validationError("empty_text", "未识别到有效文字，请重新录制");
        }
        if (text.length() < minLength) {
            throw validationError("text_too_short", "识别内容过短，请重新录制");
        }
        if (text.length() > maxLength) {
            throw validationError("text_too_long", "识别内容过长，请控制在 " + maxLength + " 字以内");
        }

        String contentWithoutPunct = text.replaceAll("[\\s\\p{P}\\p{S}]", "");
        if (contentWithoutPunct.isEmpty()) {
            throw validationError("invalid_content", "未识别到有效内容，请重新录制");
        }
        if (contentWithoutPunct.matches("(.)\\1{4,}")) {
            throw validationError("invalid_content", "识别结果异常，请重新录制");
        }
        return text;
    }

    private static BusinessException validationError(String errorType, String message) {
        return new BusinessException(400, message);
    }
}
