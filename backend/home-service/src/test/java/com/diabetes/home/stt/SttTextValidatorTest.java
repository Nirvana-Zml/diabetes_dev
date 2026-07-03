package com.diabetes.home.stt;

import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SttTextValidatorTest {

    @Test
    void validatesRecognizedText() {
        assertEquals("你好 世界", SttTextValidator.validateAndNormalize("  你好  世界  ", 500, 2));
    }

    @Test
    void rejectsEmptyText() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SttTextValidator.validateAndNormalize("   ", 500, 2));
        assertEquals(400, ex.getCode());
    }

    @Test
    void rejectsTooShortText() {
        assertThrows(BusinessException.class,
                () -> SttTextValidator.validateAndNormalize("你", 500, 2));
    }
}
