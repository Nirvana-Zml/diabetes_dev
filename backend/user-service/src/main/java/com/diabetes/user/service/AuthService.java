package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.util.IdGenerator;
import com.diabetes.common.util.JwtUtil;
import com.diabetes.user.dto.*;
import com.diabetes.user.entity.Admin;
import com.diabetes.user.entity.User;
import com.diabetes.user.mapper.AdminMapper;
import com.diabetes.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final String jwtSecret;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;
    private final Map<String, String> verifyCodes = new ConcurrentHashMap<>();

    public AuthService(UserMapper userMapper,
                       AdminMapper adminMapper,
                       PasswordEncoder passwordEncoder,
                       @Value("${jwt.secret}") String jwtSecret,
                       @Value("${jwt.access-expire-seconds}") long accessExpireSeconds,
                       @Value("${jwt.refresh-expire-seconds}") long refreshExpireSeconds) {
        this.userMapper = userMapper;
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
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
    public void register(RegisterRequest request) {
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
    }

    public void sendVerifyCode(String account, String type) {
        if (account == null || account.isBlank()) {
            throw new BusinessException(400, "请输入" + ("phone".equals(type) ? "手机号" : "邮箱"));
        }
        verifyCodes.put(account.trim(), "123456");
    }

    @Transactional
    public void resetPassword(String account, String code, String newPassword) {
        String key = account.trim();
        String saved = verifyCodes.get(key);
        if (saved == null || !saved.equals(code)) {
            throw new BusinessException(400, "验证码错误或已过期");
        }

        User user = userMapper.findByUsername(key);
        if (user == null) {
            user = userMapper.findByPhone(key);
        }
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }

        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword));
        verifyCodes.remove(key);
    }

    private TokenResponse buildTokenResponse(String subjectId, String username, String role) {
        String accessToken = JwtUtil.generateToken(jwtSecret, subjectId, role, accessExpireSeconds);
        String refreshToken = JwtUtil.generateToken(jwtSecret, subjectId, role, refreshExpireSeconds);
        return new TokenResponse(accessToken, refreshToken, subjectId, username, role);
    }
}
