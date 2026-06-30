package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VerifyCodeService {

    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public String generateAndStore(String account) {
        String key = normalizeKey(account);
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        codes.put(key, new CodeEntry(code, System.currentTimeMillis()));
        return code;
    }

    public void verifyOrThrow(String account, String code) {
        String key = normalizeKey(account);
        CodeEntry entry = codes.get(key);
        if (entry == null) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        if (System.currentTimeMillis() - entry.createdAt() > CODE_TTL_MS) {
            codes.remove(key);
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }
        if (!entry.code().equals(code)) {
            throw new BusinessException(400, "验证码错误");
        }
    }

    public void remove(String account) {
        codes.remove(normalizeKey(account));
    }

    private String normalizeKey(String account) {
        return account == null ? "" : account.trim().toLowerCase();
    }

    private record CodeEntry(String code, long createdAt) {}
}
