package com.diabetes.consultation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ConsultationServiceApplication.class, 
               webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("ConsultationServiceApplication 启动类测试")
class ConsultationServiceApplicationTest {

    @Test
    @DisplayName("main方法 - 应用能正常启动")
    void main() {
        assertDoesNotThrow(() -> {
            ConsultationServiceApplication.main(new String[]{});
        });
    }

    @Test
    @DisplayName("上下文加载 - 应用上下文能正常创建")
    void contextLoads(ApplicationContext context) {
        assertNotNull(context);
        assertTrue(context.containsBean("consultationService"));
        assertTrue(context.containsBean("consultationController"));
        assertTrue(context.containsBean("consultationV2Controller"));
        assertTrue(context.containsBean("internalConsultationController"));
        assertTrue(context.containsBean("webMvcConfig"));
    }
}