package com.coremall.agent.listener;

import com.coremall.agent.dto.AgentStepEvent;
import com.coremall.agent.service.AgentSinkRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@DisplayName("AgentStepEventListener - AgentStepEvent → SSE 推送")
class AgentStepEventListenerTest {

    private AgentSinkRegistry sinkRegistry;
    private AgentStepEventListener listener;

    @BeforeEach
    void setUp() {
        sinkRegistry = new AgentSinkRegistry();
        listener = new AgentStepEventListener(sinkRegistry);
    }

    @Test
    @DisplayName("onStepEvent：runId 不存在 → 靜默忽略，不拋例外")
    void shouldSilentlyIgnoreEventForUnknownRunId() {
        AgentStepEvent event = new AgentStepEvent("unknown-run", "OrderAgent", "createOrder", "SUCCEEDED", "ok");
        assertThatCode(() -> listener.onStepEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onStepEvent：payload 為 null → 正常處理不拋例外")
    void shouldHandleNullPayloadInStepEvent() {
        AgentStepEvent event = new AgentStepEvent("unknown-run", "OrderAgent", "createOrder", "STARTED", null);
        assertThatCode(() -> listener.onStepEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onStepEvent：sink 存在且 payload 為 null → 推送 SSE，payload 欄位為空字串")
    void shouldEmitSseEventWithNullPayload() {
        String runId = "run-001";
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        listener.onStepEvent(new AgentStepEvent(runId, "OrderAgent", "createOrder", "STARTED", null));

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("step-started");
                    assertThat(sse.data()).contains("createOrder").doesNotContain("null");
                })
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("onStepEvent：sink 存在且 payload 非 null → 推送對應 SSE 事件")
    void shouldEmitSseEventWhenSinkExists() {
        String runId = "run-002";
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        listener.onStepEvent(new AgentStepEvent(runId, "OrderAgent", "createOrder", "SUCCEEDED", "訂單已建立，ID: order-1"));

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("step-succeeded");
                    assertThat(sse.data()).contains("createOrder").contains("SUCCEEDED");
                })
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("onStepEvent：SSE data 包含 agentName 欄位")
    void shouldIncludeAgentNameInSseData() {
        String runId = "run-004";
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        listener.onStepEvent(new AgentStepEvent(runId, "InventoryAgent", "checkInventory", "STARTED", null));

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> assertThat(sse.data()).contains("InventoryAgent"))
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("onStepEvent：event 名稱依 status 小寫化（FAILED → step-failed）")
    void shouldLowercaseStatusForEventName() {
        String runId = "run-003";
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        listener.onStepEvent(new AgentStepEvent(runId, "OrderAgent", "cancelOrder", "FAILED", "錯誤訊息"));

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> assertThat(sse.event()).isEqualTo("step-failed"))
                .thenCancel()
                .verify();
    }
}
