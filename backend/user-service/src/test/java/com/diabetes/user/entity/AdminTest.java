package com.diabetes.user.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminTest {

    @Test
    void gettersAndSetters() {
        Admin admin = new Admin();
        admin.setAdminId("a_1");
        admin.setUsername("admin");
        admin.setPasswordHash("hash");

        assertEquals("a_1", admin.getAdminId());
        assertEquals("admin", admin.getUsername());
        assertEquals("hash", admin.getPasswordHash());
    }
}
