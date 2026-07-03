package com.diabetes.article.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dashscope.tts")
public class DashScopeTtsProperties {

    /** 是否启用资讯朗读（需配置 DASHSCOPE_API_KEY） */
    private boolean enabled = true;

    /** 阿里云百炼 API Key（可与 STT 共用 DASHSCOPE_API_KEY） */
    private String apiKey = "";

    /** 模型名称，默认 qwen3-tts-flash（非流式 blocking 调用） */
    private String model = "qwen3-tts-flash";

    /**
     * DashScope HTTP API 根地址。
     * 北京地域默认：https://dashscope.aliyuncs.com/api/v1
     */
    private String baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";

    /** 音色，参见 AudioParameters.Voice，如 CHERRY、SERENA */
    private String voice = "CHERRY";

    /** 合成语种，中文资讯固定 Chinese */
    private String languageType = "Chinese";

    /** 单次请求最大字符数（qwen3-tts-flash 上限约 512 Token，保守取 400 字） */
    private int maxChunkChars = 400;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseHttpApiUrl() { return baseHttpApiUrl; }
    public void setBaseHttpApiUrl(String baseHttpApiUrl) { this.baseHttpApiUrl = baseHttpApiUrl; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getLanguageType() { return languageType; }
    public void setLanguageType(String languageType) { this.languageType = languageType; }
    public int getMaxChunkChars() { return maxChunkChars; }
    public void setMaxChunkChars(int maxChunkChars) { this.maxChunkChars = maxChunkChars; }
}
