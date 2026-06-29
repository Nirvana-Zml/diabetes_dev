package com.diabetes.common.client;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 医生咨询模块占位客户端：供其他微服务调用，返回空数据结构。
 */
@Component
public class ConsultationServiceClient {

    public List<AiDoctorPlaceholder> listAiDoctors(String department, String keyword) {
        return Collections.emptyList();
    }

    public Optional<SessionPlaceholder> getActiveSession(String userId) {
        return Optional.empty();
    }

    public record AiDoctorPlaceholder(String doctorId, String name, String department) {}

    public record SessionPlaceholder(String sessionId, String doctorId, String status) {}
}
