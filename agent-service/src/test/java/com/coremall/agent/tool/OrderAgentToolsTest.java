package com.coremall.agent.tool;

import com.coremall.agent.client.OrderServiceClient;
import com.coremall.agent.dto.OrderResult;
import com.coremall.agent.service.AsyncStepService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAgentTools - @Tool 方法冪等性與呼叫邏輯")
class OrderAgentToolsTest {

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

    @InjectMocks
    private OrderAgentTools orderAgentTools;

    @BeforeEach
    void setUp() {
        AgentRunContext.set("test-run-id");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @AfterEach
    void tearDown() {
        AgentRunContext.clear();
    }

    // ─── createOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder：無冪等快取 → 呼叫 order-service → 回傳訂單 ID 訊息")
    void shouldCreateOrderWhenNoCacheHit() {
        when(valueOps.get(anyString())).thenReturn(null);
        OrderResult result = new OrderResult("order-1", "user-1", "Apple", 5, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.createOrder(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(result);

        String response = orderAgentTools.createOrder("user-1", "Apple", 5);

        assertThat(response).contains("order-1");
        verify(orderServiceClient).createOrder(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("createOrder：Redis 已有快取 → 直接回傳，不呼叫 order-service")
    void shouldReturnCachedResultWhenIdempotencyKeyHit() {
        when(valueOps.get(anyString())).thenReturn("訂單已建立，ID: order-cached");

        String response = orderAgentTools.createOrder("user-1", "Apple", 5);

        assertThat(response).isEqualTo("訂單已建立，ID: order-cached");
        verify(orderServiceClient, never()).createOrder(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("createOrder：order-service 異常 → 回傳錯誤訊息，不拋出例外")
    void shouldReturnErrorMessageWhenCreateOrderFails() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(orderServiceClient.createOrder(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        String response = orderAgentTools.createOrder("user-1", "Apple", 5);

        assertThat(response).contains("失敗").contains("Connection refused");
    }

    // ─── updateOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateOrder：無快取 → 呼叫 order-service → 回傳更新訊息")
    void shouldUpdateOrder() {
        when(valueOps.get(anyString())).thenReturn(null);
        OrderResult result = new OrderResult("order-2", "user-1", "Apple", 10, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.updateOrder(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(result);

        String response = orderAgentTools.updateOrder("order-2", "Apple", 10);

        assertThat(response).contains("order-2").contains("10");
        verify(orderServiceClient).updateOrder(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("updateOrder：Redis 快取命中 → 不呼叫 order-service")
    void shouldReturnCachedUpdateResult() {
        when(valueOps.get(anyString())).thenReturn("訂單已更新，ID: order-2，數量: 10");

        String response = orderAgentTools.updateOrder("order-2", "Apple", 10);

        assertThat(response).contains("order-2");
        verify(orderServiceClient, never()).updateOrder(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("updateOrder：order-service 異常 → 回傳錯誤訊息")
    void shouldReturnErrorMessageWhenUpdateOrderFails() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(orderServiceClient.updateOrder(anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("Timeout"));

        String response = orderAgentTools.updateOrder("order-2", "Apple", 10);

        assertThat(response).contains("失敗").contains("Timeout");
    }

    // ─── cancelOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder：呼叫 order-service DELETE，回傳取消確認訊息")
    void shouldCancelOrder() {
        when(valueOps.get(anyString())).thenReturn(null);

        String response = orderAgentTools.cancelOrder("order-3");

        assertThat(response).contains("order-3");
        verify(orderServiceClient).cancelOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("cancelOrder：Redis 快取命中 → 不呼叫 order-service")
    void shouldReturnCachedCancelResult() {
        when(valueOps.get(anyString())).thenReturn("訂單 order-3 已取消");

        String response = orderAgentTools.cancelOrder("order-3");

        assertThat(response).contains("order-3");
        verify(orderServiceClient, never()).cancelOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("cancelOrder：order-service 異常 → 回傳錯誤訊息")
    void shouldReturnErrorMessageWhenCancelFails() {
        when(valueOps.get(anyString())).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("Not found"))
                .when(orderServiceClient).cancelOrder(anyString(), anyString());

        String response = orderAgentTools.cancelOrder("order-3");

        assertThat(response).contains("失敗").contains("Not found");
    }

    // ─── getOrderStatus ────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderStatus：呼叫 order-service GET，回傳狀態訊息")
    void shouldGetOrderStatus() {
        when(valueOps.get(anyString())).thenReturn(null);
        OrderResult result = new OrderResult("order-2", "user-1", "Banana", 3, "CREATED", "2025-01-01T00:00:00Z");
        when(orderServiceClient.getOrder("order-2")).thenReturn(result);

        String response = orderAgentTools.getOrderStatus("order-2");

        assertThat(response).contains("CREATED");
        verify(orderServiceClient).getOrder("order-2");
    }

    @Test
    @DisplayName("getOrderStatus：Redis 快取命中 → 不呼叫 order-service")
    void shouldReturnCachedOrderStatus() {
        when(valueOps.get(anyString())).thenReturn("訂單 order-2 狀態：CREATED，商品：Banana，數量：3");

        String response = orderAgentTools.getOrderStatus("order-2");

        assertThat(response).contains("CREATED");
        verify(orderServiceClient, never()).getOrder(anyString());
    }

    @Test
    @DisplayName("getOrderStatus：order-service 異常 → 回傳錯誤訊息")
    void shouldReturnErrorMessageWhenGetOrderFails() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(orderServiceClient.getOrder(anyString())).thenThrow(new RuntimeException("Order not found"));

        String response = orderAgentTools.getOrderStatus("order-99");

        assertThat(response).contains("失敗").contains("Order not found");
    }
}
