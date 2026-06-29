package com.diabetes.article.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 推荐候选资讯（含标签与热度） */
public class ArticleCandidate {

    private String articleId;
    private String title;
    private String summary;
    private String coverImageId;
    private Integer category;
    private Integer viewCount;
    private Integer favoriteCount;
    private LocalDateTime publishedAt;
    private List<String> tags = new ArrayList<>();
    private String textFingerprint;

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCoverImageId() { return coverImageId; }
    public void setCoverImageId(String coverImageId) { this.coverImageId = coverImageId; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public String getTextFingerprint() { return textFingerprint; }
    public void setTextFingerprint(String textFingerprint) { this.textFingerprint = textFingerprint; }
}
