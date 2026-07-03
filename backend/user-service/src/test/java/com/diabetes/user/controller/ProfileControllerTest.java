package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.client.AuditServiceClient;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.user.config.JwtAuthInterceptor;
import com.diabetes.user.dto.*;
import com.diabetes.user.service.DataExportService;
import com.diabetes.user.service.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private DataExportService dataExportService;
    @Mock
    private AuditServiceClient auditServiceClient;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ProfileController profileController;

    @BeforeEach
    void setUp() {
        when(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).thenReturn("u_1");
    }

    @Test
    void overview() {
        UserOverviewResponse overview = new UserOverviewResponse(
                "u_1", "alice", "A", "http://avatar", 100, "138");
        when(userProfileService.getOverview("u_1")).thenReturn(overview);

        ApiResponse<UserOverviewResponse> response = profileController.overview(request);

        assertEquals(overview, response.data());
    }

    @Test
    void getProfile() {
        UserProfileResponse profile = new UserProfileResponse(
                "u_1", "alice", "138", "a@b.com", "", "A", 1, "1990-01-01", 10, null, "2024-01-01");
        when(userProfileService.getProfile("u_1")).thenReturn(profile);

        assertEquals(profile, profileController.getProfile(request).data());
    }

    @Test
    void uploadAvatar() {
        AvatarUploadResponse avatar = new AvatarUploadResponse("http://avatar");
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "x".getBytes());
        when(userProfileService.uploadAvatar("u_1", file)).thenReturn(avatar);

        assertEquals(avatar, profileController.uploadAvatar(request, file).data());
    }

    @Test
    void updateProfile() {
        UpdateProfileRequest body = new UpdateProfileRequest("Bob", null, null, null, 1, "1990-01-01");
        UserProfileResponse profile = new UserProfileResponse(
                "u_1", "alice", "138", null, "", "Bob", 1, "1990-01-01", 10, null, null);
        when(userProfileService.updateProfile("u_1", body)).thenReturn(profile);

        assertEquals(profile, profileController.updateProfile(request, body).data());
    }

    @Test
    void changePassword() {
        ChangePasswordRequest body = new ChangePasswordRequest("old", "newpass123");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("  ");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");
        when(request.getRemoteAddr()).thenReturn("10.0.0.6");

        ApiResponse<Void> response = profileController.changePassword(request, body);

        verify(userProfileService).changePassword("u_1", body);
        verify(auditServiceClient).log(eq("u_1"), eq("user.password.change"), eq("u_1"), any(), eq("10.0.0.6"), eq("JUnit"), eq(1));
        assertEquals("密码修改成功", response.message());
    }

    @Test
    void changePasswordUsesForwardedFor() {
        ChangePasswordRequest body = new ChangePasswordRequest("old", "newpass123");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.2");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        profileController.changePassword(request, body);

        verify(auditServiceClient).log(eq("u_1"), eq("user.password.change"), eq("u_1"), any(), eq("203.0.113.9"), eq("JUnit"), eq(1));
    }

    @Test
    void bindEmail() {
        BindEmailRequest body = new BindEmailRequest("a@b.com", "123456");
        UserProfileResponse profile = new UserProfileResponse(
                "u_1", "alice", "138", "a@b.com", "", "A", 1, null, 10, null, null);
        when(userProfileService.bindEmail("u_1", body)).thenReturn(profile);

        ApiResponse<UserProfileResponse> response = profileController.bindEmail(request, body);
        assertEquals("邮箱绑定成功", response.message());
        assertEquals(profile, response.data());
    }

    @Test
    void bindPhone() {
        BindPhoneRequest body = new BindPhoneRequest("13900139000", "123456");
        UserProfileResponse profile = new UserProfileResponse(
                "u_1", "alice", "13900139000", null, "", "A", 1, null, 10, null, null);
        when(userProfileService.bindPhone("u_1", body)).thenReturn(profile);

        ApiResponse<UserProfileResponse> response = profileController.bindPhone(request, body);
        assertEquals("手机号绑定成功", response.message());
    }

    @Test
    void updatePrivacy() {
        PrivacySettingsRequest body = new PrivacySettingsRequest(Map.of("show", true));
        UserProfileResponse profile = new UserProfileResponse(
                "u_1", "alice", "138", null, "", "A", 1, null, 10, Map.of("show", true), null);
        when(userProfileService.updatePrivacy("u_1", body)).thenReturn(profile);

        assertEquals(profile, profileController.updatePrivacy(request, body).data());
    }

    @Test
    void exportData() {
        ExportDataRequest body = new ExportDataRequest(List.of("health"), "excel", null, null);
        ExportTaskResponse task = new ExportTaskResponse(
                "t_1", "completed", "ok", "http://dl", "f.xlsx", "2024-01-02T00:00:00");
        when(dataExportService.submitExport("u_1", body)).thenReturn(task);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.8");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        ApiResponse<ExportTaskResponse> response = profileController.exportData(request, body);
        assertEquals("导出成功", response.message());
        assertEquals(task, response.data());
        verify(auditServiceClient).log(eq("u_1"), eq("data.export"), eq("t_1"), any(), eq("198.51.100.8"), eq("JUnit"), eq(1));
    }

    @Test
    void exportTask() {
        ExportTaskResponse task = new ExportTaskResponse(
                "t_1", "completed", "ok", "http://dl", "f.xlsx", "2024-01-02T00:00:00");
        when(dataExportService.getTask("u_1", "t_1")).thenReturn(task);

        assertEquals(task, profileController.exportTask(request, "t_1").data());
    }

    @Test
    void currentUserId_missing() {
        when(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).thenReturn(null);
        assertThrows(BusinessException.class, () -> profileController.overview(request));
    }

    @Test
    void currentUserId_blank() {
        when(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).thenReturn("   ");
        assertThrows(BusinessException.class, () -> profileController.overview(request));
    }
}
