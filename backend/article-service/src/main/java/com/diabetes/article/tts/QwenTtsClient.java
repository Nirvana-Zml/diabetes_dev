package com.diabetes.article.tts;

import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.utils.Constants;
import com.diabetes.article.config.DashScopeTtsProperties;
import com.diabetes.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * 阿里云百炼 qwen3-tts-flash 非流式（blocking）语音合成客户端。
 * 文档：https://help.aliyun.com/zh/model-studio/qwen-tts-api
 */
@Component
public class QwenTtsClient {

    private static final Logger log = LoggerFactory.getLogger(QwenTtsClient.class);

    private final DashScopeTtsProperties properties;

    public QwenTtsClient(DashScopeTtsProperties properties) {
        this.properties = properties;
    }

    /**
     * 合成单段文本，返回 WAV 二进制（从 DashScope 临时 OSS URL 下载）。
     */
    public byte[] synthesize(String text) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(500, "语音朗读服务未配置（缺少 DASHSCOPE_API_KEY）");
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(400, "朗读文本不能为空");
        }

        applyBaseHttpApiUrl();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .model(properties.getModel())
                .apiKey(apiKey)
                .text(text.trim())
                .voice(resolveVoice(properties.getVoice()))
                .languageType(properties.getLanguageType())
                .build();

        try {
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);
            if (result == null || result.getOutput() == null || result.getOutput().getAudio() == null) {
                throw new BusinessException(500, "语音合成未返回音频");
            }
            String audioUrl = result.getOutput().getAudio().getUrl();
            if (audioUrl == null || audioUrl.isBlank()) {
                throw new BusinessException(500, "语音合成未返回音频 URL");
            }
            log.debug("TTS 合成成功: requestId={}, chars={}", result.getRequestId(), text.length());
            return downloadAudio(audioUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (ApiException e) {
            log.error("TTS 合成失败: {}", e.getMessage());
            throw mapApiException(e);
        } catch (Exception e) {
            log.error("TTS 合成失败: {}", e.getMessage(), e);
            throw mapGenericException(e);
        }
    }

    static BusinessException mapApiException(ApiException e) {
        return mapErrorMessage(e.getMessage());
    }

    static BusinessException mapGenericException(Exception e) {
        return mapErrorMessage(e.getMessage());
    }

    static BusinessException mapErrorMessage(String raw) {
        String message = raw != null ? raw : "";
        if (message.contains("AllocationQuota")
                || message.contains("FreeTierOnly")
                || message.contains("free quota")) {
            return new BusinessException(503,
                    "语音朗读额度已用完，请在阿里云百炼控制台开通付费，或关闭「仅使用免费额度」模式");
        }
        if (message.contains("InvalidApiKey") || message.contains("\"statusCode\":401")) {
            return new BusinessException(503, "语音朗读服务密钥无效，请检查 DASHSCOPE_API_KEY");
        }
        if (message.length() > 160) {
            return new BusinessException(500, "语音合成失败，请稍后重试");
        }
        return new BusinessException(500, "语音合成失败: " + message);
    }

    private void applyBaseHttpApiUrl() {
        String baseUrl = properties.getBaseHttpApiUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            Constants.baseHttpApiUrl = baseUrl.trim();
        }
    }

    static AudioParameters.Voice resolveVoice(String name) {
        if (name == null || name.isBlank()) {
            return AudioParameters.Voice.CHERRY;
        }
        try {
            return AudioParameters.Voice.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AudioParameters.Voice.CHERRY;
        }
    }

    private byte[] downloadAudio(String audioUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(audioUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(120_000);
        connection.connect();

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length == 0) {
                throw new BusinessException(500, "语音文件下载为空");
            }
            return bytes;
        } finally {
            connection.disconnect();
        }
    }
}
