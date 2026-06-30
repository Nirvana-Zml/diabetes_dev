package com.diabetes.plan;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlanServiceApplicationTest {

    @Test
    void defaultConstructor() {
        assertNotNull(new PlanServiceApplication());
    }

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(PlanServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            PlanServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(PlanServiceApplication.class, new String[]{}));
        }
    }
}
