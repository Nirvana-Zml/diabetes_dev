package com.diabetes.article.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将资讯 Markdown 转为适合 TTS 朗读的纯文本，并按 API 长度限制分段。
 */
public final class ArticleReadAloudText {

    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern BOLD_ITALIC = Pattern.compile("\\*{1,3}([^*]+)\\*{1,3}");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ArticleReadAloudText() {
    }

    public static String prepare(String title, String markdown) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim()).append('。');
        }
        if (markdown != null && !markdown.isBlank()) {
            sb.append(stripMarkdown(markdown));
        }
        return normalizeWhitespace(sb.toString());
    }

    public static List<String> chunk(String text, int maxChunkChars) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int limit = maxChunkChars > 0 ? maxChunkChars : 400;
        if (text.length() <= limit) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : splitSentences(text)) {
            if (sentence.length() > limit) {
                flushChunk(chunks, current);
                for (int i = 0; i < sentence.length(); i += limit) {
                    chunks.add(sentence.substring(i, Math.min(i + limit, sentence.length())));
                }
                continue;
            }
            if (current.length() + sentence.length() > limit) {
                flushChunk(chunks, current);
            }
            current.append(sentence);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private static void flushChunk(List<String> chunks, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        chunks.add(current.toString());
        current.setLength(0);
    }

    private static List<String> splitSentences(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                parts.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (!sb.isEmpty()) {
            parts.add(sb.toString());
        }
        return parts;
    }

    static String stripMarkdown(String markdown) {
        String text = markdown;
        text = CODE_BLOCK.matcher(text).replaceAll(" ");
        text = IMAGE.matcher(text).replaceAll(" ");
        text = LINK.matcher(text).replaceAll("$1");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = HEADING.matcher(text).replaceAll("");
        text = BOLD_ITALIC.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = text.replace('-', ' ').replace('*', ' ').replace('_', ' ');
        return normalizeWhitespace(text);
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text.trim()).replaceAll("");
    }
}
