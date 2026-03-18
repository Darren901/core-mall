package com.coremall.agent.service;

import com.coremall.agent.agent.InventoryAgent;
import com.coremall.agent.agent.OrderAgent;
import com.coremall.agent.jpa.repository.AgentRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRunExecutor - Tracing Span")
class AgentRunExecutorTracingTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClientFactory chatClientFactory;

    @Mock
    private InventoryAgent inventoryAgent;

    @Mock
    private OrderAgent orderAgent;

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
        when(chatClientFactory.getClient(any())).thenReturn(chatClient);

        sinkRegistry = new AgentSinkRegistry();
        executor = new AgentRunExecutor(chatClientFactory, inventoryAgent, orderAgent, agentRunRepository, sinkRegistry, objectMapper, tracer);
    }

    @Test
    @DisplayName("execute：建立名為 agent.run 的 span，帶 runId、userId tag")
    void shouldCreateAgentRunSpanWithNameAndTags() {
        String runId = UUID.randomUUID().toString();
        String userId = "user-001";
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(Consumer.class)).tools(any(), any()).call().content())
                .thenReturn("ok");

        executor.execute(runId, userId, "測試訊息", null);

        verify(mockSpan).name("agent.run");
        verify(mockSpan).tag("runId", runId);
        verify(mockSpan).tag("userId", userId);
        verify(mockSpan).start();
    }

    @Test
    @DisplayName("execute 成功：span 在執行完畢後關閉")
    void shouldEndSpanAfterSuccessfulExecution() {
        String runId = UUID.randomUUID().toString();
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(Consumer.class)).tools(any(), any()).call().content())
                .thenReturn("ok");

        executor.execute(runId, "U001", "test", null);

        verify(mockSpan).end();
    }

    @Test
    @DisplayName("execute 失敗：span 在例外後仍然關閉（不洩漏）")
    void shouldEndSpanEvenWhenExecutionFails() {
        String runId = UUID.randomUUID().toString();
        sinkRegistry.register(runId);

        when(chatClient.prompt().user(anyString()).advisors(any(Consumer.class)).tools(any(), any()).call().content())
                .thenThrow(new RuntimeException("LLM error"));

        executor.execute(runId, "U001", "test", null);

        verify(mockSpan).end();
    }
}
