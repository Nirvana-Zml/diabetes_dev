package com.diabetes.home.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.home.config.DashScopeSttProperties;
import com.diabetes.home.config.QaChatProperties;
import com.diabetes.home.dto.VoiceTranscriptionResult;
import com.diabetes.home.stt.FunAsrTranscriptionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoiceSttServiceTest {

    private DashScopeSttProperties properties;
    private FunAsrTranscriptionClient funAsrClient;
    private MinioStorageService minioStorageService;
    private VoiceSttService service;

    @BeforeEach
    void setUp() {
        properties = new DashScopeSttProperties();
        properties.setApiKey("sk-test");
        properties.setDefaultLanguage("zh-CN");
        properties.setMaxAudioBytes(1024 * 1024);
        properties.setAudioPublicBaseUrl("http://minio.local");

        funAsrClient = mock(FunAsrTranscriptionClient.class);
        minioStorageService = mock(MinioStorageService.class);

        QaChatProperties qaProps = new QaChatProperties();
        qaProps.setQueryMaxLength(500);

        service = new VoiceSttService(properties, funAsrClient, minioStorageService, qaProps);
    }

    @Test
    void transcribe_success() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.wav", "audio/wav", "fake-audio".getBytes());
        when(minioStorageService.uploadSttTempAudio(any(), eq(10L), eq("voice.wav"), eq("audio/wav"), eq("http://minio.local")))
                .thenReturn(new MinioStorageService.SttAudioUploadResult("temp/u1/voice.wav", "http://minio.local/stt/temp/u1/voice.wav"));
        when(funAsrClient.transcribe(eq("http://minio.local/stt/temp/u1/voice.wav"), eq(new String[]{"zh", "en"})))
                .thenReturn("你好");

        VoiceTranscriptionResult result = service.transcribe(audio, "u1", null);

        assertEquals("你好", result.text());
        assertEquals("zh-CN", result.language());
        verify(minioStorageService).deleteSttTempAudio("temp/u1/voice.wav");
    }

    @Test
    void transcribe_rejectsWebm() {
        MockMultipartFile webm = new MockMultipartFile("audio", "voice.webm", "audio/webm", "x".getBytes());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.transcribe(webm, "u1", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void transcribe_rejectsEmptyAndOversizedFiles() {
        assertThrows(BusinessException.class, () -> service.transcribe(null, "u1", null));

        MockMultipartFile empty = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[0]);
        assertThrows(BusinessException.class, () -> service.transcribe(empty, "u1", null));

        properties.setMaxAudioBytes(4);
        MockMultipartFile large = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[8]);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.transcribe(large, "u1", null));
        assertEquals(400, ex.getCode());
    }

    @Test
    void getSttSpec_containsProvider() {
        assertEquals("dashscope-fun-asr", service.getSttSpec().get("provider"));
        assertEquals("fun-asr", service.getSttSpec().get("model"));
    }
}
