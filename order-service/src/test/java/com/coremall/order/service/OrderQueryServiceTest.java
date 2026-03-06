package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.exception.OrderNotFoundException;
import com.coremall.order.jpa.entity.Order;
import com.coremall.order.jpa.entity.OrderStatus;
import com.coremall.order.jpa.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService - Redis 優先查詢 + DB fallback")
class OrderQueryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private OrderRepository orderRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Redis 有快取時直接回傳，不查 DB")
    void shouldReturnFromRedisWithoutHittingDb() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse cached = new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-05T12:00:00");
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId))
                .thenReturn(new ObjectMapper().writeValueAsString(cached));

        OrderResponse result = service.getOrder(orderId);

        assertThat(result.id()).isEqualTo(orderId);
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Redis 沒有快取時 fallback 到 DB")
    void shouldFallbackToDbWhenRedisMiss() {
        UUID orderId = UUID.randomUUID();
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(null);

        Order order = buildOrder(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = service.getOrder(orderId.toString());

        assertThat(result.id()).isEqualTo(orderId.toString());
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("Redis 和 DB 都沒有時拋出 OrderNotFoundException")
    void shouldThrowOrderNotFoundWhenNeitherRedisNorDb() {
        UUID orderId = UUID.randomUUID();
        when(valueOps.get(anyKey())).thenReturn(null);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(orderId.toString()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private String anyKey() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private Order buildOrder(UUID id) {
        Order o = new Order();
        o.setId(id);
        o.setUserId("u1");
        o.setProductName("Apple");
        o.setQuantity(3);
        o.setStatus(OrderStatus.CREATED);
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        return o;
    }
}
