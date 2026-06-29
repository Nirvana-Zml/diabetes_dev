package com.diabetes.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge.milvus")
public class KnowledgeMilvusProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 19530;
    private String collection = "diabetes_knowledge";
    private int dimension = 1024;
    private String metricType = "COSINE";
    private int searchTopK = 5;
    private double scoreThreshold = 0.0;
    private final Embedding embedding = new Embedding();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public int getSearchTopK() { return searchTopK; }
    public void setSearchTopK(int searchTopK) { this.searchTopK = searchTopK; }
    public double getScoreThreshold() { return scoreThreshold; }
    public void setScoreThreshold(double scoreThreshold) { this.scoreThreshold = scoreThreshold; }
    public Embedding getEmbedding() { return embedding; }

    public static class Embedding {
        private String provider = "openai";
        private String openaiBaseUrl = "";
        private String openaiApiKey = "";
        private String openaiModel = "qwen3-embedding:0.6b";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getOpenaiBaseUrl() { return openaiBaseUrl; }
        public void setOpenaiBaseUrl(String openaiBaseUrl) { this.openaiBaseUrl = openaiBaseUrl; }
        public String getOpenaiApiKey() { return openaiApiKey; }
        public void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; }
        public String getOpenaiModel() { return openaiModel; }
        public void setOpenaiModel(String openaiModel) { this.openaiModel = openaiModel; }
    }
}
