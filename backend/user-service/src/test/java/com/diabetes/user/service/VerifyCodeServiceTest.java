package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerifyCodeServiceTest {

    private VerifyCodeService verifyCodeService;

    @BeforeEach
    void setUp() {
        verifyCodeService = new VerifyCodeService();
    }

    @Test
    void generateAndStore_returnsSixDigitCode() {
        String code = verifyCodeService.generateAndStore("13800138000");
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void verifyOrThrow_success() {
        String code = verifyCodeService.generateAndStore("user@example.com");
        assertDoesNotThrow(() -> verifyCodeService.verifyOrThrow("user@example.com", code));
    }

    @Test
    void verifyOrThrow_normalizesKeyCase() {
        String code = verifyCodeService.generateAndStore("User@Example.COM");
        assertDoesNotThrow(() -> verifyCodeService.verifyOrThrow("user@example.com", code));
    }

    @Test
    void verifyOrThrow_noCode() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifyCodeService.verifyOrThrow("unknown@test.com", "123456"));
        assertEquals(400, ex.getCode());
        assertEquals("验证码错误或已过期", ex.getMessage());
    }

    @Test
    void verifyOrThrow_wrongCode() {
        verifyCodeService.generateAndStore("13800138001");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifyCodeService.verifyOrThrow("13800138001", "000000"));
        assertEquals(400, ex.getCode());
        assertEquals("验证码错误", ex.getMessage());
    }

    @Test
    void verifyOrThrow_expiredCode() throws Exception {
        String account = "13800138002";
        String code = verifyCodeService.generateAndStore(account);
        expireCode(account, code);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> verifyCodeService.verifyOrThrow(account, code));
        assertEquals(400, ex.getCode());
        assertEquals("验证码已过期，请重新获取", ex.getMessage());
    }

    @Test
    void remove_clearsCode() {
        verifyCodeService.generateAndStore("13800138003");
        verifyCodeService.remove("13800138003");
        assertThrows(BusinessException.class,
                () -> verifyCodeService.verifyOrThrow("13800138003", "123456"));
    }

    @Test
    void generateAndStore_nullAccount() {
        String code = verifyCodeService.generateAndStore(null);
        assertNotNull(code);
        assertDoesNotThrow(() -> verifyCodeService.verifyOrThrow(null, code));
    }

    @SuppressWarnings("unchecked")
    private void expireCode(String account, String code) throws Exception {
        Field field = VerifyCodeService.class.getDeclaredField("codes");
        field.setAccessible(true);
        Map<String, Object> codes = (Map<String, Object>) field.get(verifyCodeService);
        String key = account == null ? "" : account.trim().toLowerCase();
        Class<?> entryClass = Class.forName("com.diabetes.user.service.VerifyCodeService$CodeEntry");
        var ctor = entryClass.getDeclaredConstructor(String.class, long.class);
        ctor.setAccessible(true);
        codes.put(key, ctor.newInstance(code, System.currentTimeMillis() - 6 * 60 * 1000L));
    }
}
