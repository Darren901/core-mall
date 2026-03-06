package com.coremall.agent.service;

import com.coremall.agent.jpa.entity.AgentStep;
import com.coremall.agent.jpa.repository.AgentStepRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncStepService - AgentStep 狀態更新")
class AsyncStepServiceTest {

    @Mock
    private AgentStepRepository stepRepository;

    @InjectMocks
    private AsyncStepService asyncStepService;

    @Test
    @DisplayName("saveCompleted SUCCEEDED：找到 STARTED step → 呼叫 succeed()")
    void shouldUpdateStepToSucceeded() {
        UUID runId = UUID.randomUUID();
        AgentStep step = AgentStep.start(runId, "createOrder");
        when(stepRepository.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(step));

        asyncStepService.saveCompleted(runId.toString(), "createOrder", "SUCCEEDED", "訂單已建立");

        verify(stepRepository).save(step);
        assertThat(step.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(step.getPayload()).isEqualTo("訂單已建立");
    }

    @Test
    @DisplayName("saveCompleted FAILED：找到 STARTED step → 呼叫 fail()")
    void shouldUpdateStepToFailed() {
        UUID runId = UUID.randomUUID();
        AgentStep step = AgentStep.start(runId, "createOrder");
        when(stepRepository.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(step));

        asyncStepService.saveCompleted(runId.toString(), "createOrder", "FAILED", "Connection refused");

        verify(stepRepository).save(step);
        assertThat(step.getStatus()).isEqualTo("FAILED");
        assertThat(step.getError()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("saveCompleted：無符合的 STARTED step → 不呼叫 save()")
    void shouldSkipWhenNoMatchingStep() {
        UUID runId = UUID.randomUUID();
        when(stepRepository.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(Collections.emptyList());

        asyncStepService.saveCompleted(runId.toString(), "createOrder", "SUCCEEDED", "ok");

        verify(stepRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveCompleted：step toolName 不符 → 跳過，不呼叫 save()")
    void shouldSkipWhenToolNameMismatch() {
        UUID runId = UUID.randomUUID();
        AgentStep step = AgentStep.start(runId, "cancelOrder");
        when(stepRepository.findByRunIdOrderByCreatedAtAsc(runId)).thenReturn(List.of(step));

        asyncStepService.saveCompleted(runId.toString(), "createOrder", "SUCCEEDED", "ok");

        verify(stepRepository, never()).save(any());
    }
}
