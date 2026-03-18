package com.coremall.agent.tool;

import com.coremall.agent.client.InventoryServiceClient;
import com.coremall.agent.dto.InventoryResult;
import com.coremall.agent.service.AsyncStepService;
import com.coremall.sharedkernel.exception.ServiceBusinessException;
import com.coremall.sharedkernel.exception.ServiceTransientException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAgentTools - @Tool checkInventory 冪等性與呼叫邏輯")
class InventoryAgentToolsTest {

    @Mock
    private InventoryServiceClient inventoryServiceClient;

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

    @InjectMocks
    private InventoryAgentTools inventoryAgentTools;

    @BeforeEach
    void setUp() {
        AgentRunContext.set("test-run-id");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
        lenient().when(mockSpan.name(anyString())).thenReturn(mockSpan);
        lenient().when(mockSpan.tag(anyString(), any())).thenReturn(mockSpan);
        lenient().when(mockSpan.start()).thenReturn(mockSpan);
        lenient().when(tracer.withSpan(mockSpan)).thenReturn(mockScope);
    }

    @AfterEach
    void tearDown() {
        AgentRunContext.clear();
    }

    @Test
    @DisplayName("checkInventory：無冪等快取 → 呼叫 inventory-service → 回傳庫存訊息")
    void shouldCheckInventoryWhenNoCacheHit() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(inventoryServiceClient.getStock("iPhone 15"))
                .thenReturn(new InventoryResult("iPhone 15", 10));

        String response = inventoryAgentTools.checkInventory("iPhone 15");

        assertThat(response).contains("iPhone 15").contains("10");
        verify(inventoryServiceClient).getStock("iPhone 15");
    }

    @Test
    @DisplayName("checkInventory：Redis 已有快取 → 直接回傳，不呼叫 inventory-service")
    void shouldReturnCachedResultWhenIdempotencyKeyHit() {
        when(valueOps.get(anyString())).thenReturn("iPhone 15 庫存：10 件，有貨");

        String response = inventoryAgentTools.checkInventory("iPhone 15");

        assertThat(response).isEqualTo("iPhone 15 庫存：10 件，有貨");
        verify(inventoryServiceClient, never()).getStock(anyString());
    }

    @Test
    @DisplayName("checkInventory：ServiceBusinessException → 回傳 BUSINESS_ERROR| 前綴")
    void shouldReturnBusinessErrorPrefixOnBusinessException() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(inventoryServiceClient.getStock(anyString()))
                .thenThrow(new ServiceBusinessException("商品不存在"));

        String response = inventoryAgentTools.checkInventory("不存在商品");

        assertThat(response).startsWith("BUSINESS_ERROR|");
        assertThat(response).contains("商品不存在");
    }

    @Test
    @DisplayName("checkInventory：ServiceTransientException → 回傳 TRANSIENT_ERROR| 前綴")
    void shouldReturnTransientErrorPrefixOnTransientException() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(inventoryServiceClient.getStock(anyString()))
                .thenThrow(new ServiceTransientException("服務暫時不可用"));

        String response = inventoryAgentTools.checkInventory("iPhone 15");

        assertThat(response).startsWith("TRANSIENT_ERROR|");
        assertThat(response).contains("服務暫時不可用");
    }

    @Test
    @DisplayName("checkInventory：成功後 Redis 寫入冪等 key")
    void shouldWriteIdempotencyKeyToRedisOnSuccess() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(inventoryServiceClient.getStock("iPhone 15"))
                .thenReturn(new InventoryResult("iPhone 15", 10));

        inventoryAgentTools.checkInventory("iPhone 15");

        verify(valueOps).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("checkInventory：成功後發佈 STARTED + SUCCEEDED 兩個 AgentStepEvent")
    void shouldPublishStartedAndSucceededEvents() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(inventoryServiceClient.getStock("iPhone 15"))
                .thenReturn(new InventoryResult("iPhone 15", 10));

        inventoryAgentTools.checkInventory("iPhone 15");

        verify(asyncStepService).saveStarted(anyString(), anyString());
        verify(asyncStepService).saveCompleted(anyString(), anyString(), anyString(), anyString());
    }
}
