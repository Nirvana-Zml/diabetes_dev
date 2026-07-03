package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.IdGenerator;
import com.diabetes.common.util.JwtUtil;
import com.diabetes.user.dto.*;
import com.diabetes.user.entity.Admin;
import com.diabetes.user.entity.User;
import com.diabetes.user.mapper.AdminMapper;
import com.diabetes.user.mapper.UserMapper;
import com.diabetes.user.util.MailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;
    private final MailUtil mailUtil;
    private final String jwtSecret;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;

    public AuthService(UserMapper userMapper,
                       AdminMapper adminMapper,
                       PasswordEncoder passwordEncoder,
                       VerifyCodeService verifyCodeService,
                       MailUtil mailUtil,
                       @Value("${jwt.secret}") String jwtSecret,
                       @Value("${jwt.access-expire-seconds}") long accessExpireSeconds,
                       @Value("${jwt.refresh-expire-seconds}") long refreshExpireSeconds) {
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.verifyCodeService = verifyCodeService;
        this.mailUtil = mailUtil;
        this.jwtSecret = jwtSecret;
        this.accessExpireSeconds = accessExpireSeconds;
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = userMapper.findByUsername(username);
        if (user != null) {
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BusinessException(401, "用户名或密码错误");
            }
            return buildTokenResponse(user.getUserId(), user.getUsername(), "user");
        }
        Admin admin = adminMapper.findByUsername(username);
        if (admin == null || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return buildTokenResponse(admin.getAdminId(), admin.getUsername(), "admin");
    }

    @Transactional
    public String register(RegisterRequest request) {
        String username = request.username().trim();
        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        if (userMapper.countByPhone(request.phone()) > 0) {
            throw new BusinessException(400, "手机号已被注册");
        }

        User user = new User();
        user.setUserId(IdGenerator.nextId("u_"));
        user.setUsername(username);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPoints(0);
        user.setGender(0);
        user.setDeleted(false);
        userMapper.insert(user);
        return user.getUserId();
    }

    public void sendVerifyCode(String account, String type, String purpose) {
        if (!StringUtils.hasText(account)) {
            throw new BusinessException(400, "请输入" + ("phone".equals(type) ? "手机号" : "邮箱"));
        }
        String normalizedAccount = account.trim();
        String normalizedType = StringUtils.hasText(type) ? type.trim() : "phone";
        String normalizedPurpose = StringUtils.hasText(purpose) ? purpose.trim() : "bind";

        if ("email".equals(normalizedType)) {
            if (!normalizedAccount.matches(EMAIL_PATTERN)) {
                throw new BusinessException(400, "邮箱格式不正确");
            }
            if ("reset_password".equals(normalizedPurpose) && userMapper.findByEmail(normalizedAccount) == null) {
                throw new BusinessException(404, "该邮箱未注册");
            }
        } else {
            if (!normalizedAccount.matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException(400, "手机号格式不正确");
            }
            if ("reset_password".equals(normalizedPurpose) && userMapper.findByPhone(normalizedAccount) == null) {
                throw new BusinessException(404, "该手机号未注册");
            }
        }

        String code = verifyCodeService.generateAndStore(normalizedAccount);
        if ("email".equals(normalizedType)) {
            String subject = "【糖尿病智能助手】验证码";
            String text = buildEmailText(code, normalizedPurpose);
            mailUtil.sendMail(normalizedAccount, subject, text);
        } else {
            log.info("手机验证码（开发环境，未接入短信）: phone={}, code={}", maskPhone(normalizedAccount), code);
        }
    }

    @Transactional
    public String resetPassword(String account, String code, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BusinessException(400, "密码长度应为 6-32 位");
        }

        String key = account.trim();
        verifyCodeService.verifyOrThrow(key, code);

        User user = userMapper.findByUsername(key);
        if (user == null) {
            user = userMapper.findByPhone(key);
        }
        if (user == null) {
            user = userMapper.findByEmail(key);
        }
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }

        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword));
        verifyCodeService.remove(key);
        return user.getUserId();
    }

    private String buildEmailText(String code, String purpose) {
        String action = "reset_password".equals(purpose) ? "重置密码" : "绑定邮箱";
        return """
                您好！

                您正在进行%s操作，验证码为：%s

                验证码 5 分钟内有效，请勿泄露给他人。如非本人操作，请忽略本邮件。

                —— 糖尿病智能助手
                """.formatted(action, code);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private TokenResponse buildTokenResponse(String subjectId, String username, String role) {
        String accessToken = JwtUtil.generateToken(jwtSecret, subjectId, role, accessExpireSeconds);
        String refreshToken = JwtUtil.generateToken(jwtSecret, subjectId, role, refreshExpireSeconds);
        return new TokenResponse(accessToken, refreshToken, subjectId, username, role);
    }
}
