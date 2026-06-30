package com.diabetes.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void gettersAndSetters() {
        User user = new User();
        LocalDate birth = LocalDate.of(1990, 1, 1);
        LocalDateTime now = LocalDateTime.now();

        user.setUserId("u_1");
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setPhone("13800138000");
        user.setEmail("alice@test.com");
        user.setAvatarId("u_1.jpg");
        user.setNickname("Alice");
        user.setGender(1);
        user.setBirthDate(birth);
        user.setPoints(100);
        user.setPrivacySettings("{}");
        user.setDeleted(false);
        user.setDeletedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        assertEquals("u_1", user.getUserId());
        assertEquals("alice", user.getUsername());
        assertEquals("hash", user.getPasswordHash());
        assertEquals("13800138000", user.getPhone());
        assertEquals("alice@test.com", user.getEmail());
        assertEquals("u_1.jpg", user.getAvatarId());
        assertEquals("Alice", user.getNickname());
        assertEquals(1, user.getGender());
        assertEquals(birth, user.getBirthDate());
        assertEquals(100, user.getPoints());
        assertEquals("{}", user.getPrivacySettings());
        assertFalse(user.getDeleted());
        assertEquals(now, user.getDeletedAt());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }
}
