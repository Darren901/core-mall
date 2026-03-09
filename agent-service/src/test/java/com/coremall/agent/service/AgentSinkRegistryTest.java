package com.coremall.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentSinkRegistry - SSE Sink 生命週期管理")
class AgentSinkRegistryTest {

    private AgentSinkRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentSinkRegistry();
    }

    @Test
    @DisplayName("stream：未 register 的 runId → 立即返回空 Flux")
    void shouldReturnEmptyFluxForUnknownRunId() {
        Flux<ServerSentEvent<String>> flux = registry.stream("unknown-id");
        StepVerifier.create(flux).verifyComplete();
    }

    @Test
    @DisplayName("register：新增後 get 能取回非 null sink")
    void shouldReturnSinkAfterRegister() {
        registry.register("run-1");

        Sinks.Many<ServerSentEvent<String>> sink = registry.get("run-1");

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("remove：移除後 get 返回 null，remove 返回原 sink")
    void shouldRemoveSinkAndReturnIt() {
        registry.register("run-1");

        Sinks.Many<ServerSentEvent<String>> removed = registry.remove("run-1");

        assertThat(removed).isNotNull();
        assertThat(registry.get("run-1")).isNull();
    }

    @Test
    @DisplayName("remove：不存在的 runId → 返回 null 不拋例外")
    void shouldReturnNullWhenRemovingUnknownRunId() {
        Sinks.Many<ServerSentEvent<String>> result = registry.remove("ghost-id");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("stream：register 後能接收到推入的事件")
    void shouldStreamEventsFromRegisteredSink() {
        registry.register("run-2");
        Sinks.Many<ServerSentEvent<String>> sink = registry.get("run-2");

        ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                .event("step-succeeded")
                .data("{\"toolName\":\"createOrder\"}")
                .build();
        sink.tryEmitNext(event);
        sink.tryEmitComplete();

        StepVerifier.create(registry.stream("run-2"))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("step-succeeded");
                    assertThat(sse.data()).contains("createOrder");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("register：多個不同 runId 彼此獨立，互不干擾")
    void shouldIsolateMultipleRunIds() {
        registry.register("run-a");
        registry.register("run-b");

        assertThat(registry.get("run-a")).isNotNull();
        assertThat(registry.get("run-b")).isNotNull();
        assertThat(registry.get("run-a")).isNotSameAs(registry.get("run-b"));
    }
}
