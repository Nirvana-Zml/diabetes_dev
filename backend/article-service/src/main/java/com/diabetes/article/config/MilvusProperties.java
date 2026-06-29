package com.diabetes.article.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 19530;
    private String collection = "article_knowledge";
    private int dimension = 384;
    private String metricType = "COSINE";
    private String indexType = "IVF_FLAT";
    private int indexNlist = 128;
    private boolean syncOnStartup = true;

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
    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }
    public int getIndexNlist() { return indexNlist; }
    public void setIndexNlist(int indexNlist) { this.indexNlist = indexNlist; }
    public boolean isSyncOnStartup() { return syncOnStartup; }
    public void setSyncOnStartup(boolean syncOnStartup) { this.syncOnStartup = syncOnStartup; }
    public Embedding getEmbedding() { return embedding; }

    public static class Embedding {
        /** local=本地哈希向量；openai=OpenAI 兼容 Embedding API */
        private String provider = "local";
        private String openaiBaseUrl = "";
        private String openaiApiKey = "";
        private String openaiModel = "text-embedding-3-small";

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
