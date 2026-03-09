package com.coremall.agent.service;

import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.jpa.entity.AgentRun;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRunService - startRun 協調與 SSE 事件委派")
class AgentRunServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private AgentRunExecutor agentRunExecutor;

    private AgentSinkRegistry sinkRegistry;
    private AgentRunService agentRunService;

    @BeforeEach
    void setUp() {
        sinkRegistry = new AgentSinkRegistry();
        agentRunService = new AgentRunService(agentRunRepository, sinkRegistry, agentRunExecutor);
    }

    @Test
    @DisplayName("startRun：建立 AgentRun 並持久化，回傳 runId")
    void shouldCreateAgentRunAndReturnRunId() {
        AgentRun savedRun = AgentRun.create("幫我訂5個蘋果");
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        String runId = agentRunService.startRun("U001", "幫我訂5個蘋果");

        assertThat(runId).isNotNull();
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository).save(captor.capture());
        assertThat(captor.getValue().getUserMessage()).isEqualTo("幫我訂5個蘋果");
    }

    @Test
    @DisplayName("startRun：AgentRun 的 userMessage 正確設定，狀態為 RUNNING")
    void shouldSetCorrectUserMessageAndStatus() {
        String msg = "幫我查訂單";
        AgentRun savedRun = AgentRun.create(msg);
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        agentRunService.startRun("U001", msg);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository).save(captor.capture());
        assertThat(captor.getValue().getUserMessage()).isEqualTo(msg);
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("startRun：呼叫 AgentRunExecutor.execute 觸發非同步執行，並傳遞 userId")
    void shouldDelegateExecutionToAgentRunExecutor() {
        AgentRun savedRun = AgentRun.create("幫我建立訂單");
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        String runId = agentRunService.startRun("U001", "幫我建立訂單");

        verify(agentRunExecutor).execute(runId, "U001", "幫我建立訂單");
    }

    @Test
    @DisplayName("streamEvents：runId 不存在 → 返回空 Flux")
    void shouldReturnEmptyFluxForUnknownRunId() {
        Flux<ServerSentEvent<String>> flux = agentRunService.streamEvents("non-existent-id");
        StepVerifier.create(flux).verifyComplete();
    }

    @Test
    @DisplayName("streamEvents：startRun 後 runId 存在 → 回傳非空 Flux")
    void shouldReturnFluxAfterStartRun() {
        AgentRun savedRun = AgentRun.create("test");
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        String runId = agentRunService.startRun("U001", "test");
        Flux<ServerSentEvent<String>> flux = agentRunService.streamEvents(runId);

        assertThat(flux).isNotNull();
        // close sink so StepVerifier can complete
        sinkRegistry.get(runId).tryEmitComplete();
        StepVerifier.create(flux).verifyComplete();
    }

    @Test
    @DisplayName("onStepEvent：runId 不存在 → 靜默忽略，不拋例外")
    void shouldSilentlyIgnoreEventForUnknownRunId() {
        AgentStepEvent event = new AgentStepEvent("unknown-run", "createOrder", "SUCCEEDED", "ok");
        agentRunService.onStepEvent(event);
        // 不應拋出例外
    }

    @Test
    @DisplayName("onStepEvent：payload 為 null → 正常處理不拋例外")
    void shouldHandleNullPayloadInStepEvent() {
        AgentStepEvent event = new AgentStepEvent("unknown-run", "createOrder", "STARTED", null);
        agentRunService.onStepEvent(event);
        // 不應拋出例外
    }

    @Test
    @DisplayName("onStepEvent：sink 存在且 payload 為 null → 成功推送 SSE 事件（payload 欄位為空字串）")
    void shouldEmitSseEventWithNullPayload() {
        AgentRun savedRun = AgentRun.create("test");
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        String runId = agentRunService.startRun("U001", "test");
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        AgentStepEvent event = new AgentStepEvent(runId, "createOrder", "STARTED", null);
        agentRunService.onStepEvent(event);

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("step-started");
                    assertThat(sse.data()).contains("createOrder").doesNotContain("null");
                })
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("onStepEvent：sink 存在且 payload 非 null → 成功推送 SSE 事件")
    void shouldEmitSseEventWhenSinkExists() {
        AgentRun savedRun = AgentRun.create("test");
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        String runId = agentRunService.startRun("U001", "test");
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        AgentStepEvent event = new AgentStepEvent(runId, "createOrder", "SUCCEEDED", "訂單已建立，ID: order-1");
        agentRunService.onStepEvent(event);

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("step-succeeded");
                    assertThat(sse.data()).contains("createOrder").contains("SUCCEEDED");
                })
                .thenCancel()
                .verify();
    }
}
