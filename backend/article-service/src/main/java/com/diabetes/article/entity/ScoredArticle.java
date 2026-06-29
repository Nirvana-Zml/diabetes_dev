package com.diabetes.article.entity;

public class ScoredArticle {

    private final ArticleCandidate candidate;
    private double score;
    private String reason;
    private int phase;

    public ScoredArticle(ArticleCandidate candidate) {
        this.candidate = candidate;
    }

    public ArticleCandidate getCandidate() { return candidate; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public void addScore(double delta) { this.score += delta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getPhase() { return phase; }
    public void setPhase(int phase) { this.phase = phase; }
}
