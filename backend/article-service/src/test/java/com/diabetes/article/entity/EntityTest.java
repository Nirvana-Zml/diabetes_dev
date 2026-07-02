package com.diabetes.article.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void article_gettersAndSetters() {
        Article article = new Article();
        LocalDateTime now = LocalDateTime.now();
        article.setArticleId("art_1");
        article.setTitle("标题");
        article.setContent("内容");
        article.setSummary("摘要");
        article.setCoverImageId("art_1.jpg");
        article.setCategory(2);
        article.setStatus(3);
        article.setRejectReason("原因");
        article.setViewCount(10);
        article.setFavoriteCount(2);
        article.setPublishedAt(now);
        article.setCreatedAt(now);
        article.setUpdatedAt(now);
        article.setDelFlag(0);

        assertEquals("art_1", article.getArticleId());
        assertEquals("标题", article.getTitle());
        assertEquals("内容", article.getContent());
        assertEquals("摘要", article.getSummary());
        assertEquals("art_1.jpg", article.getCoverImageId());
        assertEquals(2, article.getCategory());
        assertEquals(3, article.getStatus());
        assertEquals("原因", article.getRejectReason());
        assertEquals(10, article.getViewCount());
        assertEquals(2, article.getFavoriteCount());
        assertEquals(now, article.getPublishedAt());
        assertEquals(now, article.getCreatedAt());
        assertEquals(now, article.getUpdatedAt());
        assertEquals(0, article.getDelFlag());
    }

    @Test
    void articleCandidate_tagsDefaultAndScoredArticleMutations() {
        ArticleCandidate candidate = new ArticleCandidate();
        candidate.setArticleId("art_2");
        candidate.setTitle("标题2");
        candidate.setSummary("摘要2");
        candidate.setCoverImageId("http://example.com/cover.jpg");
        candidate.setCategory(3);
        candidate.setViewCount(5);
        candidate.setFavoriteCount(1);
        candidate.setPublishedAt(LocalDateTime.now());
        candidate.setTags(List.of("运动", "康复"));
        candidate.setTextFingerprint("fingerprint");

        assertEquals(List.of("运动", "康复"), candidate.getTags());
        candidate.setTags(null);
        assertNotNull(candidate.getTags());
        assertTrue(candidate.getTags().isEmpty());

        ScoredArticle scored = new ScoredArticle(candidate);
        assertSame(candidate, scored.getCandidate());
        scored.setScore(12.5);
        scored.addScore(2.5);
        scored.setReason("推荐原因");
        scored.setPhase(3);

        assertEquals(15.0, scored.getScore());
        assertEquals("推荐原因", scored.getReason());
        assertEquals(3, scored.getPhase());
    }
}
