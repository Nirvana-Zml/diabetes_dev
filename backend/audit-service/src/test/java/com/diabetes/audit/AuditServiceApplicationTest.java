package com.diabetes.audit;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class AuditServiceApplicationTest {

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(AuditServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            AuditServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(AuditServiceApplication.class, new String[]{}));
        }
    }
}
