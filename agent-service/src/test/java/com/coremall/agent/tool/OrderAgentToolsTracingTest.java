package com.coremall.agent.tool;

import com.coremall.agent.client.OrderServiceClient;
import com.coremall.agent.dto.OrderResult;
import com.coremall.agent.service.AsyncStepService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAgentTools - Tracing Span")
class OrderAgentToolsTracingTest {

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private AsyncStepService asyncStepService;

    @Mock
    private ApplicationEventPublisher publisher;

    @Mock
    private Tracer tracer;

    @Mock
    private Span mockSpan;

    @Mock
    private Tracer.SpanInScope mockScope;

    private OrderAgentTools orderAgentTools;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(mockSpan);
        when(mockSpan.name(anyString())).thenReturn(mockSpan);
        when(mockSpan.tag(anyString(), any())).thenReturn(mockSpan);
        when(mockSpan.start()).thenReturn(mockSpan);
        when(tracer.withSpan(mockSpan)).thenReturn(mockScope);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        AgentRunContext.set("test-run-id");
        orderAgentTools = new OrderAgentTools(orderServiceClient, redisTemplate, asyncStepService, publisher, tracer);
    }

    @AfterEach
    void tearDown() {
        AgentRunContext.clear();
    }

    @Test
    @DisplayName("createOrder：建立名為 agent.tool.createOrder 的 span 並帶 runId tag")
    void shouldCreateSpanForCreateOrder() {
        OrderResult result = new OrderResult("order-1", "user-1", "Apple", 5, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.createOrder(anyString(), anyString(), anyInt(), anyString())).thenReturn(result);

        orderAgentTools.createOrder("user-1", "Apple", 5);

        verify(mockSpan).name("agent.tool.createOrder");
        verify(mockSpan).tag("runId", "test-run-id");
        verify(mockSpan).end();
    }

    @Test
    @DisplayName("updateOrder：建立名為 agent.tool.updateOrder 的 span 並帶 runId tag")
    void shouldCreateSpanForUpdateOrder() {
        OrderResult result = new OrderResult("order-2", "user-1", "Apple", 10, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.updateOrder(anyString(), anyString(), anyInt(), anyString())).thenReturn(result);

        orderAgentTools.updateOrder("order-2", "Apple", 10);

        verify(mockSpan).name("agent.tool.updateOrder");
        verify(mockSpan).tag("runId", "test-run-id");
        verify(mockSpan).end();
    }

    @Test
    @DisplayName("cancelOrder：建立名為 agent.tool.cancelOrder 的 span 並帶 runId tag")
    void shouldCreateSpanForCancelOrder() {
        orderAgentTools.cancelOrder("order-3");

        verify(mockSpan).name("agent.tool.cancelOrder");
        verify(mockSpan).tag("runId", "test-run-id");
        verify(mockSpan).end();
    }

    @Test
    @DisplayName("getOrderStatus：建立名為 agent.tool.getOrderStatus 的 span 並帶 runId tag")
    void shouldCreateSpanForGetOrderStatus() {
        OrderResult result = new OrderResult("order-4", "user-1", "Banana", 3, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.getOrder(anyString())).thenReturn(result);

        orderAgentTools.getOrderStatus("order-4");

        verify(mockSpan).name("agent.tool.getOrderStatus");
        verify(mockSpan).tag("runId", "test-run-id");
        verify(mockSpan).end();
    }

    @Test
    @DisplayName("createOrder 失敗：span 在例外後仍然關閉（不洩漏）")
    void shouldEndSpanEvenWhenCreateOrderFails() {
        when(orderServiceClient.createOrder(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        orderAgentTools.createOrder("user-1", "Apple", 5);

        verify(mockSpan).end();
    }

    @Test
    @DisplayName("cancelOrder 失敗：span 在例外後仍然關閉（不洩漏）")
    void shouldEndSpanEvenWhenCancelOrderFails() {
        org.mockito.Mockito.doThrow(new RuntimeException("Not found"))
                .when(orderServiceClient).cancelOrder(anyString(), anyString());

        orderAgentTools.cancelOrder("order-5");

        verify(mockSpan).end();
    }

    @Test
    @DisplayName("createOrder：AgentRunContext 為 null 時 tag 使用空字串")
    void shouldUseEmptyTagWhenRunIdIsNullForCreate() {
        AgentRunContext.clear();
        OrderResult result = new OrderResult("order-1", "user-1", "Apple", 5, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.createOrder(anyString(), anyString(), anyInt(), anyString())).thenReturn(result);

        orderAgentTools.createOrder("user-1", "Apple", 5);

        verify(mockSpan).tag("runId", "");
    }

    @Test
    @DisplayName("updateOrder：AgentRunContext 為 null 時 tag 使用空字串")
    void shouldUseEmptyTagWhenRunIdIsNullForUpdate() {
        AgentRunContext.clear();
        OrderResult result = new OrderResult("order-2", "user-1", "Apple", 10, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.updateOrder(anyString(), anyString(), anyInt(), anyString())).thenReturn(result);

        orderAgentTools.updateOrder("order-2", "Apple", 10);

        verify(mockSpan).tag("runId", "");
    }

    @Test
    @DisplayName("cancelOrder：AgentRunContext 為 null 時 tag 使用空字串")
    void shouldUseEmptyTagWhenRunIdIsNullForCancel() {
        AgentRunContext.clear();

        orderAgentTools.cancelOrder("order-3");

        verify(mockSpan).tag("runId", "");
    }

    @Test
    @DisplayName("getOrderStatus：AgentRunContext 為 null 時 tag 使用空字串")
    void shouldUseEmptyTagWhenRunIdIsNullForGetStatus() {
        AgentRunContext.clear();
        OrderResult result = new OrderResult("order-4", "user-1", "Banana", 3, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.getOrder(anyString())).thenReturn(result);

        orderAgentTools.getOrderStatus("order-4");

        verify(mockSpan).tag("runId", "");
    }
}
