package com.diabetes.user.controller;

import com.diabetes.user.dto.InterventionEvaluateRequest;
import com.diabetes.user.service.HealthInterventionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.*;

class InternalInterventionControllerTest {

    private HealthInterventionOrchestrator orchestrator;
    private InternalInterventionController controller;

    @BeforeEach
    void setUp() {
        orchestrator = mock(HealthInterventionOrchestrator.class);
        controller = new InternalInterventionController(orchestrator);
    }

    @Test
    void evaluate_delegatesToOrchestrator() {
        InterventionEvaluateRequest request = new InterventionEvaluateRequest(
                "u1", "health_record_saved", Map.of("recordId", "hr_1"));

        controller.evaluate(request);

        verify(orchestrator).evaluateAsync("u1", "health_record_saved", request.context());
    }
}
