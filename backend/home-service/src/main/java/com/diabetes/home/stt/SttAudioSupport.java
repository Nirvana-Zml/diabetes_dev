package com.diabetes.home.stt;

import java.util.List;
import java.util.Locale;

/** 科普助手语音识别 — 音频格式校验与文件名规范化 */
public final class SttAudioSupport {

    public static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "mp3", "m4a", "wav", "amr", "mpga", "mp4"
    );

    private static final String UNSUPPORTED_FORMAT_MESSAGE =
            "当前录音格式不受支持，请使用 MP3、M4A、WAV、AMR 或 MPGA";

    private SttAudioSupport() {
    }

    public static boolean isSupportedAudioMime(String mimeType, String filename) {
        String base = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        String ext = extensionOf(filename);
        if ("webm".equals(ext) || "ogg".equals(ext) || base.contains("webm") || base.contains("ogg")) {
            return false;
        }
        if (SUPPORTED_EXTENSIONS.contains(ext)) {
            return true;
        }
        return base.startsWith("audio/");
    }

    public static String unsupportedFormatMessage() {
        return UNSUPPORTED_FORMAT_MESSAGE;
    }

    public static String extensionOf(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static String normalizeFilename(String originalFilename) {
        if (originalFilename != null && !originalFilename.isBlank()) {
            return originalFilename;
        }
        return "voice.wav";
    }

    public static String normalizeContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType.split(";")[0].trim();
        }
        return switch (extensionOf(filename)) {
            case "wav" -> "audio/wav";
            case "mp3", "mpga" -> "audio/mpeg";
            case "m4a", "mp4" -> "audio/mp4";
            case "amr" -> "audio/amr";
            default -> "audio/wav";
        };
    }
}
