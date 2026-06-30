package com.diabetes.consultation.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Entity 实体类测试")
class EntityTests {

    @Nested
    @DisplayName("Doctor 医生实体测试")
    class DoctorTest {

        @Test
        @DisplayName("所有 getter/setter 测试")
        void allGettersSetters() {
            Doctor doctor = new Doctor();
            doctor.setDoctorId("d001");
            doctor.setName("张医生");
            doctor.setTitle("主任医师");
            doctor.setDepartment("内分泌科");
            doctor.setHospital("北京医院");
            doctor.setAvatarId("avatar_001");
            doctor.setIntroduction("资深内分泌专家");
            doctor.setSpecialties("糖尿病、甲状腺疾病");
            doctor.setRating(BigDecimal.valueOf(4.8));
            doctor.setConsultationCount(100);
            doctor.setStatus(1);

            assertEquals("d001", doctor.getDoctorId());
            assertEquals("张医生", doctor.getName());
            assertEquals("主任医师", doctor.getTitle());
            assertEquals("内分泌科", doctor.getDepartment());
            assertEquals("北京医院", doctor.getHospital());
            assertEquals("avatar_001", doctor.getAvatarId());
            assertEquals("资深内分泌专家", doctor.getIntroduction());
            assertEquals("糖尿病、甲状腺疾病", doctor.getSpecialties());
            assertEquals(BigDecimal.valueOf(4.8), doctor.getRating());
            assertEquals(100, doctor.getConsultationCount());
            assertEquals(1, doctor.getStatus());
        }

        @Test
        @DisplayName("null 值测试")
        void nullValues() {
            Doctor doctor = new Doctor();
            doctor.setDoctorId(null);
            doctor.setName(null);
            doctor.setTitle(null);
            doctor.setDepartment(null);
            doctor.setHospital(null);
            doctor.setAvatarId(null);
            doctor.setIntroduction(null);
            doctor.setSpecialties(null);
            doctor.setRating(null);
            doctor.setConsultationCount(null);
            doctor.setStatus(null);

            assertNull(doctor.getDoctorId());
            assertNull(doctor.getName());
            assertNull(doctor.getTitle());
            assertNull(doctor.getDepartment());
            assertNull(doctor.getHospital());
            assertNull(doctor.getAvatarId());
            assertNull(doctor.getIntroduction());
            assertNull(doctor.getSpecialties());
            assertNull(doctor.getRating());
            assertNull(doctor.getConsultationCount());
            assertNull(doctor.getStatus());
        }
    }

    @Nested
    @DisplayName("ConsultationSession 会话实体测试")
    class ConsultationSessionTest {

        @Test
        @DisplayName("所有 getter/setter 测试")
        void allGettersSetters() {
            LocalDateTime now = LocalDateTime.now();
            ConsultationSession session = new ConsultationSession();
            session.setSessionId("sess_001");
            session.setUserId("user_001");
            session.setDoctorId("d001");
            session.setDifyConversationId("conv_001");
            session.setDifyWorkflowId("wf_001");
            session.setStatus(1);
            session.setTitle("糖尿病咨询");
            session.setInitialMessage("你好医生");
            session.setLastMessage("建议控制饮食");
            session.setLastMessageAt(now);
            session.setMessageCount(5);
            session.setRating(5);
            session.setFeedback("非常满意");
            session.setCloseReason(1);
            session.setStartedAt(now);
            session.setEndedAt(now);

            assertEquals("sess_001", session.getSessionId());
            assertEquals("user_001", session.getUserId());
            assertEquals("d001", session.getDoctorId());
            assertEquals("conv_001", session.getDifyConversationId());
            assertEquals("wf_001", session.getDifyWorkflowId());
            assertEquals(1, session.getStatus());
            assertEquals("糖尿病咨询", session.getTitle());
            assertEquals("你好医生", session.getInitialMessage());
            assertEquals("建议控制饮食", session.getLastMessage());
            assertEquals(now, session.getLastMessageAt());
            assertEquals(5, session.getMessageCount());
            assertEquals(5, session.getRating());
            assertEquals("非常满意", session.getFeedback());
            assertEquals(1, session.getCloseReason());
            assertEquals(now, session.getStartedAt());
            assertEquals(now, session.getEndedAt());
        }

        @Test
        @DisplayName("null 值测试")
        void nullValues() {
            ConsultationSession session = new ConsultationSession();
            session.setSessionId(null);
            session.setUserId(null);
            session.setDoctorId(null);
            session.setDifyConversationId(null);
            session.setDifyWorkflowId(null);
            session.setStatus(null);
            session.setTitle(null);
            session.setInitialMessage(null);
            session.setLastMessage(null);
            session.setLastMessageAt(null);
            session.setMessageCount(null);
            session.setRating(null);
            session.setFeedback(null);
            session.setCloseReason(null);
            session.setStartedAt(null);
            session.setEndedAt(null);

            assertNull(session.getSessionId());
            assertNull(session.getUserId());
            assertNull(session.getDoctorId());
            assertNull(session.getDifyConversationId());
            assertNull(session.getDifyWorkflowId());
            assertNull(session.getStatus());
            assertNull(session.getTitle());
            assertNull(session.getInitialMessage());
            assertNull(session.getLastMessage());
            assertNull(session.getLastMessageAt());
            assertNull(session.getMessageCount());
            assertNull(session.getRating());
            assertNull(session.getFeedback());
            assertNull(session.getCloseReason());
            assertNull(session.getStartedAt());
            assertNull(session.getEndedAt());
        }
    }

    @Nested
    @DisplayName("ConsultationMessage 消息实体测试")
    class ConsultationMessageTest {

        @Test
        @DisplayName("所有 getter/setter 测试")
        void allGettersSetters() {
            LocalDateTime now = LocalDateTime.now();
            ConsultationMessage message = new ConsultationMessage();
            message.setMessageId("msg_001");
            message.setSessionId("sess_001");
            message.setSenderType(1);
            message.setSenderId("user_001");
            message.setMessageType(1);
            message.setContent("你好医生");
            message.setIsAiGenerated(0);
            message.setAiMetadata("{\"suggestion\":\"建议\"}");
            message.setIsRead(1);
            message.setSentAt(now);

            assertEquals("msg_001", message.getMessageId());
            assertEquals("sess_001", message.getSessionId());
            assertEquals(1, message.getSenderType());
            assertEquals("user_001", message.getSenderId());
            assertEquals(1, message.getMessageType());
            assertEquals("你好医生", message.getContent());
            assertEquals(0, message.getIsAiGenerated());
            assertEquals("{\"suggestion\":\"建议\"}", message.getAiMetadata());
            assertEquals(1, message.getIsRead());
            assertEquals(now, message.getSentAt());
        }

        @Test
        @DisplayName("null 值测试")
        void nullValues() {
            ConsultationMessage message = new ConsultationMessage();
            message.setMessageId(null);
            message.setSessionId(null);
            message.setSenderType(null);
            message.setSenderId(null);
            message.setMessageType(null);
            message.setContent(null);
            message.setIsAiGenerated(null);
            message.setAiMetadata(null);
            message.setIsRead(null);
            message.setSentAt(null);

            assertNull(message.getMessageId());
            assertNull(message.getSessionId());
            assertNull(message.getSenderType());
            assertNull(message.getSenderId());
            assertNull(message.getMessageType());
            assertNull(message.getContent());
            assertNull(message.getIsAiGenerated());
            assertNull(message.getAiMetadata());
            assertNull(message.getIsRead());
            assertNull(message.getSentAt());
        }
    }
}