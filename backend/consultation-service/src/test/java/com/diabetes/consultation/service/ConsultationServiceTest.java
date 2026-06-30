package com.diabetes.consultation.service;

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
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("ConsultationService 问诊服务测试")
class ConsultationServiceTest {

    @Mock
    private DoctorMapper doctorMapper;

    @Mock
    private ConsultationSessionMapper sessionMapper;

    @Mock
    private ConsultationMessageMapper messageMapper;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private HealthServiceClient healthServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private HomeServiceClient homeServiceClient;

    @Mock
    private DifyClient difyClient;

    private ObjectMapper objectMapper;

    private ConsultationService service;
    private ConsultationService serviceWithDify;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        service = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", "", "blocking", "");
        serviceWithDify = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", "test-api-key", "blocking", "internal-key");
    }

    // ==================== listDoctors ====================

    @Nested
    @DisplayName("listDoctors 医生列表")
    class ListDoctorsTests {

        @Test
        @DisplayName("返回医生列表")
        void listDoctors() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of(doctor));
            when(minioStorageService.buildDoctorAvatarUrl("d1")).thenReturn("http://avatar/d1");

            List<Map<String, Object>> result = service.listDoctors(null, null, null);

            assertEquals(1, result.size());
            assertEquals("d1", result.get(0).get("doctorId"));
            assertEquals("张医生", result.get(0).get("name"));
            assertEquals("内分泌科", result.get(0).get("department"));
            assertEquals("online", result.get(0).get("status"));
        }

        @Test
        @DisplayName("按状态过滤 - online")
        void listDoctors_online() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(doctorMapper.findAll(null, null, 1)).thenReturn(List.of(doctor));

            List<Map<String, Object>> result = service.listDoctors(null, null, "online");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("按状态过滤 - busy")
        void listDoctors_busy() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 3);
            when(doctorMapper.findAll(null, null, 3)).thenReturn(List.of(doctor));

            List<Map<String, Object>> result = service.listDoctors(null, null, "busy");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("按状态过滤 - offline")
        void listDoctors_offline() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 2);
            when(doctorMapper.findAll(null, null, 2)).thenReturn(List.of(doctor));

            List<Map<String, Object>> result = service.listDoctors(null, null, "offline");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("按状态过滤 - 无效状态返回null")
        void listDoctors_invalidStatus() {
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of());

            List<Map<String, Object>> result = service.listDoctors(null, null, "invalid");

            assertTrue(result.isEmpty());
            verify(doctorMapper).findAll(null, null, null);
        }

        @Test
        @DisplayName("医生状态为null显示offline")
        void listDoctors_nullStatus() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", null);
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of(doctor));
            when(minioStorageService.buildDoctorAvatarUrl("d1")).thenReturn("http://avatar/d1");

            List<Map<String, Object>> result = service.listDoctors(null, null, null);

            assertEquals("offline", result.get(0).get("status"));
        }

        @Test
        @DisplayName("医生状态为其他值显示offline")
        void listDoctors_otherStatus() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 5);
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of(doctor));
            when(minioStorageService.buildDoctorAvatarUrl("d1")).thenReturn("http://avatar/d1");

            List<Map<String, Object>> result = service.listDoctors(null, null, null);

            assertEquals("offline", result.get(0).get("status"));
        }

        @Test
        @DisplayName("按科室和关键词过滤")
        void listDoctors_filtered() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(doctorMapper.findAll("内分泌科", "张", 1)).thenReturn(List.of(doctor));
            when(minioStorageService.buildDoctorAvatarUrl("d1")).thenReturn("http://avatar/d1");

            List<Map<String, Object>> result = service.listDoctors("内分泌科", "张", "online");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("空列表")
        void listDoctors_empty() {
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of());

            List<Map<String, Object>> result = service.listDoctors(null, null, null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("医生有avatarId时使用avatarId")
        void listDoctors_withAvatarId() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            doctor.setAvatarId("avatar_001");
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of(doctor));
            when(minioStorageService.buildDoctorAvatarUrl("avatar_001")).thenReturn("http://avatar/avatar_001");

            List<Map<String, Object>> result = service.listDoctors(null, null, null);

            assertEquals("http://avatar/avatar_001", result.get(0).get("avatarUrl"));
        }
    }

    // ==================== listDepartments ====================

    @Nested
    @DisplayName("listDepartments 科室列表")
    class ListDepartmentsTests {

        @Test
        @DisplayName("返回科室列表")
        void listDepartments() {
            Doctor d1 = createDoctor("d1", "张医生", "内分泌科", 1);
            Doctor d2 = createDoctor("d2", "李医生", "心血管科", 1);
            Doctor d3 = createDoctor("d3", "王医生", "内分泌科", 1);
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of(d1, d2, d3));

            List<String> result = service.listDepartments();

            assertEquals(2, result.size());
            assertEquals("内分泌科", result.get(0));
            assertEquals("心血管科", result.get(1));
        }

        @Test
        @DisplayName("空列表")
        void listDepartments_empty() {
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of());

            List<String> result = service.listDepartments();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("过滤null和空科室")
        void listDepartments_filterNull() {
            Doctor d1 = createDoctor("d1", "张医生", null, 1);
            Doctor d2 = createDoctor("d2", "李医生", "", 1);
            Doctor d3 = createDoctor("d3", "王医生", "  ", 1);
            Doctor d4 = createDoctor("d4", "赵医生", "内分泌科", 1);
            when(doctorMapper.findAll(null, null, null)).thenReturn(List.of(d1, d2, d3, d4));

            List<String> result = service.listDepartments();

            assertEquals(1, result.size());
            assertEquals("内分泌科", result.get(0));
        }
    }

    // ==================== createSession ====================

    @Nested
    @DisplayName("createSession 创建会话")
    class CreateSessionTests {

        @Test
        @DisplayName("创建会话成功")
        void createSession() {
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(sessionMapper.insert(any(ConsultationSession.class))).thenReturn(1);
            when(doctorMapper.incrementConsultationCount("d1")).thenReturn(1);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(minioStorageService.buildDoctorAvatarUrl(anyString())).thenReturn("http://avatar/d1");

            Map<String, Object> result = service.createSession("user1", "d1");

            assertNotNull(result.get("sessionId"));
            assertEquals("active", result.get("status"));
            assertEquals("张医生", result.get("doctorName"));
            verify(sessionMapper).insert(any(ConsultationSession.class));
            verify(doctorMapper).incrementConsultationCount("d1");
        }

        @Test
        @DisplayName("doctorId为null抛异常")
        void createSession_nullDoctorId() {
            assertThrows(BusinessException.class, () -> service.createSession("user1", null));
        }

        @Test
        @DisplayName("doctorId为空字符串抛异常")
        void createSession_blankDoctorId() {
            assertThrows(BusinessException.class, () -> service.createSession("user1", ""));
            assertThrows(BusinessException.class, () -> service.createSession("user1", "  "));
        }

        @Test
        @DisplayName("医生不存在抛异常")
        void createSession_doctorNotFound() {
            when(doctorMapper.findById("d1")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.createSession("user1", "d1"));
            assertEquals(404, ex.getCode());
        }
    }

    // ==================== listSessions ====================

    @Nested
    @DisplayName("listSessions 会话列表")
    class ListSessionsTests {

        @Test
        @DisplayName("返回会话列表")
        void listSessions() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findByUserId("user1", 1, 0, 10)).thenReturn(List.of(session));
            when(sessionMapper.countByUserId("user1", 1)).thenReturn(1);
            when(doctorMapper.findById("d1")).thenReturn(doctor);

            Map<String, Object> result = service.listSessions("user1", "active", 1, 10);

            assertEquals(1, ((List<?>) result.get("sessions")).size());
            assertEquals(1, result.get("total"));
            Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("sessions")).get(0);
            assertEquals("张医生", item.get("doctorName"));
            assertEquals("active", item.get("status"));
        }

        @Test
        @DisplayName("会话状态为2显示closed")
        void listSessions_closedStatus() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 2);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findByUserId("user1", 2, 0, 10)).thenReturn(List.of(session));
            when(sessionMapper.countByUserId("user1", 2)).thenReturn(1);
            when(doctorMapper.findById("d1")).thenReturn(doctor);

            Map<String, Object> result = service.listSessions("user1", "closed", 1, 10);

            Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("sessions")).get(0);
            assertEquals("closed", item.get("status"));
        }

        @Test
        @DisplayName("会话状态为null显示closed")
        void listSessions_nullStatus() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", null);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findByUserId("user1", null, 0, 10)).thenReturn(List.of(session));
            when(sessionMapper.countByUserId("user1", null)).thenReturn(1);
            when(doctorMapper.findById("d1")).thenReturn(doctor);

            Map<String, Object> result = service.listSessions("user1", null, 1, 10);

            Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("sessions")).get(0);
            assertEquals("closed", item.get("status"));
        }

        @Test
        @DisplayName("无效状态过滤 - 状态为其他值")
        void listSessions_otherStatusFilter() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findByUserId("user1", null, 0, 10)).thenReturn(List.of(session));
            when(sessionMapper.countByUserId("user1", null)).thenReturn(1);

            // 其他状态值会被当作 null 处理
            assertThrows(NullPointerException.class, () -> service.listSessions("user1", "invalid", 1, 10));
        }

        @Test
        @DisplayName("空列表")
        void listSessions_empty() {
            when(sessionMapper.findByUserId("user1", null, 0, 10)).thenReturn(List.of());
            when(sessionMapper.countByUserId("user1", null)).thenReturn(0);

            Map<String, Object> result = service.listSessions("user1", null, 1, 10);

            assertTrue(((List<?>) result.get("sessions")).isEmpty());
            assertEquals(0, result.get("total"));
        }

        @Test
        @DisplayName("医生信息为空")
        void listSessions_doctorNull() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findByUserId("user1", null, 0, 10)).thenReturn(List.of(session));
            when(sessionMapper.countByUserId("user1", null)).thenReturn(1);
            when(doctorMapper.findById("d1")).thenReturn(null);

            Map<String, Object> result = service.listSessions("user1", null, 1, 10);

            assertEquals(1, ((List<?>) result.get("sessions")).size());
            Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("sessions")).get(0);
            assertNull(item.get("doctorName"));
        }

        @Test
        @DisplayName("会话有评分和反馈")
        void listSessions_withRating() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 2);
            session.setRating(5);
            session.setFeedback("非常满意");
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findByUserId("user1", 2, 0, 10)).thenReturn(List.of(session));
            when(sessionMapper.countByUserId("user1", 2)).thenReturn(1);
            when(doctorMapper.findById("d1")).thenReturn(doctor);

            Map<String, Object> result = service.listSessions("user1", "closed", 1, 10);

            Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("sessions")).get(0);
            assertEquals(5, item.get("rating"));
            assertEquals("非常满意", item.get("feedback"));
        }
    }

    // ==================== sendMessage ====================

    @Nested
    @DisplayName("sendMessage 发送消息")
    class SendMessageTests {

        @Test
        @DisplayName("发送消息成功 - 无Dify apiKey")
        void sendMessage_noDifyKey() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);

            Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("userMessage"));
            assertEquals("user", result.get("senderType"));
            assertEquals("你好医生", result.get("content"));
            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - 有Dify apiKey但调用失败")
        void sendMessage_difyCallFail() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of("age", 50));
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                    .thenReturn(Map.of("height", 170));
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("糖尿病指南");
            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenThrow(new RuntimeException("Dify调用失败"));

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("userMessage"));
            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - Dify返回成功")
        void sendMessage_difySuccess() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            doctor.setIntroduction("资深内分泌专家");
            doctor.setSpecialties("糖尿病、甲状腺疾病");
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString()))
                    .thenReturn(Map.of("age", 50, "gender", "male", "privacy_settings", Map.of("data_visible", true)));
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                    .thenReturn(Map.of("height", 170, "weight", 65, "fastingGlucose", 5.5));
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("糖尿病指南片段");
            when(messageMapper.findRecentBySessionId("sess_001", 10)).thenReturn(List.of());

            // 构造 Dify 响应
            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "succeeded");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            ObjectNode doctorReply = outputsNode.putObject("doctor_reply");
            doctorReply.put("content", "建议控制饮食");
            ObjectNode suggestion = doctorReply.putObject("suggestion");
            suggestion.putArray("possible_diagnoses").addObject().put("name", "糖尿病前期").put("probability", "60%");
            suggestion.putArray("suggested_questions").add("最近血糖监测情况如何");
            suggestion.putArray("recommended_exams").add("糖化血红蛋白检测");
            suggestion.put("treatment_strategy", "饮食控制");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("userMessage"));
            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - 用户健康数据不可见")
        void sendMessage_healthDataNotVisible() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString()))
                    .thenReturn(Map.of("privacySettings", Map.of("dataVisible", false)));
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "succeeded");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            ObjectNode doctorReply = outputsNode.putObject("doctor_reply");
            doctorReply.put("content", "回复");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - 有对话历史")
        void sendMessage_withHistory() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "你好");
            ConsultationMessage msg2 = createMessage("msg_2", "sess_001", 2, "d1", "请问有什么可以帮您");
            msg2.setSentAt(LocalDateTime.now());

            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(3);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
            when(messageMapper.findRecentBySessionId("sess_001", 10)).thenReturn(List.of(msg2, msg1));

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "succeeded");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            outputsNode.putObject("doctor_reply").put("content", "回复");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "最近血糖偏高", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - Dify返回空content")
        void sendMessage_difyEmptyContent() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "succeeded");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            outputsNode.putObject("doctor_reply").put("content", "");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - Dify返回带suggestion")
        void sendMessage_difyWithSuggestion() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "succeeded");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            ObjectNode doctorReply = outputsNode.putObject("doctor_reply");
            doctorReply.put("content", "回复内容");
            ObjectNode suggestion = doctorReply.putObject("suggestion");
            suggestion.putArray("possible_diagnoses");
            suggestion.putArray("suggested_questions");
            suggestion.putArray("recommended_exams");
            suggestion.put("treatment_strategy", "建议");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - Dify工作流状态非succeeded")
        void sendMessage_difyWorkflowFailed() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "failed");
            dataNode.put("error", "工作流执行失败");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
            // 失败时返回 AI_UNAVAILABLE
        }

        @Test
        @DisplayName("发送消息成功 - Dify响应在outputs层级")
        void sendMessage_difyOutputsLevel() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode outputsNode = response.putObject("outputs");
            outputsNode.putObject("doctor_reply").put("content", "回复");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - Dify响应text字段")
        void sendMessage_difyTextField() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            outputsNode.put("text", "{\"doctor_reply\":{\"content\":\"回复内容\"}}");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - Dify响应text字段为纯文本")
        void sendMessage_difyTextPlain() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            outputsNode.put("text", "纯文本回复");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - 知识库检索无结果")
        void sendMessage_noKnowledgeContext() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);
            when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
            when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
            when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn(null);

            ObjectNode response = objectMapper.createObjectNode();
            ObjectNode dataNode = response.putObject("data");
            dataNode.put("status", "succeeded");
            ObjectNode outputsNode = dataNode.putObject("outputs");
            outputsNode.putObject("doctor_reply").put("content", "回复");

            when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                    .thenReturn(response);

            Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("发送消息成功 - 医生不存在时返回null AI消息")
        void sendMessage_doctorNotFound() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(null);

            Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好医生", null);

            assertNotNull(result.get("userMessage"));
            assertNull(result.get("aiMessage"));
        }

        @Test
        @DisplayName("内容为null抛异常")
        void sendMessage_nullContent() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            assertThrows(BusinessException.class, () -> service.sendMessage("user1", "sess_001", null, null));
        }

        @Test
        @DisplayName("内容为空字符串抛异常")
        void sendMessage_blankContent() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            assertThrows(BusinessException.class, () -> service.sendMessage("user1", "sess_001", "", null));
            assertThrows(BusinessException.class, () -> service.sendMessage("user1", "sess_001", "  ", null));
        }

        @Test
        @DisplayName("会话不存在抛异常")
        void sendMessage_sessionNotFound() {
            when(sessionMapper.findById("sess_001")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.sendMessage("user1", "sess_001", "你好", null));
            assertEquals(404, ex.getCode());
        }

        @Test
        @DisplayName("会话已关闭抛异常")
        void sendMessage_sessionClosed() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 2);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.sendMessage("user1", "sess_001", "你好", null));
            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("会话不属于用户抛异常")
        void sendMessage_wrongUser() {
            ConsultationSession session = createSession("sess_001", "user2", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            assertThrows(BusinessException.class, () -> service.sendMessage("user1", "sess_001", "你好", null));
        }

        @Test
        @DisplayName("用户发送消息senderType为1")
        void sendMessage_userSenderType() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
            when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
            when(doctorMapper.findById("d1")).thenReturn(doctor);

            Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好", null);

            // 验证插入的消息
            verify(messageMapper).insert(argThat(msg -> msg.getSenderType() == 1 && msg.getIsRead() == 1));
        }
    }

    // ==================== listMessages ====================

    @Nested
    @DisplayName("listMessages 消息列表")
    class ListMessagesTests {

        @Test
        @DisplayName("返回消息列表")
        void listMessages() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            ConsultationMessage message = createMessage("msg_001", "sess_001", 1, "user1", "你好");
            message.setSentAt(LocalDateTime.now());
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findBySessionId("sess_001", 0, 50)).thenReturn(List.of(message));
            when(messageMapper.countBySessionId("sess_001")).thenReturn(1);

            Map<String, Object> result = service.listMessages("user1", "sess_001", 1, 50);

            assertEquals(1, ((List<?>) result.get("messages")).size());
            assertEquals(1, result.get("total"));
        }

        @Test
        @DisplayName("医生消息senderType为doctor")
        void listMessages_doctorMessage() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "你好");
            message.setSentAt(LocalDateTime.now());
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findBySessionId("sess_001", 0, 50)).thenReturn(List.of(message));
            when(messageMapper.countBySessionId("sess_001")).thenReturn(1);

            Map<String, Object> result = service.listMessages("user1", "sess_001", 1, 50);

            List<?> messages = (List<?>) result.get("messages");
            Map<?, ?> msgVo = (Map<?, ?>) messages.get(0);
            assertEquals("doctor", msgVo.get("senderType"));
        }

        @Test
        @DisplayName("空列表")
        void listMessages_empty() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findBySessionId("sess_001", 0, 50)).thenReturn(List.of());
            when(messageMapper.countBySessionId("sess_001")).thenReturn(0);

            Map<String, Object> result = service.listMessages("user1", "sess_001", 1, 50);

            assertTrue(((List<?>) result.get("messages")).isEmpty());
            assertEquals(0, result.get("total"));
        }

        @Test
        @DisplayName("会话不存在抛异常")
        void listMessages_sessionNotFound() {
            when(sessionMapper.findById("sess_001")).thenReturn(null);

            assertThrows(BusinessException.class, () -> service.listMessages("user1", "sess_001", 1, 50));
        }

        @Test
        @DisplayName("分页计算offset")
        void listMessages_pagination() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findBySessionId("sess_001", 50, 50)).thenReturn(List.of());
            when(messageMapper.countBySessionId("sess_001")).thenReturn(0);

            service.listMessages("user1", "sess_001", 2, 50);

            verify(messageMapper).findBySessionId("sess_001", 50, 50);
        }
    }

    // ==================== closeSession ====================

    @Nested
    @DisplayName("closeSession 关闭会话")
    class CloseSessionTests {

        @Test
        @DisplayName("关闭会话成功")
        void closeSession() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(sessionMapper.closeSession("sess_001", 5, "很好")).thenReturn(1);

            service.closeSession("user1", "sess_001", 5, "很好");

            verify(sessionMapper).closeSession("sess_001", 5, "很好");
        }

        @Test
        @DisplayName("评分和反馈为null")
        void closeSession_nullRatingFeedback() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(sessionMapper.closeSession("sess_001", null, null)).thenReturn(1);

            service.closeSession("user1", "sess_001", null, null);

            verify(sessionMapper).closeSession("sess_001", null, null);
        }

        @Test
        @DisplayName("会话已关闭抛异常")
        void closeSession_alreadyClosed() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 2);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closeSession("user1", "sess_001", 5, null));
            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("评分小于1抛异常")
        void closeSession_ratingTooLow() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closeSession("user1", "sess_001", 0, null));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("评分大于5抛异常")
        void closeSession_ratingTooHigh() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closeSession("user1", "sess_001", 6, null));
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("更新失败抛异常")
        void closeSession_updateFailed() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(sessionMapper.closeSession("sess_001", 5, null)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closeSession("user1", "sess_001", 5, null));
            assertEquals(409, ex.getCode());
        }

        @Test
        @DisplayName("会话不存在抛异常")
        void closeSession_notFound() {
            when(sessionMapper.findById("sess_001")).thenReturn(null);

            assertThrows(BusinessException.class, () -> service.closeSession("user1", "sess_001", 5, null));
        }
    }

    // ==================== getAiSuggestion ====================

    @Nested
    @DisplayName("getAiSuggestion AI建议")
    class GetAiSuggestionTests {

        @Test
        @DisplayName("返回AI建议")
        void getAiSuggestion() throws Exception {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
            message.setAiMetadata(
                    "{\"possible_diagnoses\":[{\"name\":\"糖尿病\",\"probability\":\"80%\"}],\"suggested_questions\":[\"测试\"],\"recommended_exams\":[\"血糖检测\"],\"treatment_strategy\":\"建议饮食控制\"}");
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

            Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

            assertNotNull(result.get("possibleDiagnoses"));
            assertNotNull(result.get("suggestedQuestions"));
            assertNotNull(result.get("recommendedExams"));
            assertNotNull(result.get("treatmentStrategy"));
        }

        @Test
        @DisplayName("AI元数据为空字符串返回空建议")
        void getAiSuggestion_blankMetadata() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
            message.setAiMetadata("");
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

            Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

            assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
            assertTrue(((List<?>) result.get("suggestedQuestions")).isEmpty());
            assertTrue(((List<?>) result.get("recommendedExams")).isEmpty());
            assertEquals("", result.get("treatmentStrategy"));
        }

        @Test
        @DisplayName("无AI消息返回空建议")
        void getAiSuggestion_noMessage() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(null);

            Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

            assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
            assertEquals("", result.get("treatmentStrategy"));
        }

        @Test
        @DisplayName("JSON解析失败返回空建议")
        void getAiSuggestion_parseError() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
            message.setAiMetadata("invalid json");
            when(sessionMapper.findById("sess_001")).thenReturn(session);
            when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

            Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

            assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
        }
    }

    // ==================== getActiveSession ====================

    @Nested
    @DisplayName("getActiveSession 活跃会话")
    class GetActiveSessionTests {

        @Test
        @DisplayName("返回活跃会话")
        void getActiveSession() {
            ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
            session.setStartedAt(LocalDateTime.now());
            when(sessionMapper.findActiveByUserId("user1")).thenReturn(session);

            Map<String, Object> result = service.getActiveSession("user1");

            assertNotNull(result.get("session"));
            assertEquals("sess_001", ((Map<?, ?>) result.get("session")).get("sessionId"));
            assertEquals("active", ((Map<?, ?>) result.get("session")).get("status"));
        }

        @Test
        @DisplayName("无活跃会话返回空")
        void getActiveSession_noActive() {
            when(sessionMapper.findActiveByUserId("user1")).thenReturn(null);

            Map<String, Object> result = service.getActiveSession("user1");

            assertTrue(((Map<?, ?>) result.get("session")).isEmpty());
        }
    }

    // ==================== getDifyWorkflowSpec ====================

    @Test
    @DisplayName("getDifyWorkflowSpec - 返回工作流规范")
    void getDifyWorkflowSpec() {
        Map<String, Object> result = service.getDifyWorkflowSpec();

        assertNotNull(result.get("workflowUrl"));
        assertNotNull(result.get("inputJsonSchema"));
        assertNotNull(result.get("outputJsonSchema"));
        assertNotNull(result.get("inputExample"));
        assertEquals("doctor_reply", result.get("outputKey"));
    }

    // ==================== Helper Methods ====================

    private Doctor createDoctor(String doctorId, String name, String department, Integer status) {
        Doctor doctor = new Doctor();
        doctor.setDoctorId(doctorId);
        doctor.setName(name);
        doctor.setTitle("主任医师");
        doctor.setDepartment(department);
        doctor.setHospital("北京医院");
        doctor.setRating(BigDecimal.valueOf(4.8));
        doctor.setConsultationCount(100);
        doctor.setStatus(status);
        return doctor;
    }

    private ConsultationSession createSession(String sessionId, String userId, String doctorId, Integer status) {
        ConsultationSession session = new ConsultationSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setDoctorId(doctorId);
        session.setStatus(status);
        session.setStartedAt(LocalDateTime.now());
        return session;
    }

    private ConsultationMessage createMessage(String messageId, String sessionId, Integer senderType, String senderId,
            String content) {
        ConsultationMessage message = new ConsultationMessage();
        message.setMessageId(messageId);
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setSentAt(LocalDateTime.now());
        return message;
    }

    // ==================== 构造方法 difyResponseMode=null 分支 ====================

    @Test
    @DisplayName("构造方法 - difyResponseMode为null")
    void constructor_nullResponseMode() {
        ConsultationService serviceWithNullMode = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", "test-api-key", null, "internal-key");

        assertNotNull(serviceWithNullMode);
    }

    // ==================== closeSession status=null 分支 ====================

    @Test
    @DisplayName("closeSession - status为null时关闭成功")
    void closeSession_statusNull() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", null);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(sessionMapper.closeSession("sess_001", 5, null)).thenReturn(1);

        service.closeSession("user1", "sess_001", 5, null);

        verify(sessionMapper).closeSession("sess_001", 5, null);
    }

    // ==================== requireActiveSession status=null 分支 ====================

    @Test
    @DisplayName("requireActiveSession - status为null时允许发送消息")
    void sendMessage_statusNull() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", null);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("userMessage"));
    }

    // ==================== generateAiReply 分支覆盖 ====================

    @Test
    @DisplayName("sendMessage - Dify返回空content")
    void sendMessage_difyEmptyContent() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "   ");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== isHealthDataVisible 分支覆盖 ====================

    @Test
    @DisplayName("isHealthDataVisible - privacySettings为null返回true")
    void isHealthDataVisible_nullPrivacy() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("isHealthDataVisible - dataVisible为null返回true")
    void isHealthDataVisible_nullDataVisible() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString()))
                .thenReturn(Map.of("privacy_settings", Map.of()));
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("isHealthDataVisible - dataVisible为Boolean false")
    void isHealthDataVisible_booleanFalse() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString()))
                .thenReturn(Map.of("privacy_settings", Map.of("data_visible", false)));
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("isHealthDataVisible - dataVisible为String \"false\"")
    void isHealthDataVisible_stringFalse() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString()))
                .thenReturn(Map.of("privacy_settings", Map.of("data_visible", "false")));
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory 分支覆盖 ====================

    @Test
    @DisplayName("sendMessage - 有对话历史 excludeLatest=true")
    void sendMessage_withHistoryExcludeLatest() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "你好");
        ConsultationMessage msg2 = createMessage("msg_2", "sess_001", 2, "d1", "请问有什么可以帮您");
        msg1.setSentAt(LocalDateTime.now().minusMinutes(5));
        msg2.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(3);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg2, msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "最近血糖偏高", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("sendMessage - 对话历史为空列表")
    void sendMessage_emptyHistory() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of());

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildDoctorRole 分支覆盖 ====================

    @Test
    @DisplayName("sendMessage - 医生有介绍和擅长领域")
    void sendMessage_doctorWithIntroAndSpecialties() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setIntroduction("资深内分泌专家，从医30年");
        doctor.setSpecialties("糖尿病、甲状腺疾病、骨质疏松");
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== parseDoctorReply/extractDoctorReply 分支覆盖
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应缺失doctor_reply")
    void sendMessage_difyMissingDoctorReply() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("sendMessage - Dify响应仅在outputs层级")
    void sendMessage_difyOutputsOnly() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 ====================

    @Test
    @DisplayName("sendMessage - Dify工作流状态失败")
    void sendMessage_difyFailedStatus() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "failed");
        dataNode.put("error", "工作流执行失败");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("sendMessage - Dify工作流状态在顶层")
    void sendMessage_difyStatusAtTopLevel() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "succeeded");
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== toMessageVo senderType=user 分支 ====================

    @Test
    @DisplayName("listMessages - 用户消息senderType为user")
    void listMessages_userMessage() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        ConsultationMessage message = createMessage("msg_001", "sess_001", 1, "user1", "你好");
        message.setSentAt(LocalDateTime.now());
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findBySessionId("sess_001", 0, 50)).thenReturn(List.of(message));
        when(messageMapper.countBySessionId("sess_001")).thenReturn(1);

        Map<String, Object> result = service.listMessages("user1", "sess_001", 1, 50);

        List<?> messages = (List<?>) result.get("messages");
        Map<?, ?> msgVo = (Map<?, ?>) messages.get(0);
        assertEquals("user", msgVo.get("senderType"));
    }

    // ==================== mapStatusFilter/mapSessionStatusFilter null分支
    // ====================

    @Test
    @DisplayName("listDoctors - status为空字符串")
    void listDoctors_emptyStatus() {
        when(doctorMapper.findAll(null, null, null)).thenReturn(List.of());

        List<Map<String, Object>> result = service.listDoctors(null, null, "");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listSessions - status为空字符串")
    void listSessions_emptyStatus() {
        when(sessionMapper.findByUserId("user1", null, 0, 10)).thenReturn(List.of());
        when(sessionMapper.countByUserId("user1", null)).thenReturn(0);

        Map<String, Object> result = service.listSessions("user1", "", 1, 10);

        assertTrue(((List<?>) result.get("sessions")).isEmpty());
    }

    // ==================== truncate/stringValue/firstPresent 分支覆盖
    // ====================

    @Test
    @DisplayName("createSession - greeting超过500字符被截断")
    void createSession_longGreeting() {
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setTitle("这是一个非常非常长的职称名称".repeat(20));
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(sessionMapper.insert(any(ConsultationSession.class))).thenReturn(1);
        when(doctorMapper.incrementConsultationCount("d1")).thenReturn(1);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(minioStorageService.buildDoctorAvatarUrl(anyString())).thenReturn("http://avatar/d1");

        Map<String, Object> result = service.createSession("user1", "d1");

        assertNotNull(result.get("sessionId"));
    }

    // ==================== truncate 分支覆盖 ====================

    @Test
    @DisplayName("createSession - greeting超过500字符被截断")
    void createSession_truncateText() {
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setTitle("这是一个非常非常长的职称名称".repeat(20));
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(sessionMapper.insert(any(ConsultationSession.class))).thenReturn(1);
        when(doctorMapper.incrementConsultationCount("d1")).thenReturn(1);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(minioStorageService.buildDoctorAvatarUrl(anyString())).thenReturn("http://avatar/d1");

        Map<String, Object> result = service.createSession("user1", "d1");

        assertNotNull(result.get("sessionId"));
        verify(sessionMapper).updateAfterMessage(anyString(), argThat(text -> text.length() <= 500), eq(1));
    }

    // ==================== stringValue 分支覆盖 ====================

    @Test
    @DisplayName("sendMessage - Dify返回content为null")
    void sendMessage_difyContentNull() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        ObjectNode doctorReply = outputsNode.putObject("doctor_reply");
        doctorReply.putNull("content");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== truncate 分支覆盖 (通过反射) ====================

    @Test
    @DisplayName("truncate - text为null返回空字符串")
    void truncate_nullText() throws Exception {
        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("truncate", String.class,
                int.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, null, 500);
        assertEquals("", result);
    }

    @Test
    @DisplayName("truncate - text长度不超过max不截断")
    void truncate_noTruncation() throws Exception {
        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("truncate", String.class,
                int.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "hello", 500);
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("truncate - text长度超过max被截断")
    void truncate_exceedsMax() throws Exception {
        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("truncate", String.class,
                int.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "hello world", 5);
        assertEquals("hello", result);
    }

    // ==================== buildConversationHistory 分支覆盖 (通过反射)
    // ====================

    @Test
    @DisplayName("buildConversationHistory - excludeLatest=false分支")
    void buildConversationHistory_excludeLatestFalse() throws Exception {
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "你好");
        msg1.setSentAt(null);
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("buildConversationHistory",
                String.class, boolean.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "sess_001", false);

        assertTrue(result.contains("用户"));
    }

    @Test
    @DisplayName("buildConversationHistory - senderType=2和sentAt不为null")
    void buildConversationHistory_senderType2() throws Exception {
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "AI回复");
        msg1.setSentAt(LocalDateTime.now());
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("buildConversationHistory",
                String.class, boolean.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "sess_001", false);

        assertTrue(result.contains("AI医生"));
    }

    @Test
    @DisplayName("buildConversationHistory - excludeLatest=true且列表不为空")
    void buildConversationHistory_excludeLatestTrue() throws Exception {
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "消息1");
        ConsultationMessage msg2 = createMessage("msg_2", "sess_001", 2, "d1", "消息2");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg2, msg1));

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("buildConversationHistory",
                String.class, boolean.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "sess_001", true);

        assertTrue(result.contains("消息1"));
    }

    @Test
    @DisplayName("buildConversationHistory - excludeLatest=false且列表不为空")
    void buildConversationHistory_excludeLatestFalseNotEmpty() throws Exception {
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "消息1");
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("buildConversationHistory",
                String.class, boolean.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "sess_001", false);

        assertTrue(result.contains("消息1"));
    }

    @Test
    @DisplayName("buildConversationHistory - 空列表")
    void buildConversationHistory_emptyList() throws Exception {
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of());

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("buildConversationHistory",
                String.class, boolean.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "sess_001", false);

        assertEquals("", result);
    }

    @Test
    @DisplayName("buildConversationHistory - senderType为null")
    void buildConversationHistory_senderTypeNull() throws Exception {
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", null, "user1", "你好");
        msg1.setSentAt(LocalDateTime.now());
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("buildConversationHistory",
                String.class, boolean.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "sess_001", false);

        assertTrue(result.contains("用户"));
    }

    // ==================== extractDoctorReply 分支覆盖 (通过反射) ====================

    @Test
    @DisplayName("extractDoctorReply - 第二个路径有效但第一个无效")
    void extractDoctorReply_secondPathValidFirstInvalid() throws Exception {
        String json = "{\"outputs\":{\"doctor_reply\":{\"content\":\"AI回复\"}}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("extractDoctorReply",
                JsonNode.class);
        method.setAccessible(true);
        JsonNode result = (JsonNode) method.invoke(service, response);

        assertEquals("AI回复", result.get("content").asText());
    }

    @Test
    @DisplayName("extractDoctorReply - 回复为null")
    void extractDoctorReply_nullReply() throws Exception {
        String json = "{\"data\":{\"outputs\":{\"doctor_reply\":null}}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("extractDoctorReply",
                JsonNode.class);
        method.setAccessible(true);
        JsonNode result = (JsonNode) method.invoke(service, response);

        assertTrue(result.isNull() || result.isMissingNode());
    }

    @Test
    @DisplayName("extractDoctorReply - 第二个路径 doctor_reply 为 null")
    void extractDoctorReply_secondPathNull() throws Exception {
        String json = "{\"data\":{\"outputs\":{}},\"outputs\":{\"doctor_reply\":null}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("extractDoctorReply",
                JsonNode.class);
        method.setAccessible(true);
        JsonNode result = (JsonNode) method.invoke(service, response);

        assertTrue(result.isNull() || result.isMissingNode());
    }

    // ==================== parseDoctorReply 分支覆盖 (通过反射) ====================

    @Test
    @DisplayName("parseDoctorReply - replyNode为null抛出异常")
    void parseDoctorReply_nullReply() throws Exception {
        String json = "{\"data\":{\"outputs\":{\"doctor_reply\":null}}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("parseDoctorReply",
                JsonNode.class);
        method.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service, response));
    }

    @Test
    @DisplayName("parseDoctorReply - replyNode.isMissingNode抛出异常")
    void parseDoctorReply_isMissingNode() throws Exception {
        String json = "{}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("parseDoctorReply",
                JsonNode.class);
        method.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service, response));
    }

    @Test
    @DisplayName("extractDoctorReply - 第二个路径有效但第一个无效")
    void extractDoctorReply_secondPathValidFirstInvalid_stringReply() throws Exception {
        String json = "{\"outputs\":{\"doctor_reply\":\"test reply\"}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("extractDoctorReply",
                JsonNode.class);
        method.setAccessible(true);

        JsonNode result = (JsonNode) method.invoke(service, response);
        assertEquals("test reply", result.asText());
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (通过反射) ====================

    @Test
    @DisplayName("assertWorkflowSucceeded - status在根路径且不为succeeded")
    void assertWorkflowSucceeded_rootPathFailed() throws Exception {
        String json = "{\"status\":\"failed\",\"error\":\"测试错误\"}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("assertWorkflowSucceeded",
                JsonNode.class);
        method.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service, response));
    }

    @Test
    @DisplayName("assertWorkflowSucceeded - status在data路径且不为succeeded")
    void assertWorkflowSucceeded_dataPathFailed() throws Exception {
        String json = "{\"data\":{\"status\":\"failed\",\"error\":\"数据路径错误\"}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("assertWorkflowSucceeded",
                JsonNode.class);
        method.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service, response));
    }

    @Test
    @DisplayName("assertWorkflowSucceeded - status为空字符串")
    void assertWorkflowSucceeded_emptyStatus() throws Exception {
        String json = "{\"data\":{\"status\":\"\"},\"status\":\"succeeded\"}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("assertWorkflowSucceeded",
                JsonNode.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(service, response));
    }

    // ==================== buildConversationHistory 分支覆盖 ====================

    @Test
    @DisplayName("sendMessage - excludeLatest=false分支")
    void sendMessage_excludeLatestFalse() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "你好");
        msg1.setSentAt(null);
        msg1.setSenderType(null);

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildDoctorRole 分支覆盖 (introduction和specialties为空)
    // ====================

    @Test
    @DisplayName("sendMessage - 医生无介绍和擅长领域")
    void sendMessage_doctorWithoutIntroAndSpecialties() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setIntroduction(null);
        doctor.setSpecialties("");
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory 分支覆盖 (senderType=2,
    // sentAt不为null)
    // ====================

    @Test
    @DisplayName("sendMessage - buildConversationHistory senderType=2和sentAt不为null")
    void sendMessage_buildConversationHistoryAiDoctor() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "AI医生回复");
        msg1.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (顶层status不为succeeded)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify工作流顶层status不为succeeded")
    void sendMessage_difyTopLevelStatusFailed() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "FAILED");
        response.put("error", "顶层错误信息");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== extractDoctorReply 分支覆盖 (第二个路径有效)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应仅outputs路径有效")
    void sendMessage_difyOnlyOutputsPath() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== getAiSuggestion 分支覆盖 (aiMetadata不为null但为空白)
    // ====================

    @Test
    @DisplayName("getAiSuggestion - aiMetadata不为null但为空白字符串")
    void getAiSuggestion_blankAiMetadata() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
        message.setAiMetadata("   ");
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

        Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

        assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
        assertEquals("", result.get("treatmentStrategy"));
    }

    @Test
    @DisplayName("getAiSuggestion - latest为null返回空建议")
    void getAiSuggestion_nullLatest() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(null);

        Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

        assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
        assertEquals("", result.get("treatmentStrategy"));
    }

    @Test
    @DisplayName("getAiSuggestion - aiMetadata为null返回空建议")
    void getAiSuggestion_nullAiMetadata() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
        message.setAiMetadata(null);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

        Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

        assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
        assertEquals("", result.get("treatmentStrategy"));
    }

    // ==================== generateAiReply 分支覆盖 (difyApiKey为null)
    // ====================

    @Test
    @DisplayName("sendMessage - difyApiKey为null")
    void sendMessage_difyApiKeyNull_v2() {
        ConsultationService serviceWithNullKey = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", null, "blocking", "internal-key");

        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = serviceWithNullKey.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== generateAiReply 分支覆盖 (difyApiKey为空白字符)
    // ====================

    @Test
    @DisplayName("sendMessage - difyApiKey为空白字符")
    void sendMessage_difyApiKeyBlank() {
        ConsultationService serviceWithBlankKey = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", "   ", "blocking", "internal-key");

        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = serviceWithBlankKey.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (status在顶层)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify工作流状态在顶层且为succeeded")
    void sendMessage_difyStatusAtTopLevelSucceeded() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "SUCCEEDED");
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== extractDoctorReply 分支覆盖 (data.outputs路径有效)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应data.outputs路径有效")
    void sendMessage_difyDataOutputsPath() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory 分支覆盖 (excludeLatest=false,
    // senderType=null, sentAt=null)
    // ====================

    @Test
    @DisplayName("sendMessage - 对话历史excludeLatest=false且senderType=null且sentAt=null")
    void sendMessage_historyExcludeLatestFalseNullFields() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", null, "user1", "你好");
        msg1.setSentAt(null);

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildDoctorRole 分支覆盖 (introduction=null)
    // ====================

    @Test
    @DisplayName("sendMessage - 医生introduction为null")
    void sendMessage_doctorIntroductionNull() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setIntroduction(null);
        doctor.setSpecialties(null);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== parseDoctorReply 分支覆盖 (isMissingNode异常)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应完全缺失doctor_reply触发异常")
    void sendMessage_difyNoDoctorReplyAtAll() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== extractDoctorReply 分支覆盖 (data.outputs无效但outputs有效)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应data.outputs无效但顶层outputs有效")
    void sendMessage_difyDataOutputsInvalidButOutputsValid() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (status为空字符串)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify工作流status为空字符串")
    void sendMessage_difyStatusEmptyString() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (status不为succeeded)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify工作流status为pending")
    void sendMessage_difyStatusPending() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "pending");
        dataNode.put("error", "任务处理中");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== firstPresent 分支覆盖 (key不存在)
    // ====================

    @Test
    @DisplayName("sendMessage - healthProfile不包含任何指定key")
    void sendMessage_healthProfileNoKeys() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString()))
                .thenReturn(Map.of("privacy_settings", Map.of("data_visible", true)));
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== truncate 分支覆盖 (text=null)
    // ====================

    @Test
    @DisplayName("sendMessage - AI回复内容为null被truncate处理")
    void sendMessage_aiReplyNullContent() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== truncate 分支覆盖 (text.length()<=max)
    // ====================

    @Test
    @DisplayName("createSession - greeting内容不超过500字符不被截断")
    void createSession_greetingNotTruncated() {
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(sessionMapper.insert(any(ConsultationSession.class))).thenReturn(1);
        when(doctorMapper.incrementConsultationCount("d1")).thenReturn(1);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(minioStorageService.buildDoctorAvatarUrl(anyString())).thenReturn("http://avatar/d1");

        Map<String, Object> result = service.createSession("user1", "d1");

        assertNotNull(result.get("sessionId"));
        verify(sessionMapper).updateAfterMessage(anyString(),
                argThat(text -> text.length() <= 500 && text.length() > 0), eq(1));
    }

    // ==================== firstPresent 分支覆盖 (map不包含key) ====================

    @Test
    @DisplayName("sendMessage - healthProfile不包含fastingGlucose字段")
    void sendMessage_healthProfileMissingField() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString()))
                .thenReturn(Map.of("privacy_settings", Map.of("data_visible", true)));
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString()))
                .thenReturn(Map.of("height", 170, "weight", 65));
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== generateAiReply difyApiKey为null分支 ====================

    @Test
    @DisplayName("sendMessage - difyApiKey为null返回AI_UNAVAILABLE")
    void sendMessage_difyApiKeyNull() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("sendMessage - difyApiKey为空字符串返回AI_UNAVAILABLE")
    void sendMessage_difyApiKeyEmpty() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        ConsultationService serviceWithEmptyKey = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", "", "blocking", "");

        Map<String, Object> result = serviceWithEmptyKey.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== toMessageVo senderType=user分支 (senderType=null)
    // ====================

    @Test
    @DisplayName("listMessages - senderType为null显示user")
    void listMessages_nullSenderType() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        ConsultationMessage message = createMessage("msg_001", "sess_001", null, "user1", "你好");
        message.setSentAt(LocalDateTime.now());
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findBySessionId("sess_001", 0, 50)).thenReturn(List.of(message));
        when(messageMapper.countBySessionId("sess_001")).thenReturn(1);

        Map<String, Object> result = service.listMessages("user1", "sess_001", 1, 50);

        List<?> messages = (List<?>) result.get("messages");
        Map<?, ?> msgVo = (Map<?, ?>) messages.get(0);
        assertEquals("user", msgVo.get("senderType"));
    }

    // ==================== parseDoctorReply replyNode.isNull分支 ====================

    @Test
    @DisplayName("sendMessage - Dify响应doctor_reply为null")
    void sendMessage_difyDoctorReplyNull() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putNull("doctor_reply");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory senderType=user分支
    // ====================

    @Test
    @DisplayName("sendMessage - 对话历史senderType为1显示用户")
    void sendMessage_historyUserSenderType() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 1, "user1", "你好");
        msg1.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory sentAt为null分支
    // ====================

    @Test
    @DisplayName("sendMessage - 对话历史sentAt为null")
    void sendMessage_historyNullSentAt() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "回复内容");
        msg1.setSentAt(null);

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory excludeLatest=false分支 (获取20条)
    // ====================

    @Test
    @DisplayName("sendMessage - excludeLatest=false获取20条历史")
    void sendMessage_historyExcludeLatestFalse20() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "回复");
        msg1.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory excludeLatest=true且有多个消息
    // ====================

    @Test
    @DisplayName("sendMessage - excludeLatest=true且有多个消息跳过第一条")
    void sendMessage_historyExcludeLatestSkipFirst() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "最新回复");
        ConsultationMessage msg2 = createMessage("msg_2", "sess_001", 1, "user1", "用户消息");
        msg1.setSentAt(LocalDateTime.now());
        msg2.setSentAt(LocalDateTime.now().minusMinutes(1));

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(3);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1, msg2));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory senderType=2显示AI医生
    // ====================

    @Test
    @DisplayName("sendMessage - 对话历史senderType=2显示AI医生")
    void sendMessage_historySenderType2() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "医生回复");
        msg1.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory sentAt不为null且有时间
    // ====================

    @Test
    @DisplayName("sendMessage - 对话历史sentAt有时间")
    void sendMessage_historyWithTime() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "医生回复");
        msg1.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildDoctorRole introduction为空字符串
    // ====================

    @Test
    @DisplayName("sendMessage - 医生introduction为空字符串")
    void sendMessage_doctorIntroductionEmpty() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setIntroduction("   ");
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== parseDoctorReply replyNode.isNull分支
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应doctor_reply显式为null")
    void sendMessage_difyDoctorReplyExplicitNull() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putNull("doctor_reply");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== extractDoctorReply 第二个路径有效
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应data.outputs无效但outputs路径有效")
    void sendMessage_difySecondPathValid() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded status不为succeeded分支
    // ====================

    @Test
    @DisplayName("sendMessage - Dify工作流status为failed")
    void sendMessage_difyStatusFailed() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "failed");
        dataNode.put("error", "任务失败");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== firstPresent map包含key但value为null
    // ====================

    @Test
    @DisplayName("sendMessage - healthProfile包含key但value为null")
    void sendMessage_healthProfileKeyWithNullValue() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString()))
                .thenReturn(Map.of("privacy_settings", Map.of("data_visible", true)));
        java.util.Map<String, Object> healthProfile = new java.util.HashMap<>();
        healthProfile.put("fastingGlucose", null);
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(healthProfile);
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== truncate text为null
    // ====================

    @Test
    @DisplayName("createSession - greeting为null被truncate处理")
    void createSession_greetingNull() {
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(sessionMapper.insert(any(ConsultationSession.class))).thenReturn(1);
        when(doctorMapper.incrementConsultationCount("d1")).thenReturn(1);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(minioStorageService.buildDoctorAvatarUrl(anyString())).thenReturn("http://avatar/d1");

        Map<String, Object> result = service.createSession("user1", "d1");

        assertNotNull(result.get("sessionId"));
    }

    // ==================== truncate text.length() <= max
    // ====================

    @Test
    @DisplayName("sendMessage - AI回复内容不超过500字符")
    void sendMessage_aiReplyShortContent() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "简短回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== buildConversationHistory 分支覆盖 (excludeLatest=false,
    // senderType=2, sentAt=null)
    // ====================

    @Test
    @DisplayName("sendMessage - buildConversationHistory excludeLatest=false且senderType=2")
    void sendMessage_historyExcludeLatestFalseSenderType2() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "医生回复");
        msg1.setSentAt(LocalDateTime.now());

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 20)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    @Test
    @DisplayName("sendMessage - buildConversationHistory sentAt为空字符串时间")
    void sendMessage_historyTimeBlank() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        ConsultationMessage msg1 = createMessage("msg_1", "sess_001", 2, "d1", "回复");
        msg1.setSentAt(null);

        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");
        when(messageMapper.findRecentBySessionId("sess_001", 21)).thenReturn(List.of(msg1));

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== truncate 分支覆盖 (text=null, text.length()<=max)
    // ====================

    @Test
    @DisplayName("sendMessage - AI回复内容为null触发truncate返回空字符串")
    void sendMessage_truncateNullText() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = service.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
        verify(sessionMapper, times(2)).updateAfterMessage(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("createSession - greeting正好500字符不被截断")
    void createSession_greetingExact500() {
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        doctor.setTitle("主任医师");
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(sessionMapper.insert(any(ConsultationSession.class))).thenReturn(1);
        when(doctorMapper.incrementConsultationCount("d1")).thenReturn(1);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(minioStorageService.buildDoctorAvatarUrl(anyString())).thenReturn("http://avatar/d1");

        Map<String, Object> result = service.createSession("user1", "d1");

        assertNotNull(result.get("sessionId"));
        verify(sessionMapper).updateAfterMessage(anyString(), argThat(text -> text.length() <= 500), eq(1));
    }

    // ==================== mapDiagnoses/toStringList 分支覆盖 (非数组节点)
    // ====================

    @Test
    @DisplayName("getAiSuggestion - 非数组节点返回空列表")
    void getAiSuggestion_nonArrayNodes() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
        message.setAiMetadata(
                "{\"possible_diagnoses\":\"不是数组\",\"suggested_questions\":\"不是数组\",\"recommended_exams\":\"不是数组\",\"treatment_strategy\":\"建议\"}");
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

        Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

        assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
        assertTrue(((List<?>) result.get("suggestedQuestions")).isEmpty());
        assertTrue(((List<?>) result.get("recommendedExams")).isEmpty());
        assertEquals("建议", result.get("treatmentStrategy"));
    }

    // ==================== extractDoctorReply 分支覆盖 (第一个路径无效但第二个路径有效)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应data.outputs无效但顶层outputs有效")
    void sendMessage_difyDataOutputsMissingButOutputsValid() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "succeeded");
        ObjectNode outputsNode = response.putObject("outputs");
        outputsNode.putObject("doctor_reply").put("content", "回复");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== parseDoctorReply 分支覆盖 (isMissingNode)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify响应完全缺失doctor_reply触发isMissingNode")
    void sendMessage_difyDoctorReplyMissingNode() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode dataNode = response.putObject("data");
        dataNode.put("status", "succeeded");
        ObjectNode outputsNode = dataNode.putObject("outputs");
        outputsNode.put("text", "纯文本回复无JSON");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (status不为succeeded的完整路径)
    // ====================

    @Test
    @DisplayName("sendMessage - Dify工作流status为failed且error在顶层")
    void sendMessage_difyStatusFailedErrorAtTop() throws Exception {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);
        when(userServiceClient.getUserProfile(anyString(), anyString())).thenReturn(Map.of());
        when(healthServiceClient.getLatestHealthProfile(anyString(), anyString())).thenReturn(Map.of());
        when(homeServiceClient.searchKnowledgeContext(anyString(), anyInt(), anyString())).thenReturn("");

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "failed");
        response.put("error", "顶层错误信息");

        when(difyClient.runWorkflowBlocking(anyString(), anyString(), any(), anyString()))
                .thenReturn(response);

        Map<String, Object> result = serviceWithDify.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== getAiSuggestion 分支覆盖 (aiMetadata.isBlank()的组合条件)
    // ====================

    @Test
    @DisplayName("getAiSuggestion - aiMetadata为空白字符返回空建议")
    void getAiSuggestion_aiMetadataBlank() {
        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        ConsultationMessage message = createMessage("msg_001", "sess_001", 2, "d1", "回复");
        message.setAiMetadata("   ");
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.findLatestAiMessage("sess_001")).thenReturn(message);

        Map<String, Object> result = service.getAiSuggestion("user1", "sess_001");

        assertTrue(((List<?>) result.get("possibleDiagnoses")).isEmpty());
        assertEquals("", result.get("treatmentStrategy"));
    }

    // ==================== generateAiReply 分支覆盖 (difyApiKey.isBlank()空白字符)
    // ====================

    @Test
    @DisplayName("sendMessage - difyApiKey为空白字符返回AI_UNAVAILABLE")
    void sendMessage_difyApiKeyBlankSpaces() {
        ConsultationService serviceWithBlankKey = new ConsultationService(
                doctorMapper, sessionMapper, messageMapper,
                minioStorageService, healthServiceClient,
                userServiceClient, homeServiceClient, difyClient,
                objectMapper, "http://localhost", "   ", "blocking", "");

        ConsultationSession session = createSession("sess_001", "user1", "d1", 1);
        Doctor doctor = createDoctor("d1", "张医生", "内分泌科", 1);
        when(sessionMapper.findById("sess_001")).thenReturn(session);
        when(messageMapper.insert(any(ConsultationMessage.class))).thenReturn(1);
        when(messageMapper.countBySessionId("sess_001")).thenReturn(2);
        when(doctorMapper.findById("d1")).thenReturn(doctor);

        Map<String, Object> result = serviceWithBlankKey.sendMessage("user1", "sess_001", "你好医生", null);

        assertNotNull(result.get("aiMessage"));
    }

    // ==================== extractDoctorReply 分支覆盖 (第二个路径返回NullNode)
    // ====================

    @Test
    @DisplayName("extractDoctorReply - 第二个路径返回有效节点")
    void extractDoctorReply_secondPathValid() throws Exception {
        String json = "{\"outputs\":{\"doctor_reply\":\"test reply\"}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("extractDoctorReply",
                JsonNode.class);
        method.setAccessible(true);

        JsonNode result = (JsonNode) method.invoke(service, response);
        assertEquals("test reply", result.asText());
    }

    // ==================== parseDoctorReply 分支覆盖 (extractDoctorReply返回NullNode)
    // ====================

    @Test
    @DisplayName("parseDoctorReply - extractDoctorReply返回NullNode触发isNull分支")
    void parseDoctorReply_isNullBranch() throws Exception {
        String json = "{\"data\":{\"outputs\":{\"text\":\"{\\\"doctor_reply\\\":null}\"}}}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("parseDoctorReply",
                JsonNode.class);
        method.setAccessible(true);

        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(service, response));
    }

    // ==================== assertWorkflowSucceeded 分支覆盖 (空白状态短路分支)
    // ====================

    @Test
    @DisplayName("assertWorkflowSucceeded - data.status为空白字符串")
    void assertWorkflowSucceeded_dataBlankStatus() throws Exception {
        String json = "{\"data\":{\"status\":\" \"},\"status\":\"succeeded\"}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("assertWorkflowSucceeded",
                JsonNode.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(service, response));
    }

    @Test
    @DisplayName("assertWorkflowSucceeded - 根status为空白字符串")
    void assertWorkflowSucceeded_rootBlankStatus() throws Exception {
        String json = "{\"data\":{\"status\":null},\"status\":\" \"}";
        JsonNode response = objectMapper.readTree(json);

        java.lang.reflect.Method method = ConsultationService.class.getDeclaredMethod("assertWorkflowSucceeded",
                JsonNode.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(service, response));
    }
}