package com.diabetes.health;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthServiceApplicationTest {

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(HealthServiceApplication.class, new String[] {}))
                    .thenReturn(null);
            HealthServiceApplication.main(new String[] {});
            mocked.verify(() -> SpringApplication.run(HealthServiceApplication.class, new String[] {}));
        }
    }

    @Test
    void constructor_instantiatesSuccessfully() {
        assertNotNull(new HealthServiceApplication());
    }
}