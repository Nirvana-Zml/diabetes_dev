package com.diabetes.home.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.home.config.DashScopeSttProperties;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dto.VoiceTranscriptionResult;
import com.diabetes.home.stt.FunAsrTranscriptionClient;
import com.diabetes.home.stt.SttAudioSupport;
import com.diabetes.home.stt.SttTextValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VoiceSttService {

    private static final Logger log = LoggerFactory.getLogger(VoiceSttService.class);

    private final DashScopeSttProperties sttProperties;
    private final FunAsrTranscriptionClient funAsrClient;
    private final MinioStorageService minioStorageService;
    private final QaChatProperties qaChatProperties;

    public VoiceSttService(DashScopeSttProperties sttProperties,
                           FunAsrTranscriptionClient funAsrClient,
                           MinioStorageService minioStorageService,
                           QaChatProperties qaChatProperties) {
        this.sttProperties = sttProperties;
        this.funAsrClient = funAsrClient;
        this.minioStorageService = minioStorageService;
        this.qaChatProperties = qaChatProperties;
    }

    public Map<String, Object> getSttSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("provider", "dashscope-fun-asr");
        spec.put("model", sttProperties.getModel());
        spec.put("defaultLanguage", sttProperties.getDefaultLanguage());
        spec.put("languageHints", sttProperties.getLanguageHints());
        spec.put("supportedAudioExtensions", SttAudioSupport.SUPPORTED_EXTENSIONS);
        spec.put("maxAudioBytes", sttProperties.getMaxAudioBytes());
        return spec;
    }

    public VoiceTranscriptionResult transcribe(MultipartFile audio, String userId, String language) {
        String uid = userId == null || userId.isBlank() ? "guest" : userId;
        String lang = language == null || language.isBlank()
                ? sttProperties.getDefaultLanguage()
                : language.trim();

        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(400, "请上传音频文件");
        }

        String filename = SttAudioSupport.normalizeFilename(audio.getOriginalFilename());
        String contentType = SttAudioSupport.normalizeContentType(audio.getContentType(), filename);
        if (!SttAudioSupport.isSupportedAudioMime(contentType, filename)) {
            throw new BusinessException(400, SttAudioSupport.unsupportedFormatMessage());
        }

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (Exception e) {
            throw new BusinessException(400, "读取音频文件失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException(400, "音频文件不能为空");
        }
        if (bytes.length > sttProperties.getMaxAudioBytes()) {
            throw new BusinessException(400, "音频文件过大，请控制在 5MB 以内");
        }

        log.info("语音识别(Fun-ASR): user={}, size={}, filename={}, contentType={}",
                uid, bytes.length, filename, contentType);

        MinioStorageService.SttAudioUploadResult upload = minioStorageService.uploadSttTempAudio(
                new ByteArrayInputStream(bytes),
                bytes.length,
                filename,
                contentType,
                sttProperties.getAudioPublicBaseUrl()
        );

        try {
            String rawText = funAsrClient.transcribe(upload.publicUrl(), resolveLanguageHints(lang));
            String text = SttTextValidator.validateAndNormalize(
                    rawText,
                    qaChatProperties.getQueryMaxLength(),
                    2
            );
            return new VoiceTranscriptionResult(text, lang);
        } finally {
            minioStorageService.deleteSttTempAudio(upload.objectKey());
        }
    }

    private String[] resolveLanguageHints(String language) {
        if (language == null || language.isBlank()) {
            return sttProperties.getLanguageHints().toArray(String[]::new);
        }
        String normalized = language.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("zh")) {
            return new String[]{"zh", "en"};
        }
        if (normalized.startsWith("en")) {
            return new String[]{"en"};
        }
        return sttProperties.getLanguageHints().toArray(String[]::new);
    }
}
