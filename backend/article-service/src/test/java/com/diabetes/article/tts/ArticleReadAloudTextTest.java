package com.diabetes.article.tts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleReadAloudTextTest {

    @Test
    void prepare_stripsMarkdownAndPrefixesTitle() {
        String text = ArticleReadAloudText.prepare(
                "糖尿病饮食指南",
                "## 要点\n\n请**控制**碳水，![图](http://x.jpg)详见[链接](http://a.com)。"
        );
        assertTrue(text.startsWith("糖尿病饮食指南。"));
        assertTrue(text.contains("要点"));
        assertTrue(text.contains("控制碳水"));
        assertTrue(text.contains("链接"));
        assertFalse(text.contains("##"));
        assertFalse(text.contains("**"));
    }

    @Test
    void chunk_splitsLongTextOnSentences() {
        String paragraph = "第一句内容。".repeat(60);
        List<String> chunks = ArticleReadAloudText.chunk(paragraph, 100);
        assertTrue(chunks.size() > 1);
        chunks.forEach(chunk -> assertTrue(chunk.length() <= 100));
    }

    @Test
    void chunk_returnsEmptyForBlank() {
        assertTrue(ArticleReadAloudText.chunk("  ", 400).isEmpty());
    }

    @Test
    void chunk_handlesOversizedSentence() {
        String longSentence = "长".repeat(500);
        List<String> chunks = ArticleReadAloudText.chunk(longSentence, 100);
        assertEquals(5, chunks.size());
        assertEquals(100, chunks.get(0).length());
    }
}
