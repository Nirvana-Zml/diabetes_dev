package com.diabetes.user.service;

import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.user.dto.*;
import com.diabetes.user.entity.User;
import com.diabetes.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private VerifyCodeService verifyCodeService;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(
                userMapper, passwordEncoder, objectMapper, minioStorageService, verifyCodeService);
    }

    @Test
    void getUserOrThrow_notFound() {
        when(userMapper.findById("u_missing")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userProfileService.getUserOrThrow("u_missing"));
        assertEquals(404, ex.getCode());
    }

    @Test
    void getProfile_success() throws Exception {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);
        when(objectMapper.readValue(eq("{\"show\":true}"), any(TypeReference.class)))
                .thenReturn(Map.of("show", true));

        UserProfileResponse profile = userProfileService.getProfile("u_1");

        assertEquals("u_1", profile.user_id());
        assertEquals("alice", profile.username());
        assertEquals("Alice", profile.nickname());
        assertEquals(Map.of("show", true), profile.privacy_settings());
    }

    @Test
    void getProfile_invalidPrivacyJson() throws Exception {
        User user = sampleUser();
        user.setPrivacySettings("not-json");
        when(userMapper.findById("u_1")).thenReturn(user);
        when(objectMapper.readValue(eq("not-json"), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("bad") {});

        UserProfileResponse profile = userProfileService.getProfile("u_1");
        assertEquals("not-json", profile.privacy_settings());
    }

    @Test
    void getProfile_noPrivacy() {
        User user = sampleUser();
        user.setPrivacySettings(null);
        when(userMapper.findById("u_1")).thenReturn(user);

        UserProfileResponse profile = userProfileService.getProfile("u_1");
        assertNull(profile.privacy_settings());
    }

    @Test
    void getOverview_success() {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);
        when(minioStorageService.buildProfileAvatarUrl("u_1")).thenReturn("http://minio/avatar/u_1.jpg");

        UserOverviewResponse overview = userProfileService.getOverview("u_1");

        assertEquals("u_1", overview.user_id());
        assertEquals("alice", overview.username());
        assertEquals(100, overview.points());
        assertTrue(overview.avatar_url().contains("http://minio/avatar/u_1.jpg"));
    }

    @Test
    void updateProfile_allFields() {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest("Bob", null, null, null, 1, "1990-05-20");
        userProfileService.updateProfile("u_1", request);

        verify(userMapper).updateProfile(user);
        assertEquals("Bob", user.getNickname());
        assertEquals(1, user.getGender());
        assertEquals(LocalDate.parse("1990-05-20"), user.getBirthDate());
    }

    @Test
    void updateProfile_nullFields() {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);

        userProfileService.updateProfile("u_1", new UpdateProfileRequest(null, null, null, null, null, ""));

        verify(userMapper).updateProfile(user);
        assertEquals("Alice", user.getNickname());
    }

    @Test
    void updateProfile_nullBirthDate() throws Exception {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("show", true));

        userProfileService.updateProfile("u_1",
                new UpdateProfileRequest(null, null, null, null, null, null));

        assertEquals(LocalDate.of(1995, 3, 15), user.getBirthDate());
    }

    @Test
    void bindEmail_success() {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);
        when(userMapper.findByEmail("new@test.com")).thenReturn(null);

        BindEmailRequest request = new BindEmailRequest("new@test.com", "123456");
        userProfileService.bindEmail("u_1", request);

        verify(verifyCodeService).verifyOrThrow("new@test.com", "123456");
        assertEquals("new@test.com", user.getEmail());
        verify(verifyCodeService).remove("new@test.com");
    }

    @Test
    void bindEmail_takenByOther() {
        User other = sampleUser();
        other.setUserId("u_2");
        when(userMapper.findByEmail("taken@test.com")).thenReturn(other);

        assertThrows(BusinessException.class,
                () -> userProfileService.bindEmail("u_1",
                        new BindEmailRequest("taken@test.com", "123456")));
    }

    @Test
    void bindPhone_success() {
        User user = sampleUser();
        when(userMapper.findById("u_1")).thenReturn(user);
        when(userMapper.findByPhone("13900139000")).thenReturn(null);

        userProfileService.bindPhone("u_1", new BindPhoneRequest("13900139000", "123456"));

        assertEquals("13900139000", user.getPhone());
        verify(verifyCodeService).remove("13900139000");
    }

    @Test
    void bindPhone_takenByOther() {
        User other = sampleUser();
        other.setUserId("u_2");
        when(userMapper.findByPhone("13900139000")).thenReturn(other);

        assertThrows(BusinessException.class,
                () -> userProfileService.bindPhone("u_1",
                        new BindPhoneRequest("13900139000", "123456")));
    }

    @Test
    void bindPhone_sameUserOwnPhone() throws Exception {
        User user = sampleUser();
        user.setPhone("13900139000");
        when(userMapper.findByPhone("13900139000")).thenReturn(user);
        when(userMapper.findById("u_1")).thenReturn(user);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("show", true));

        userProfileService.bindPhone("u_1", new BindPhoneRequest("13900139000", "123456"));

        verify(userMapper).updateProfile(user);
    }

    @Test
    void uploadAvatar_nullFile() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", null));
    }

    @Test
    void uploadAvatar_emptyFile() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", file));
    }

    @Test
    void uploadAvatar_tooLarge() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        byte[] data = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", data);
        assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", file));
    }

    @Test
    void uploadAvatar_invalidContentType() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", file));
    }

    @Test
    void uploadAvatar_success() throws Exception {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        when(minioStorageService.buildProfileAvatarUrl("u_1")).thenReturn("http://minio/avatar/u_1.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "img".getBytes());

        AvatarUploadResponse response = userProfileService.uploadAvatar("u_1", file);

        verify(minioStorageService).uploadProfileAvatar(eq("u_1"), any(), eq(3L), eq("image/jpeg"));
        verify(userMapper).updateAvatarId("u_1", "u_1.jpg");
        assertTrue(response.avatar_url().contains("?v="));
    }

    @Test
    void uploadAvatar_minioFailure() throws Exception {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "img".getBytes());
        doThrow(new RuntimeException("minio down")).when(minioStorageService)
                .uploadProfileAvatar(anyString(), any(), anyLong(), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", file));
        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("头像上传失败"));
    }

    @Test
    void uploadAvatar_businessExceptionRethrown() throws Exception {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "img".getBytes());
        doThrow(new BusinessException(400, "bad")).when(minioStorageService)
                .uploadProfileAvatar(anyString(), any(), anyLong(), anyString());

        assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", file));
    }

    @Test
    void changePassword_wrongOld() {
        User user = sampleUser();
        user.setPasswordHash("hash");
        when(userMapper.findById("u_1")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> userProfileService.changePassword("u_1",
                        new ChangePasswordRequest("wrong", "newpass123")));
    }

    @Test
    void changePassword_success() {
        User user = sampleUser();
        user.setPasswordHash("hash");
        when(userMapper.findById("u_1")).thenReturn(user);
        when(passwordEncoder.matches("oldpass", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("newHash");

        userProfileService.changePassword("u_1",
                new ChangePasswordRequest("oldpass", "newpass123"));

        verify(userMapper).updatePassword("u_1", "newHash");
    }

    @Test
    void updatePrivacy_emptySettings() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        assertThrows(BusinessException.class,
                () -> userProfileService.updatePrivacy("u_1",
                        new PrivacySettingsRequest(Map.of())));
    }

    @Test
    void updatePrivacy_nullSettings() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        assertThrows(BusinessException.class,
                () -> userProfileService.updatePrivacy("u_1",
                        new PrivacySettingsRequest(null)));
    }

    @Test
    void updatePrivacy_success() throws Exception {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"a\":1}");

        userProfileService.updatePrivacy("u_1",
                new PrivacySettingsRequest(Map.of("a", 1)));

        verify(userMapper).updatePrivacy("u_1", "{\"a\":1}");
    }

    @Test
    void updatePrivacy_jsonError() throws Exception {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("err") {});

        assertThrows(BusinessException.class,
                () -> userProfileService.updatePrivacy("u_1",
                        new PrivacySettingsRequest(Map.of("a", 1))));
    }

    @Test
    void uploadAvatar_nullContentType() {
        when(userMapper.findById("u_1")).thenReturn(sampleUser());
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", null, "img".getBytes());
        assertThrows(BusinessException.class,
                () -> userProfileService.uploadAvatar("u_1", file));
    }

    @Test
    void bindEmail_sameUserOwnEmail() {
        User user = sampleUser();
        user.setEmail("same@test.com");
        when(userMapper.findByEmail("same@test.com")).thenReturn(user);
        when(userMapper.findById("u_1")).thenReturn(user);

        userProfileService.bindEmail("u_1", new BindEmailRequest("same@test.com", "123456"));

        verify(userMapper).updateProfile(user);
    }

    @Test
    void getProfile_blankPrivacy() {
        User user = sampleUser();
        user.setPrivacySettings("   ");
        when(userMapper.findById("u_1")).thenReturn(user);

        UserProfileResponse profile = userProfileService.getProfile("u_1");
        assertNull(profile.privacy_settings());
    }

    @Test
    void getProfile_nullBirthDateAndCreatedAt() {
        User user = sampleUser();
        user.setBirthDate(null);
        user.setCreatedAt(null);
        user.setPrivacySettings(null);
        when(userMapper.findById("u_1")).thenReturn(user);

        UserProfileResponse profile = userProfileService.getProfile("u_1");

        assertNull(profile.birth_date());
        assertNull(profile.created_at());
        assertNull(profile.privacy_settings());
    }

    @Test
    void withAvatarCacheBuster_usesCurrentTimeWhenUpdatedAtNull() throws Exception {
        Method method = UserProfileService.class.getDeclaredMethod(
                "withAvatarCacheBuster", String.class, LocalDateTime.class);
        method.setAccessible(true);

        String result = (String) method.invoke(userProfileService, "http://avatar.jpg", null);
        assertTrue(result.startsWith("http://avatar.jpg?v="));
    }

    @Test
    void resolveAvatarDisplayUrl_httpWithoutUpdatedAt() throws Exception {
        Method method = UserProfileService.class.getDeclaredMethod("resolveAvatarDisplayUrl", User.class);
        method.setAccessible(true);
        User user = sampleUser();
        user.setAvatarId("http://cdn.example.com/a.jpg");
        user.setUpdatedAt(null);

        String url = (String) method.invoke(userProfileService, user);
        assertTrue(url.contains("http://cdn.example.com/a.jpg?v="));
    }

    @Test
    void resolveAvatarDisplayUrl_blankAvatarId() throws Exception {
        Method method = UserProfileService.class.getDeclaredMethod("resolveAvatarDisplayUrl", User.class);
        method.setAccessible(true);
        User user = sampleUser();
        user.setAvatarId("   ");
        assertEquals("", method.invoke(userProfileService, user));
    }

    @Test
    void resolveAvatarDisplayUrl_variants() throws Exception {
        Method method = UserProfileService.class.getDeclaredMethod("resolveAvatarDisplayUrl", User.class);
        method.setAccessible(true);

        User noAvatar = sampleUser();
        noAvatar.setAvatarId(null);
        assertEquals("", method.invoke(userProfileService, noAvatar));

        User httpAvatar = sampleUser();
        httpAvatar.setAvatarId("https://cdn.example.com/a.jpg");
        httpAvatar.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        assertTrue(((String) method.invoke(userProfileService, httpAvatar)).contains("?v="));

        User minioAvatar = sampleUser();
        minioAvatar.setAvatarId("u_1.jpg");
        when(minioStorageService.buildProfileAvatarUrl("u_1")).thenReturn("http://minio/u_1.jpg");
        assertTrue(((String) method.invoke(userProfileService, minioAvatar)).contains("http://minio/u_1.jpg"));
    }

    @Test
    void withAvatarCacheBuster_edgeCases() throws Exception {
        Method method = UserProfileService.class.getDeclaredMethod(
                "withAvatarCacheBuster", String.class, LocalDateTime.class);
        method.setAccessible(true);

        assertNull(method.invoke(userProfileService, null, null));
        assertEquals("", method.invoke(userProfileService, "", null));
        assertEquals("http://x?v=1", method.invoke(userProfileService, "http://x?v=1", null));
        String withTime = (String) method.invoke(userProfileService, "http://x",
                LocalDateTime.of(2024, 6, 1, 12, 0));
        assertTrue(withTime.startsWith("http://x?v="));
    }

    private User sampleUser() {
        User user = new User();
        user.setUserId("u_1");
        user.setUsername("alice");
        user.setPhone("13800138000");
        user.setEmail("alice@test.com");
        user.setNickname("Alice");
        user.setGender(2);
        user.setBirthDate(LocalDate.of(1995, 3, 15));
        user.setPoints(100);
        user.setAvatarId("u_1.jpg");
        user.setPrivacySettings("{\"show\":true}");
        user.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        user.setUpdatedAt(LocalDateTime.of(2024, 6, 1, 0, 0));
        return user;
    }
}
