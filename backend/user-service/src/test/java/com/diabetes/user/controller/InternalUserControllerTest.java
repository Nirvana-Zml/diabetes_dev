package com.diabetes.user.controller;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.user.dto.UserOverviewResponse;
import com.diabetes.user.dto.UserProfileResponse;
import com.diabetes.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private InternalUserController internalUserController;

    @Test
    void profile() {
        UserProfileResponse profile = new UserProfileResponse(
                "u_1", "alice", "138", "a@b.com", "", "A", 1, "1990-01-01", 10, null, "2024-01-01");
        when(userProfileService.getProfile("u_1")).thenReturn(profile);

        ApiResponse<UserProfileResponse> response = internalUserController.profile("u_1");

        assertEquals(200, response.code());
        assertEquals(profile, response.data());
    }

    @Test
    void overview() {
        UserOverviewResponse overview = new UserOverviewResponse(
                "u_1", "alice", "A", "http://avatar", 100, "138");
        when(userProfileService.getOverview("u_1")).thenReturn(overview);

        ApiResponse<UserOverviewResponse> response = internalUserController.overview("u_1");

        assertEquals(overview, response.data());
    }
}
