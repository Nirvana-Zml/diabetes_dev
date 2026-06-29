package com.diabetes.article.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommend")
public class RecommendProperties {

    private boolean phase1Enabled = true;
    private boolean phase2Enabled = true;
    private int phase2MinCoReaders = 1;
    private boolean phase3Enabled = true;
    private boolean milvusEnabled = false;
    private boolean phase4DifyEnabled = true;
    private int cacheTtlMinutes = 20;
    private int difyTopN = 20;
    private int candidateLimit = 200;

    public boolean isPhase1Enabled() { return phase1Enabled; }
    public void setPhase1Enabled(boolean phase1Enabled) { this.phase1Enabled = phase1Enabled; }
    public boolean isPhase2Enabled() { return phase2Enabled; }
    public void setPhase2Enabled(boolean phase2Enabled) { this.phase2Enabled = phase2Enabled; }
    public int getPhase2MinCoReaders() { return phase2MinCoReaders; }
    public void setPhase2MinCoReaders(int phase2MinCoReaders) { this.phase2MinCoReaders = phase2MinCoReaders; }
    public boolean isPhase3Enabled() { return phase3Enabled; }
    public void setPhase3Enabled(boolean phase3Enabled) { this.phase3Enabled = phase3Enabled; }
    public boolean isMilvusEnabled() { return milvusEnabled; }
    public void setMilvusEnabled(boolean milvusEnabled) { this.milvusEnabled = milvusEnabled; }
    public boolean isPhase4DifyEnabled() { return phase4DifyEnabled; }
    public void setPhase4DifyEnabled(boolean phase4DifyEnabled) { this.phase4DifyEnabled = phase4DifyEnabled; }
    public int getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
    public int getDifyTopN() { return difyTopN; }
    public void setDifyTopN(int difyTopN) { this.difyTopN = difyTopN; }
    public int getCandidateLimit() { return candidateLimit; }
    public void setCandidateLimit(int candidateLimit) { this.candidateLimit = candidateLimit; }
}
