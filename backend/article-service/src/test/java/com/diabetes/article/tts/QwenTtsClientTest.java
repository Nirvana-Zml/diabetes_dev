package com.diabetes.article.tts;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QwenTtsClientTest {

    @Test
    void resolveVoice_defaultsToCherry() {
        assertEquals(AudioParameters.Voice.CHERRY, QwenTtsClient.resolveVoice(null));
        assertEquals(AudioParameters.Voice.CHERRY, QwenTtsClient.resolveVoice(" "));
        assertEquals(AudioParameters.Voice.CHERRY, QwenTtsClient.resolveVoice("unknown"));
    }

    @Test
    void resolveVoice_parsesEnumName() {
        assertEquals(AudioParameters.Voice.CHERRY, QwenTtsClient.resolveVoice("cherry"));
    }

    @Test
    void mapErrorMessage_quotaExhausted() {
        BusinessException ex = QwenTtsClient.mapErrorMessage(
                "{\"statusCode\":403,\"code\":\"AllocationQuota.FreeTierOnly\",\"message\":\"free quota\"}");
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("额度已用完"));
    }

    @Test
    void mapErrorMessage_truncatesLongRawMessage() {
        BusinessException ex = QwenTtsClient.mapErrorMessage("x".repeat(200));
        assertEquals(500, ex.getCode());
        assertEquals("语音合成失败，请稍后重试", ex.getMessage());
    }
}
