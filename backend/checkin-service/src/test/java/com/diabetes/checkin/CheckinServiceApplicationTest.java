package com.diabetes.checkin;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class CheckinServiceApplicationTest {

    @Test
    void constructor_isCovered() {
        new CheckinServiceApplication();
    }

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(CheckinServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            CheckinServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(CheckinServiceApplication.class, new String[]{}));
        }
    }
}
