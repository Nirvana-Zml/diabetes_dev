package com.diabetes.user.util;

import com.diabetes.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MailUtil {

    private static final Logger log = LoggerFactory.getLogger(MailUtil.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailUtil(JavaMailSender mailSender,
                    @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendMail(String to, String subject, String text) {
        if (!StringUtils.hasText(fromAddress)) {
            throw new BusinessException(500, "邮件服务未配置，请联系管理员");
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("验证码邮件已发送至 {}", maskEmail(to));
        } catch (Exception e) {
            log.error("邮件发送失败: to={}", maskEmail(to), e);
            throw new BusinessException(500, "邮件发送失败，请稍后重试");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
