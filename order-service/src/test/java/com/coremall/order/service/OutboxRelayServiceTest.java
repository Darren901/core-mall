package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.jpa.entity.Order;
import com.coremall.order.jpa.entity.OutboxEvent;
import com.coremall.order.jpa.repository.OrderRepository;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.lenient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayService - Redis → DB relay")
class OutboxRelayServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxRelayService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    @DisplayName("relayOne：讀 Redis → 儲存 Order + OutboxEvent → 從 pending-relay 移除")
    void shouldSaveOrderAndOutboxEventAndRemoveFromPending() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse resp = new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-05T12:00:00");
        String json = new ObjectMapper().writeValueAsString(resp);

        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(json);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        service.relayOne(orderId);

        verify(orderRepository).save(any(Order.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(setOps).remove(RedisConfig.PENDING_RELAY_KEY, orderId);
    }

    @Test
    @DisplayName("relay：遍歷所有 pending orderId 呼叫 relayOne")
    void shouldRelayAllPendingOrders() throws Exception {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        OrderResponse r1 = new OrderResponse(id1, "u1", "Apple", 1, "CREATED", "2026-03-05T12:00:00");
        OrderResponse r2 = new OrderResponse(id2, "u1", "Banana", 2, "CREATED", "2026-03-05T12:00:00");
        ObjectMapper mapper = new ObjectMapper();

        when(setOps.members(RedisConfig.PENDING_RELAY_KEY)).thenReturn(Set.of(id1, id2));
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + id1)).thenReturn(mapper.writeValueAsString(r1));
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + id2)).thenReturn(mapper.writeValueAsString(r2));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.relay();

        verify(orderRepository, org.mockito.Mockito.times(2)).save(any(Order.class));
        verify(outboxEventRepository, org.mockito.Mockito.times(2)).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("relayOne：Redis 中找不到 order JSON 時，僅從 pending-relay 移除")
    void shouldRemoveFromPendingWhenOrderJsonNotFound() {
        String orderId = UUID.randomUUID().toString();
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(null);

        service.relayOne(orderId);

        verify(orderRepository, never()).save(any());
        verify(setOps).remove(RedisConfig.PENDING_RELAY_KEY, orderId);
    }

    @Test
    @DisplayName("relay：pending-relay 為空時不執行任何 relay")
    void shouldDoNothingWhenPendingRelayIsEmpty() {
        when(setOps.members(RedisConfig.PENDING_RELAY_KEY)).thenReturn(java.util.Set.of());

        service.relay();

        verify(orderRepository, never()).save(any());
    }
}
