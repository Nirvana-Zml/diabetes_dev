package com.diabetes.user.util;

import com.diabetes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailUtilTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailUtil mailUtil;

    @Test
    void sendMail_noFromAddress() {
        ReflectionTestUtils.setField(mailUtil, "fromAddress", "");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> mailUtil.sendMail("user@test.com", "subject", "body"));
        assertEquals(500, ex.getCode());
        assertEquals("邮件服务未配置，请联系管理员", ex.getMessage());
    }

    @Test
    void sendMail_success() {
        ReflectionTestUtils.setField(mailUtil, "fromAddress", "noreply@test.com");
        mailUtil.sendMail("user@example.com", "验证码", "123456");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertEquals("noreply@test.com", msg.getFrom());
        assertEquals("user@example.com", msg.getTo()[0]);
        assertEquals("验证码", msg.getSubject());
        assertEquals("123456", msg.getText());
    }

    @Test
    void sendMail_failure() {
        ReflectionTestUtils.setField(mailUtil, "fromAddress", "noreply@test.com");
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> mailUtil.sendMail("user@example.com", "sub", "text"));
        assertEquals(500, ex.getCode());
        assertEquals("邮件发送失败，请稍后重试", ex.getMessage());
    }

    @Test
    void sendMail_masksSingleCharLocalPart() {
        ReflectionTestUtils.setField(mailUtil, "fromAddress", "noreply@test.com");
        mailUtil.sendMail("ab@test.com", "sub", "text");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendMail_masksShortEmail() {
        ReflectionTestUtils.setField(mailUtil, "fromAddress", "noreply@test.com");
        mailUtil.sendMail("a@test.com", "sub", "text");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendMail_masksNullEmail() {
        ReflectionTestUtils.setField(mailUtil, "fromAddress", "noreply@test.com");
        mailUtil.sendMail("invalid", "sub", "text");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
