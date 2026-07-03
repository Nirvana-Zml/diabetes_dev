package com.diabetes.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "dashscope.stt")
public class DashScopeSttProperties {

    /** 阿里云百炼 API Key（环境变量 DASHSCOPE_API_KEY） */
    private String apiKey = "";

    /** 模型名称，默认 fun-asr */
    private String model = "fun-asr";

    /**
     * DashScope HTTP API 根地址。
     * 北京地域默认：https://dashscope.aliyuncs.com/api/v1
     */
    private String baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";

    private String defaultLanguage = "zh-CN";

    /** language_hints 参数，默认 zh + en */
    private List<String> languageHints = new ArrayList<>(List.of("zh", "en"));

    private long maxAudioBytes = 5_242_880L;

    /**
     * 供 Fun-ASR 拉取音频的公网 URL 前缀（须阿里云可访问）。
     * 默认与 MinIO publicBaseUrl 一致，生产环境请配置为公网域名或 ngrok 地址。
     */
    private String audioPublicBaseUrl = "http://localhost:9000";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseHttpApiUrl() { return baseHttpApiUrl; }
    public void setBaseHttpApiUrl(String baseHttpApiUrl) { this.baseHttpApiUrl = baseHttpApiUrl; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public void setDefaultLanguage(String defaultLanguage) { this.defaultLanguage = defaultLanguage; }
    public List<String> getLanguageHints() { return languageHints; }
    public void setLanguageHints(List<String> languageHints) { this.languageHints = languageHints; }
    public long getMaxAudioBytes() { return maxAudioBytes; }
    public void setMaxAudioBytes(long maxAudioBytes) { this.maxAudioBytes = maxAudioBytes; }
    public String getAudioPublicBaseUrl() { return audioPublicBaseUrl; }
    public void setAudioPublicBaseUrl(String audioPublicBaseUrl) { this.audioPublicBaseUrl = audioPublicBaseUrl; }
}
