package com.diabetes.user;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class UserServiceApplicationTest {

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(UserServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            UserServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(UserServiceApplication.class, new String[]{}));
        }
    }
}
