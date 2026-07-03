package com.diabetes.home.stt;

import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionTaskResult;
import com.alibaba.dashscope.utils.Constants;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.home.config.DashScopeSttProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

/**
 * 阿里云百炼 Fun-ASR 录音文件识别客户端。
 * 文档：https://help.aliyun.com/zh/model-studio/fun-asr-recorded-speech-recognition-java-sdk
 */
@Component
public class FunAsrTranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(FunAsrTranscriptionClient.class);

    private final DashScopeSttProperties properties;
    private final ObjectMapper objectMapper;

    public FunAsrTranscriptionClient(DashScopeSttProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String transcribe(String fileUrl, String[] languageHints) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(500, "语音识别服务未配置（缺少 DASHSCOPE_API_KEY）");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BusinessException(500, "音频 URL 无效");
        }

        applyBaseHttpApiUrl();

        TranscriptionParam param = TranscriptionParam.builder()
                .apiKey(apiKey)
                .model(properties.getModel())
                .fileUrls(List.of(fileUrl))
                .parameter("language_hints", languageHints != null && languageHints.length > 0
                        ? languageHints
                        : properties.getLanguageHints().toArray(String[]::new))
                .build();
        try {
            Transcription transcription = new Transcription();
            TranscriptionResult result = transcription.asyncCall(param);
            log.info("Fun-ASR 提交成功: requestId={}, taskId={}", result.getRequestId(), result.getTaskId());

            result = transcription.wait(
                    TranscriptionQueryParam.FromTranscriptionParam(param, result.getTaskId()));

            List<TranscriptionTaskResult> taskResults = result.getResults();
            if (taskResults == null || taskResults.isEmpty()) {
                throw new BusinessException(500, "语音识别未返回结果");
            }

            TranscriptionTaskResult taskResult = taskResults.get(0);
            String transcriptionUrl = taskResult.getTranscriptionUrl();
            if (transcriptionUrl == null || transcriptionUrl.isBlank()) {
                throw new BusinessException(500, "语音识别任务失败");
            }

            return fetchTranscriptText(transcriptionUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fun-ASR 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(500, "语音识别失败: " + e.getMessage());
        }
    }

    private void applyBaseHttpApiUrl() {
        String baseUrl = properties.getBaseHttpApiUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            Constants.baseHttpApiUrl = baseUrl.trim();
        }
    }

    private String fetchTranscriptText(String transcriptionUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(transcriptionUrl).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(60_000);
        connection.connect();

        try (InputStream inputStream = connection.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            JsonNode transcripts = root.path("transcripts");
            if (transcripts.isArray() && !transcripts.isEmpty()) {
                String text = transcripts.get(0).path("text").asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
            String fallback = root.path("text").asText("").trim();
            if (!fallback.isBlank()) {
                return fallback;
            }
            throw new BusinessException(500, "语音识别结果为空");
        } finally {
            connection.disconnect();
        }
    }
}
