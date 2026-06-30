package com.diabetes.consultation.service;

import com.diabetes.consultation.dify.DifyConsultationWorkflowContract;
import com.diabetes.consultation.entity.ConsultationMessage;
import com.diabetes.consultation.entity.ConsultationSession;
import com.diabetes.consultation.entity.Doctor;
import com.diabetes.consultation.mapper.ConsultationMessageMapper;
import com.diabetes.consultation.mapper.ConsultationSessionMapper;
import com.diabetes.consultation.mapper.DoctorMapper;
import com.diabetes.common.client.HealthServiceClient;
import com.diabetes.common.client.HomeServiceClient;
import com.diabetes.common.client.UserServiceClient;
import com.diabetes.common.dify.DifyClient;
import com.diabetes.common.dify.DifyJsonSchema;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.common.util.IdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConsultationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationService.class);
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String AI_UNAVAILABLE =
            "AI 医生暂不可用，请稍后再试。如有紧急情况，请及时前往正规医疗机构就诊。";
    private static final String EMPTY_KNOWLEDGE_CONTEXT =
            "【系统提示】当前未检索到与问题直接相关的指南片段。请结合患者档案、对话历史与通用糖尿病诊疗原则作答，并明确标注「基于通用医学知识」。";

    private final DoctorMapper doctorMapper;
    private final ConsultationSessionMapper sessionMapper;
    private final ConsultationMessageMapper messageMapper;
    private final MinioStorageService minioStorageService;
    private final HealthServiceClient healthServiceClient;
    private final UserServiceClient userServiceClient;
    private final HomeServiceClient homeServiceClient;
    private final DifyClient difyClient;
    private final ObjectMapper objectMapper;
    private final String difyApiKey;
    private final String difyBaseUrl;
    private final String difyResponseMode;
    private final String difyInternalKey;

    public ConsultationService(DoctorMapper doctorMapper,
                               ConsultationSessionMapper sessionMapper,
                               ConsultationMessageMapper messageMapper,
                               MinioStorageService minioStorageService,
                               HealthServiceClient healthServiceClient,
                               UserServiceClient userServiceClient,
                               HomeServiceClient homeServiceClient,
                               DifyClient difyClient,
                               ObjectMapper objectMapper,
                               @Value("${dify.base-url:http://localhost}") String difyBaseUrl,
                               @Value("${dify.workflows.consultation.api-key:}") String difyApiKey,
                               @Value("${dify.workflows.consultation.response-mode:blocking}") String difyResponseMode,
                               @Value("${dify-internal.key:}") String difyInternalKey) {
        this.doctorMapper = doctorMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.minioStorageService = minioStorageService;
        this.healthServiceClient = healthServiceClient;
        this.userServiceClient = userServiceClient;
        this.homeServiceClient = homeServiceClient;
        this.difyClient = difyClient;
        this.objectMapper = objectMapper;
        this.difyBaseUrl = difyBaseUrl;
        this.difyApiKey = difyApiKey;
        this.difyResponseMode = difyResponseMode == null ? "blocking" : difyResponseMode.trim();
        this.difyInternalKey = difyInternalKey;
    }

    public List<Map<String, Object>> listDoctors(String department, String keyword, String statusFilter) {
        Integer statusCode = mapStatusFilter(statusFilter);
        List<Doctor> doctors = doctorMapper.findAll(department, keyword, statusCode);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Doctor doctor : doctors) {
            result.add(toDoctorVo(doctor));
        }
        return result;
    }

    public List<String> listDepartments() {
        List<Doctor> doctors = doctorMapper.findAll(null, null, null);
        return doctors.stream()
                .map(Doctor::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public Map<String, Object> createSession(String userId, String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            throw new BusinessException(400, "请选择医生");
        }
        Doctor doctor = doctorMapper.findById(doctorId);
        if (doctor == null) {
            throw new BusinessException(404, "医生不存在");
        }

        String sessionId = IdGenerator.nextId("sess_");
        ConsultationSession session = new ConsultationSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setDoctorId(doctorId);
        session.setStatus(1);
        session.setTitle(doctor.getName() + " 医生咨询");
        sessionMapper.insert(session);
        doctorMapper.incrementConsultationCount(doctorId);

        String greeting = buildGreeting(doctor);
        ConsultationMessage welcome = saveMessage(sessionId, 2, doctorId, greeting, 1, null);
        sessionMapper.updateAfterMessage(sessionId, truncate(greeting, 500), 1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("session_id", sessionId);
        data.put("status", "active");
        data.put("startedAt", session.getStartedAt());
        data.put("started_at", session.getStartedAt());
        data.put("doctorName", doctor.getName());
        data.put("doctor_name", doctor.getName());
        data.put("welcomeMessage", toMessageVo(welcome));
        return data;
    }

    public Map<String, Object> listSessions(String userId, String status, int page, int size) {
        Integer statusCode = mapSessionStatusFilter(status);
        int offset = Math.max(0, (page - 1) * size);
        List<ConsultationSession> sessions = sessionMapper.findByUserId(userId, statusCode, offset, size);
        int total = sessionMapper.countByUserId(userId, statusCode);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ConsultationSession session : sessions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", session.getSessionId());
            item.put("session_id", session.getSessionId());
            item.put("doctorId", session.getDoctorId());
            item.put("doctor_id", session.getDoctorId());
            item.put("status", session.getStatus() != null && session.getStatus() == 1 ? "active" : "closed");
            item.put("lastMessage", session.getLastMessage());
            item.put("last_message", session.getLastMessage());
            item.put("messageCount", session.getMessageCount());
            item.put("message_count", session.getMessageCount());
            item.put("startedAt", session.getStartedAt());
            item.put("started_at", session.getStartedAt());
            item.put("endedAt", session.getEndedAt());
            item.put("ended_at", session.getEndedAt());
            item.put("rating", session.getRating());
            item.put("feedback", session.getFeedback());

            Doctor doctor = doctorMapper.findById(session.getDoctorId());
            if (doctor != null) {
                item.put("doctorName", doctor.getName());
                item.put("doctor_name", doctor.getName());
                item.put("doctorTitle", doctor.getTitle());
                item.put("doctor_title", doctor.getTitle());
                item.put("department", doctor.getDepartment());
            }
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessions", items);
        result.put("list", items);
        result.put("total", total);
        result.put("page", page);
        result.put("page_size", size);
        return result;
    }

    @Transactional
    public Map<String, Object> sendMessage(String userId, String sessionId, String content, String imageUrl) {
        ConsultationSession session = requireActiveSession(userId, sessionId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "消息内容不能为空");
        }

        ConsultationMessage userMsg = saveMessage(sessionId, 1, userId, content, 0, null);
        int count = messageMapper.countBySessionId(sessionId);
        sessionMapper.updateAfterMessage(sessionId, truncate(content, 500), count);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userMessage", toMessageVo(userMsg));
        result.put("messageId", userMsg.getMessageId());
        result.put("message_id", userMsg.getMessageId());
        result.put("senderType", "user");
        result.put("sender_type", "user");
        result.put("content", userMsg.getContent());
        result.put("sentAt", userMsg.getSentAt());
        result.put("sent_at", userMsg.getSentAt());

        Map<String, Object> aiReply = generateAiReply(userId, session, content);
        if (aiReply != null) {
            result.put("aiMessage", aiReply);
            result.put("ai_message", aiReply);
        }
        return result;
    }

    public Map<String, Object> listMessages(String userId, String sessionId, int page, int size) {
        requireSession(userId, sessionId);
        int offset = Math.max(0, (page - 1) * size);
        List<ConsultationMessage> messages = messageMapper.findBySessionId(sessionId, offset, size);
        int total = messageMapper.countBySessionId(sessionId);

        List<Map<String, Object>> items = messages.stream().map(this::toMessageVo).toList();
        return Map.of("messages", items, "total", total);
    }

    @Transactional
    public void closeSession(String userId, String sessionId, Integer rating, String feedback) {
        ConsultationSession session = requireSession(userId, sessionId);
        if (session.getStatus() != null && session.getStatus() == 2) {
            throw new BusinessException(409, "会话已关闭");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessException(400, "评分范围为 1-5");
        }
        int updated = sessionMapper.closeSession(sessionId, rating, feedback);
        if (updated == 0) {
            throw new BusinessException(409, "会话已关闭");
        }
    }

    public Map<String, Object> getAiSuggestion(String userId, String sessionId) {
        requireSession(userId, sessionId);
        ConsultationMessage latest = messageMapper.findLatestAiMessage(sessionId);
        if (latest == null || latest.getAiMetadata() == null || latest.getAiMetadata().isBlank()) {
            return emptySuggestion();
        }
        try {
            JsonNode node = objectMapper.readTree(latest.getAiMetadata());
            return mapSuggestion(node);
        } catch (JsonProcessingException e) {
            log.warn("解析 AI 建议元数据失败 sessionId={}", sessionId);
            return emptySuggestion();
        }
    }

    public Map<String, Object> getDifyWorkflowSpec() {
        return DifyConsultationWorkflowContract.workflowSpec(difyBaseUrl, difyApiKey, difyResponseMode);
    }

    public Map<String, Object> getActiveSession(String userId) {
        ConsultationSession session = sessionMapper.findActiveByUserId(userId);
        if (session == null) {
            return Map.of("session", Collections.emptyMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getSessionId());
        data.put("doctorId", session.getDoctorId());
        data.put("status", "active");
        data.put("startedAt", session.getStartedAt());
        return Map.of("session", data);
    }

    private Map<String, Object> generateAiReply(String userId, ConsultationSession session, String query) {
        Doctor doctor = doctorMapper.findById(session.getDoctorId());
        if (doctor == null) {
            return null;
        }

        String replyContent;
        String aiMetadata = null;

        if (difyApiKey == null || difyApiKey.isBlank()) {
            replyContent = AI_UNAVAILABLE;
        } else {
            try {
                Map<String, Object> doctorReply = callDifyWorkflow(userId, session, doctor, query);
                replyContent = stringValue(doctorReply.get("content"));
                if (replyContent == null || replyContent.isBlank()) {
                    replyContent = AI_UNAVAILABLE;
                } else {
                    Object suggestion = doctorReply.get("suggestion");
                    if (suggestion != null) {
                        aiMetadata = objectMapper.writeValueAsString(suggestion);
                    }
                }
            } catch (Exception e) {
                log.warn("Dify 问诊工作流调用失败 sessionId={} error={}", session.getSessionId(), e.getMessage());
                replyContent = AI_UNAVAILABLE;
            }
        }

        ConsultationMessage aiMsg = saveMessage(session.getSessionId(), 2, doctor.getDoctorId(),
                replyContent, 1, aiMetadata);
        int count = messageMapper.countBySessionId(session.getSessionId());
        sessionMapper.updateAfterMessage(session.getSessionId(), truncate(replyContent, 500), count);
        return toMessageVo(aiMsg);
    }

    private Map<String, Object> callDifyWorkflow(String userId,
                                                   ConsultationSession session,
                                                   Doctor doctor,
                                                   String query) throws JsonProcessingException {
        String patientProfile = buildPatientProfileJson(userId);
        String history = buildConversationHistory(session.getSessionId(), true);
        String doctorRole = buildDoctorRole(doctor);
        String knowledgeContext = resolveKnowledgeContext(query);

        Map<String, Object> payload = DifyConsultationWorkflowContract.buildInputObject(
                query,
                session.getSessionId(),
                doctorRole,
                patientProfile,
                history,
                knowledgeContext
        );
        log.info("调用 Dify 问诊工作流 sessionId={} queryLen={} historyLen={} knowledgeLen={}",
                session.getSessionId(), query.length(), history.length(), knowledgeContext.length());
        JsonNode response = difyClient.runWorkflowBlocking(
                difyApiKey, userId, DifyJsonSchema.flatWorkflowInputs(payload), difyResponseMode);
        assertWorkflowSucceeded(response);
        return parseDoctorReply(response);
    }

    private String buildPatientProfileJson(String userId) throws JsonProcessingException {
        Map<String, Object> userProfile = userServiceClient.getUserProfile(userId, difyInternalKey);
        if (!isHealthDataVisible(userProfile)) {
            return buildHiddenPatientProfileJson();
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        Map<String, Object> healthProfile = healthServiceClient.getLatestHealthProfile(userId, difyInternalKey);

        putIfPresent(profile, "age", userProfile.get("age"));
        putIfPresent(profile, "gender", userProfile.get("gender"));
        putIfPresent(profile, "nickname", userProfile.get("nickname"));
        putIfPresent(profile, "height", healthProfile.get("height"));
        putIfPresent(profile, "weight", healthProfile.get("weight"));
        putIfPresent(profile, "bmi", healthProfile.get("bmi"));
        putIfPresent(profile, "fastingGlucose", firstPresent(healthProfile, "fastingGlucose", "fasting_glucose"));
        putIfPresent(profile, "systolicBp", firstPresent(healthProfile, "systolicBp", "systolic_bp"));
        putIfPresent(profile, "diastolicBp", firstPresent(healthProfile, "diastolicBp", "diastolic_bp"));
        putIfPresent(profile, "diabetesType", firstPresent(healthProfile, "diabetesType", "diabetes_type"));
        putIfPresent(profile, "familyHistory", firstPresent(healthProfile, "familyHistory", "family_history"));

        if (profile.isEmpty()) {
            profile.put("note", "暂无健康档案，请根据用户描述进行问诊");
        }
        return objectMapper.writeValueAsString(profile);
    }

    private String buildHiddenPatientProfileJson() throws JsonProcessingException {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("data_visible", "不可见");
        profile.put("note", "用户已关闭健康档案可见性，请仅根据对话内容问诊，勿引用系统健康档案数据");
        return objectMapper.writeValueAsString(profile);
    }

    @SuppressWarnings("unchecked")
    private boolean isHealthDataVisible(Map<String, Object> userProfile) {
        Object privacySettings = firstPresent(userProfile, "privacy_settings", "privacySettings");
        if (!(privacySettings instanceof Map<?, ?> privacy)) {
            return true;
        }
        Object visible = firstPresent((Map<String, Object>) privacy, "data_visible", "dataVisible");
        if (visible == null) {
            return true;
        }
        if (visible instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(visible));
    }

    private String buildConversationHistory(String sessionId, boolean excludeLatest) {
        List<ConsultationMessage> recent = messageMapper.findRecentBySessionId(sessionId, excludeLatest ? 21 : 20);
        if (recent.isEmpty()) {
            return "";
        }
        if (excludeLatest) {
            recent = recent.subList(1, recent.size());
        }
        Collections.reverse(recent);
        StringBuilder sb = new StringBuilder();
        for (ConsultationMessage msg : recent) {
            String role = msg.getSenderType() != null && msg.getSenderType() == 2 ? "AI医生" : "用户";
            String time = msg.getSentAt() != null ? msg.getSentAt().format(HISTORY_TIME) : "";
            sb.append("[").append(role);
            if (!time.isBlank()) {
                sb.append(" ").append(time);
            }
            sb.append("] ").append(msg.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildDoctorRole(Doctor doctor) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(doctor.getName()).append("，").append(doctor.getTitle())
                .append("，").append(doctor.getDepartment()).append("，").append(doctor.getHospital()).append("。");
        if (doctor.getIntroduction() != null && !doctor.getIntroduction().isBlank()) {
            sb.append(doctor.getIntroduction());
        }
        if (doctor.getSpecialties() != null && !doctor.getSpecialties().isBlank()) {
            sb.append(" 擅长领域：").append(doctor.getSpecialties());
        }
        sb.append(" 请以专业、温和语气直接回复患者，使用 Markdown 组织建议，并在末尾提醒必要时线下就医。");
        return sb.toString();
    }

    private String buildGreeting(Doctor doctor) {
        return "您好，我是" + doctor.getName() + "医生（" + doctor.getTitle() + "，" + doctor.getDepartment()
                + "）。请问有什么可以帮您的？您可以描述症状或上传检查报告，我会尽力为您解答。";
    }

    private String resolveKnowledgeContext(String query) {
        String context = homeServiceClient.searchKnowledgeContext(query, 5, difyInternalKey);
        if (context != null && !context.isBlank()) {
            return context;
        }
        log.warn("知识库检索无结果，使用兜底 knowledge_context query={}", truncate(query, 120));
        return EMPTY_KNOWLEDGE_CONTEXT;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDoctorReply(JsonNode response) {
        JsonNode replyNode = extractDoctorReply(response);
        if (replyNode.isMissingNode() || replyNode.isNull()) {
            throw new IllegalStateException("工作流未返回 doctor_reply");
        }
        return objectMapper.convertValue(replyNode, Map.class);
    }

    private JsonNode extractDoctorReply(JsonNode response) {
        JsonNode node = response.path("data").path("outputs").path(DifyConsultationWorkflowContract.OUTPUT_KEY);
        if (!node.isMissingNode() && !node.isNull()) {
            return node;
        }
        node = response.path("outputs").path(DifyConsultationWorkflowContract.OUTPUT_KEY);
        if (!node.isMissingNode() && !node.isNull()) {
            return node;
        }
        JsonNode text = response.path("data").path("outputs").path("text");
        if (text.isTextual()) {
            try {
                return objectMapper.readTree(text.asText()).path(DifyConsultationWorkflowContract.OUTPUT_KEY);
            } catch (Exception ignored) {
                return text;
            }
        }
        return response.path("data").path("outputs").path("text");
    }

    private void assertWorkflowSucceeded(JsonNode response) {
        String status = response.path("data").path("status").asText(null);
        if (status == null || status.isBlank()) {
            status = response.path("status").asText(null);
        }
        if (status != null && !status.isBlank() && !"succeeded".equalsIgnoreCase(status)) {
            String error = response.path("data").path("error").asText(
                    response.path("error").asText("工作流执行失败"));
            throw new IllegalStateException("Dify 工作流状态=" + status + ": " + error);
        }
    }

    private ConsultationMessage saveMessage(String sessionId, int senderType, String senderId,
                                            String content, int isAiGenerated, String aiMetadata) {
        ConsultationMessage message = new ConsultationMessage();
        message.setMessageId(IdGenerator.nextId("msg_"));
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setMessageType(1);
        message.setContent(content);
        message.setIsAiGenerated(isAiGenerated);
        message.setAiMetadata(aiMetadata);
        message.setIsRead(senderType == 1 ? 1 : 0);
        messageMapper.insert(message);
        return message;
    }

    private ConsultationSession requireSession(String userId, String sessionId) {
        ConsultationSession session = sessionMapper.findById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(404, "会话不存在");
        }
        return session;
    }

    private ConsultationSession requireActiveSession(String userId, String sessionId) {
        ConsultationSession session = requireSession(userId, sessionId);
        if (session.getStatus() != null && session.getStatus() == 2) {
            throw new BusinessException(409, "会话已关闭，无法发送消息");
        }
        return session;
    }

    private Map<String, Object> toDoctorVo(Doctor doctor) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("doctorId", doctor.getDoctorId());
        vo.put("doctor_id", doctor.getDoctorId());
        vo.put("name", doctor.getName());
        vo.put("title", doctor.getTitle());
        vo.put("department", doctor.getDepartment());
        vo.put("hospital", doctor.getHospital());
        String avatarUrl = minioStorageService.buildDoctorAvatarUrl(
                doctor.getAvatarId() != null ? doctor.getAvatarId() : doctor.getDoctorId());
        vo.put("avatarUrl", avatarUrl);
        vo.put("avatar_url", avatarUrl);
        vo.put("rating", doctor.getRating());
        vo.put("consultationCount", doctor.getConsultationCount());
        vo.put("consultation_count", doctor.getConsultationCount());
        vo.put("status", mapStatusToApi(doctor.getStatus()));
        vo.put("introduction", doctor.getIntroduction());
        return vo;
    }

    private Map<String, Object> toMessageVo(ConsultationMessage message) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("messageId", message.getMessageId());
        vo.put("message_id", message.getMessageId());
        String senderType = message.getSenderType() != null && message.getSenderType() == 2 ? "doctor" : "user";
        vo.put("senderType", senderType);
        vo.put("sender_type", senderType);
        vo.put("content", message.getContent());
        vo.put("sentAt", message.getSentAt());
        vo.put("sent_at", message.getSentAt());
        return vo;
    }

    private Map<String, Object> mapSuggestion(JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("possibleDiagnoses", mapDiagnoses(node.path("possible_diagnoses")));
        result.put("suggestedQuestions", toStringList(node.path("suggested_questions")));
        result.put("recommendedExams", toStringList(node.path("recommended_exams")));
        result.put("treatmentStrategy", node.path("treatment_strategy").asText(""));
        return result;
    }

    private List<Map<String, String>> mapDiagnoses(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<Map<String, String>> list = new ArrayList<>();
        for (JsonNode item : node) {
            Map<String, String> diag = new LinkedHashMap<>();
            diag.put("name", item.path("name").asText(""));
            diag.put("probability", item.path("probability").asText(""));
            list.add(diag);
        }
        return list;
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            list.add(item.asText(""));
        }
        return list;
    }

    private Map<String, Object> emptySuggestion() {
        return Map.of(
                "possibleDiagnoses", List.of(),
                "suggestedQuestions", List.of(),
                "recommendedExams", List.of(),
                "treatmentStrategy", ""
        );
    }

    private String mapStatusToApi(Integer status) {
        if (status == null) {
            return "offline";
        }
        return switch (status) {
            case 1 -> "online";
            case 3 -> "busy";
            default -> "offline";
        };
    }

    private Integer mapStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.toLowerCase()) {
            case "online" -> 1;
            case "busy" -> 3;
            case "offline" -> 2;
            default -> null;
        };
    }

    private Integer mapSessionStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return "active".equalsIgnoreCase(status) ? 1 : "closed".equalsIgnoreCase(status) ? 2 : null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
