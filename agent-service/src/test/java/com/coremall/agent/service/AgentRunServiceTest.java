package com.coremall.agent.service;

import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.jpa.entity.AgentRun;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.coremall.agent.tool.OrderAgentTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRunService - 建立 AgentRun 與 SSE Sink 管理")
class AgentRunServiceTest {

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private OrderAgentTools orderAgentTools;

    @InjectMocks
    private AgentRunService agentRunService;

    @Test
    @DisplayName("startRun：建立 AgentRun 並持久化，回傳 runId")
    void shouldCreateAgentRunAndReturnRunId() {
        AgentRun savedRun = AgentRun.create("幫我訂5個蘋果");
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        String runId = agentRunService.startRun("幫我訂5個蘋果");

        assertThat(runId).isNotNull();
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository).save(captor.capture());
        assertThat(captor.getValue().getUserMessage()).isEqualTo("幫我訂5個蘋果");
    }

    @Test
    @DisplayName("streamEvents：runId 不存在 → 返回空 Flux")
    void shouldReturnEmptyFluxForUnknownRunId() {
        Flux<ServerSentEvent<String>> flux = agentRunService.streamEvents("non-existent-id");
        StepVerifier.create(flux).verifyComplete();
    }

    @Test
    @DisplayName("streamEvents：runId 存在 → 回傳非空 Flux")
    @SuppressWarnings("unchecked")
    void shouldReturnFluxForExistingRunId() {
        String runId = "existing-run-id";
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().limit(100);

        ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>> sinks =
                (ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>>)
                        ReflectionTestUtils.getField(agentRunService, "sinks");
        sinks.put(runId, sink);

        Flux<ServerSentEvent<String>> flux = agentRunService.streamEvents(runId);

        assertThat(flux).isNotNull();
        sink.tryEmitComplete();
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
    @DisplayName("onStepEvent：sink 存在且 payload 非 null → 成功推送 SSE 事件")
    @SuppressWarnings("unchecked")
    void shouldEmitSseEventWhenSinkExists() {
        String runId = "active-run-id";
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().replay().limit(10);

        ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>> sinks =
                (ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>>)
                        ReflectionTestUtils.getField(agentRunService, "sinks");
        sinks.put(runId, sink);

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

    @Test
    @DisplayName("startRun：AgentRun 的 userMessage 正確設定")
    void shouldSetCorrectUserMessageOnAgentRun() {
        String msg = "幫我查訂單";
        AgentRun savedRun = AgentRun.create(msg);
        when(agentRunRepository.save(any(AgentRun.class))).thenReturn(savedRun);

        agentRunService.startRun(msg);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunRepository).save(captor.capture());
        assertThat(captor.getValue().getUserMessage()).isEqualTo(msg);
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
    }
}
