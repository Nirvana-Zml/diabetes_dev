package com.diabetes.article;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class ArticleServiceApplicationTest {

    @Test
    void constructor_isCovered() {
        new ArticleServiceApplication();
    }

    @Test
    void main_invokesSpringApplication() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(ArticleServiceApplication.class, new String[]{}))
                    .thenReturn(null);
            ArticleServiceApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(ArticleServiceApplication.class, new String[]{}));
        }
    }
}
