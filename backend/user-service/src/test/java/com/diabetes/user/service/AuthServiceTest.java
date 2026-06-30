package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.user.dto.LoginRequest;
import com.diabetes.user.dto.RegisterRequest;
import com.diabetes.user.dto.TokenResponse;
import com.diabetes.user.entity.Admin;
import com.diabetes.user.entity.User;
import com.diabetes.user.mapper.AdminMapper;
import com.diabetes.user.mapper.UserMapper;
import com.diabetes.user.util.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-must-be-long-enough-32bytes";

    @Mock
    private UserMapper userMapper;
    @Mock
    private AdminMapper adminMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private VerifyCodeService verifyCodeService;
    @Mock
    private MailUtil mailUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userMapper, adminMapper, passwordEncoder,
                verifyCodeService, mailUtil,
                JWT_SECRET, 3600L, 86400L);
    }

    @Test
    void login_userSuccess() {
        User user = user("u_1", "alice", "hash");
        when(userMapper.findByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        TokenResponse response = authService.login(new LoginRequest("alice", "password"));

        assertEquals("u_1", response.user_id());
        assertEquals("alice", response.username());
        assertEquals("user", response.role());
        assertNotNull(response.access_token());
        assertNotNull(response.refresh_token());
    }

    @Test
    void login_userWrongPassword() {
        User user = user("u_1", "alice", "hash");
        when(userMapper.findByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("alice", "wrong")));
        assertEquals(401, ex.getCode());
    }

    @Test
    void login_adminSuccess() {
        when(userMapper.findByUsername("admin")).thenReturn(null);
        Admin admin = new Admin();
        admin.setAdminId("a_1");
        admin.setUsername("admin");
        admin.setPasswordHash("adminHash");
        when(adminMapper.findByUsername("admin")).thenReturn(admin);
        when(passwordEncoder.matches("password", "adminHash")).thenReturn(true);

        TokenResponse response = authService.login(new LoginRequest("admin", "password"));

        assertEquals("a_1", response.user_id());
        assertEquals("admin", response.role());
    }

    @Test
    void login_adminNotFound() {
        when(userMapper.findByUsername("nobody")).thenReturn(null);
        when(adminMapper.findByUsername("nobody")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("nobody", "password")));
    }

    @Test
    void login_adminWrongPassword() {
        when(userMapper.findByUsername("admin")).thenReturn(null);
        Admin admin = new Admin();
        admin.setAdminId("a_1");
        admin.setUsername("admin");
        admin.setPasswordHash("adminHash");
        when(adminMapper.findByUsername("admin")).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "adminHash")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "wrong")));
    }

    @Test
    void register_success() {
        when(userMapper.countByUsername("newuser")).thenReturn(0);
        when(userMapper.countByPhone("13800138000")).thenReturn(0);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");

        authService.register(new RegisterRequest("newuser", "13800138000", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("newuser", inserted.getUsername());
        assertEquals("13800138000", inserted.getPhone());
        assertEquals("encoded", inserted.getPasswordHash());
        assertEquals(0, inserted.getPoints());
        assertEquals(0, inserted.getGender());
        assertFalse(inserted.getDeleted());
        assertTrue(inserted.getUserId().startsWith("u_"));
    }

    @Test
    void register_usernameExists() {
        when(userMapper.countByUsername("exists")).thenReturn(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest("exists", "13800138000", "password123")));
        assertEquals(400, ex.getCode());
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    void register_phoneExists() {
        when(userMapper.countByUsername("newuser")).thenReturn(0);
        when(userMapper.countByPhone("13800138000")).thenReturn(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest("newuser", "13800138000", "password123")));
        assertEquals(400, ex.getCode());
        assertEquals("手机号已被注册", ex.getMessage());
    }

    @Test
    void sendVerifyCode_emptyPhoneAccount() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendVerifyCode("", "phone", "bind"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("手机号"));
    }

    @Test
    void sendVerifyCode_emptyEmailAccount() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendVerifyCode("  ", "email", "bind"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("邮箱"));
    }

    @Test
    void sendVerifyCode_invalidEmail() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendVerifyCode("bad-email", "email", "bind"));
        assertEquals(400, ex.getCode());
        assertEquals("邮箱格式不正确", ex.getMessage());
    }

    @Test
    void sendVerifyCode_invalidPhone() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendVerifyCode("12345", "phone", "bind"));
        assertEquals(400, ex.getCode());
        assertEquals("手机号格式不正确", ex.getMessage());
    }

    @Test
    void sendVerifyCode_emailResetPasswordNotRegistered() {
        when(userMapper.findByEmail("user@test.com")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendVerifyCode("user@test.com", "email", "reset_password"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void sendVerifyCode_phoneResetPasswordNotRegistered() {
        when(userMapper.findByPhone("13800138000")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.sendVerifyCode("13800138000", "phone", "reset_password"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void sendVerifyCode_emailBind() {
        when(verifyCodeService.generateAndStore("user@test.com")).thenReturn("123456");
        authService.sendVerifyCode("user@test.com", "email", "bind");
        verify(mailUtil).sendMail(eq("user@test.com"), contains("验证码"), contains("123456"));
    }

    @Test
    void sendVerifyCode_emailResetPassword() {
        when(userMapper.findByEmail("user@test.com")).thenReturn(user("u_1", "alice", "h"));
        when(verifyCodeService.generateAndStore("user@test.com")).thenReturn("654321");
        authService.sendVerifyCode("user@test.com", "email", "reset_password");
        verify(mailUtil).sendMail(eq("user@test.com"), anyString(), contains("重置密码"));
    }

    @Test
    void sendVerifyCode_phoneBind() {
        when(verifyCodeService.generateAndStore("13800138000")).thenReturn("111222");
        assertDoesNotThrow(() -> authService.sendVerifyCode("13800138000", null, null));
        verify(verifyCodeService).generateAndStore("13800138000");
    }

    @Test
    void resetPassword_invalidLength() {
        assertThrows(BusinessException.class,
                () -> authService.resetPassword("alice", "123456", "short"));
        assertThrows(BusinessException.class,
                () -> authService.resetPassword("alice", "123456", ""));
    }

    @Test
    void resetPassword_byUsername() {
        User user = user("u_1", "alice", "old");
        when(userMapper.findByUsername("alice")).thenReturn(user);
        when(passwordEncoder.encode("newpass123")).thenReturn("newHash");

        authService.resetPassword("alice", "123456", "newpass123");

        verify(verifyCodeService).verifyOrThrow("alice", "123456");
        verify(userMapper).updatePassword("u_1", "newHash");
        verify(verifyCodeService).remove("alice");
    }

    @Test
    void resetPassword_byPhone() {
        when(userMapper.findByUsername("13800138000")).thenReturn(null);
        User user = user("u_1", "alice", "old");
        user.setPhone("13800138000");
        when(userMapper.findByPhone("13800138000")).thenReturn(user);
        when(passwordEncoder.encode("newpass123")).thenReturn("newHash");

        authService.resetPassword("13800138000", "123456", "newpass123");

        verify(userMapper).updatePassword("u_1", "newHash");
    }

    @Test
    void resetPassword_byEmail() {
        when(userMapper.findByUsername("user@test.com")).thenReturn(null);
        when(userMapper.findByPhone("user@test.com")).thenReturn(null);
        User user = user("u_1", "alice", "old");
        user.setEmail("user@test.com");
        when(userMapper.findByEmail("user@test.com")).thenReturn(user);
        when(passwordEncoder.encode("newpass123")).thenReturn("newHash");

        authService.resetPassword("user@test.com", "123456", "newpass123");

        verify(userMapper).updatePassword("u_1", "newHash");
    }

    @Test
    void resetPassword_userNotFound() {
        when(userMapper.findByUsername("nobody")).thenReturn(null);
        when(userMapper.findByPhone("nobody")).thenReturn(null);
        when(userMapper.findByEmail("nobody")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.resetPassword("nobody", "123456", "newpass123"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void resetPassword_passwordTooLong() {
        assertThrows(BusinessException.class,
                () -> authService.resetPassword("alice", "123456", "a".repeat(33)));
    }

    @Test
    void maskPhone_shortPhone() throws Exception {
        Method method = AuthService.class.getDeclaredMethod("maskPhone", String.class);
        method.setAccessible(true);
        assertEquals("***", method.invoke(authService, "123"));
        assertEquals("***", method.invoke(authService, (Object) null));
        assertEquals("138****8000", method.invoke(authService, "13800138000"));
    }

    private User user(String id, String username, String hash) {
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setPasswordHash(hash);
        return user;
    }
}
