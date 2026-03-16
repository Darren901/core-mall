package com.coremall.agent.service;

import com.coremall.agent.jpa.entity.AgentRun;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.coremall.agent.tool.AgentRunContext;
import com.coremall.agent.tool.OrderAgentTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Spy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRunExecutor - LLM 非同步執行與 SSE 事件推送")
class AgentRunExecutorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClientFactory chatClientFactory;

    @Mock
    private OrderAgentTools orderAgentTools;

    @Mock
    private AgentRunRepository agentRunRepository;

    @Mock
    private Tracer tracer;

    @Mock
    private Span mockSpan;

    @Mock
    private Tracer.SpanInScope mockScope;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private AgentSinkRegistry sinkRegistry;
    private AgentRunExecutor executor;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(mockSpan);
        when(mockSpan.name(anyString())).thenReturn(mockSpan);
        when(mockSpan.tag(anyString(), anyString())).thenReturn(mockSpan);
        when(mockSpan.start()).thenReturn(mockSpan);
        when(tracer.withSpan(mockSpan)).thenReturn(mockScope);
        org.mockito.Mockito.lenient().when(chatClientFactory.getClient(anyString())).thenReturn(chatClient);
        org.mockito.Mockito.lenient().when(chatClientFactory.getClient(null)).thenReturn(chatClient);

        sinkRegistry = new AgentSinkRegistry();
        executor = new AgentRunExecutor(chatClientFactory, orderAgentTools, agentRunRepository, sinkRegistry, objectMapper, tracer);
    }

    @Test
    @DisplayName("execute 成功：推送 run-completed 事件並移除 sink")
    void shouldEmitRunCompletedAndRemoveSinkOnSuccess() {
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.create("建立訂單");
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("訂單已建立，ID: order-abc");
        when(agentRunRepository.findById(UUID.fromString(runId))).thenReturn(Optional.of(run));

        executor.execute(runId, "U001", "建立訂單", null);

        assertThat(sinkRegistry.get(runId)).isNull();
        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("run-completed");
                    assertThat(sse.data()).contains("訂單已建立");
                })
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("execute 失敗：推送 run-failed 事件並移除 sink")
    void shouldEmitRunFailedAndRemoveSinkOnError() {
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.create("建立訂單");
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenThrow(new RuntimeException("LLM timeout"));
        when(agentRunRepository.findById(UUID.fromString(runId))).thenReturn(Optional.of(run));

        executor.execute(runId, "U001", "建立訂單", null);

        assertThat(sinkRegistry.get(runId)).isNull();
        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("run-failed");
                    assertThat(sse.data()).contains("error");
                })
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("execute：AgentRunContext 在 finally 清除，不洩漏 ThreadLocal")
    void shouldClearAgentRunContextAfterExecution() {
        String runId = UUID.randomUUID().toString();
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("ok");
        when(agentRunRepository.findById(any())).thenReturn(Optional.empty());

        executor.execute(runId, "U001", "test", null);

        assertThat(AgentRunContext.get()).isNull();
    }

    @Test
    @DisplayName("execute 成功：DB 中的 AgentRun 狀態更新為 COMPLETED")
    void shouldUpdateAgentRunStatusToCompleted() {
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.create("查詢訂單");
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("查詢完成");
        when(agentRunRepository.findById(UUID.fromString(runId))).thenReturn(Optional.of(run));

        executor.execute(runId, "U001", "查詢訂單", null);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("execute 失敗：DB 中的 AgentRun 狀態更新為 FAILED")
    void shouldUpdateAgentRunStatusToFailed() {
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.create("查詢訂單");
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenThrow(new RuntimeException("timeout"));
        when(agentRunRepository.findById(UUID.fromString(runId))).thenReturn(Optional.of(run));

        executor.execute(runId, "U001", "查詢訂單", null);

        assertThat(run.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("execute：model 為 'google' 時，factory.getClient(\"google\") 被呼叫")
    void shouldCallFactoryWithGoogleModel() {
        String runId = UUID.randomUUID().toString();
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("ok");
        when(agentRunRepository.findById(any())).thenReturn(Optional.empty());

        executor.execute(runId, "U001", "test", "google");

        org.mockito.Mockito.verify(chatClientFactory).getClient("google");
    }

    @Test
    @DisplayName("execute：model 為 'anthropic' 時，factory.getClient(\"anthropic\") 被呼叫")
    void shouldCallFactoryWithAnthropicModel() {
        String runId = UUID.randomUUID().toString();
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("ok");
        when(agentRunRepository.findById(any())).thenReturn(Optional.empty());

        executor.execute(runId, "U001", "test", "anthropic");

        org.mockito.Mockito.verify(chatClientFactory).getClient("anthropic");
    }

    @Test
    @DisplayName("execute：userId 作為 conversationId 傳入 advisor 參數，不拋例外")
    void shouldAcceptUserIdAsConversationId() {
        String runId = UUID.randomUUID().toString();
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("ok");
        when(agentRunRepository.findById(any())).thenReturn(Optional.empty());

        executor.execute(runId, "user-with-special-id-123", "test message", null);

        assertThat(sinkRegistry.get(runId)).isNull();
    }

    @Test
    @DisplayName("execute 成功：sink 未註冊時靜默完成，不拋例外")
    void shouldCompleteGracefullyWhenNoSinkRegistered() {
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.create("查詢訂單");

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenReturn("查詢完成");
        when(agentRunRepository.findById(UUID.fromString(runId))).thenReturn(Optional.of(run));

        executor.execute(runId, "U001", "查詢訂單", null);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(sinkRegistry.get(runId)).isNull();
    }

    @Test
    @DisplayName("execute 失敗：exception.getMessage() 為 null 時推送預設錯誤訊息")
    void shouldHandleNullExceptionMessage() {
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.create("查詢訂單");
        sinkRegistry.register(runId);
        Sinks.Many<ServerSentEvent<String>> sink = sinkRegistry.get(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class)).tools(any()).call().content())
                .thenThrow(new RuntimeException((String) null));
        when(agentRunRepository.findById(UUID.fromString(runId))).thenReturn(Optional.of(run));

        executor.execute(runId, "U001", "查詢訂單", null);

        StepVerifier.create(sink.asFlux().take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("run-failed");
                    assertThat(sse.data()).contains("Unknown error");
                })
                .thenCancel()
                .verify();
    }
}
