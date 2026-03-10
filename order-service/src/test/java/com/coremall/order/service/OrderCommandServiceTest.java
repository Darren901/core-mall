package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.CreateOrderRequest;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.dto.UpdateOrderRequest;
import com.coremall.order.exception.LockConflictException;
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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandService - 冪等與鎖邏輯")
class OrderCommandServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private OrderRepository orderRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderCommandService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    @DisplayName("冪等鍵已存在時，直接回傳快取訂單，不重複執行鎖與寫入")
    void shouldReturnCachedOrderWhenIdempotencyKeyExists() throws Exception {
        String idemKey = "idem-001";
        String orderId = UUID.randomUUID().toString();
        OrderResponse cached = new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-05T12:00:00");
        String orderJson = new ObjectMapper().writeValueAsString(cached);

        when(valueOps.get(RedisConfig.IDEM_KEY_PREFIX + idemKey)).thenReturn(orderId);
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(orderJson);

        OrderResponse result = service.createOrder(new CreateOrderRequest("u1", "Apple", 3), idemKey);

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.productName()).isEqualTo("Apple");
        // 鎖不應被請求
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("搶鎖失敗時拋出 LockConflictException")
    void shouldThrowLockConflictWhenCannotAcquireLock() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() ->
                service.createOrder(new CreateOrderRequest("u1", "Apple", 3), "idem-002"))
                .isInstanceOf(LockConflictException.class);
    }

    @Test
    @DisplayName("建立訂單成功，寫入 Redis（order + idem + pending-relay）")
    void shouldCreateOrderAndWriteToRedis() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        OrderResponse result = service.createOrder(new CreateOrderRequest("u1", "Apple", 3), "idem-003");

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.productName()).isEqualTo("Apple");
        assertThat(result.quantity()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("CREATED");
        assertThat(result.id()).isNotNull();

        verify(valueOps).set(startsWith(RedisConfig.ORDER_KEY_PREFIX), anyString(), eq(RedisConfig.ORDER_TTL));
        verify(valueOps).set(startsWith(RedisConfig.IDEM_KEY_PREFIX), anyString(), eq(RedisConfig.IDEM_TTL));
        verify(setOps).add(eq(RedisConfig.PENDING_RELAY_KEY), anyString());
    }

    @Test
    @DisplayName("更新訂單：冪等鍵命中直接回傳，不執行鎖")
    void shouldReturnCachedUpdateWhenIdempotencyKeyExists() throws Exception {
        String idemKey = "update-idem-001";
        String orderId = UUID.randomUUID().toString();
        OrderResponse cached = new OrderResponse(orderId, "u1", "Banana", 5, "UPDATED", "2026-03-05T12:00:00");
        String orderJson = new ObjectMapper().writeValueAsString(cached);

        when(valueOps.get(RedisConfig.IDEM_KEY_PREFIX + idemKey)).thenReturn(orderId);
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(orderJson);

        OrderResponse result = service.updateOrder(orderId, new UpdateOrderRequest("Banana", 5), idemKey);

        assertThat(result.productName()).isEqualTo("Banana");
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("取消訂單不存在時拋出 OrderNotFoundException")
    void shouldThrowOrderNotFoundWhenCancelNonExistentOrder() {
        String orderId = UUID.randomUUID().toString();
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.cancelOrder(orderId, "cancel-idem-001"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("updateOrder 成功：更新商品名稱與數量並寫回 Redis")
    void shouldUpdateOrderAndWriteToRedis() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse existing = new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-06T12:00:00");
        String existingJson = new ObjectMapper().writeValueAsString(existing);

        when(valueOps.get(startsWith(RedisConfig.IDEM_KEY_PREFIX))).thenReturn(null);
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(existingJson);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        OrderResponse result = service.updateOrder(orderId, new UpdateOrderRequest("Banana", 5), "update-idem-new");

        assertThat(result.productName()).isEqualTo("Banana");
        assertThat(result.quantity()).isEqualTo(5);
        assertThat(result.status()).isEqualTo("UPDATED");
        verify(valueOps).set(eq(RedisConfig.ORDER_KEY_PREFIX + orderId), anyString(), eq(RedisConfig.ORDER_TTL));
    }

    // ── cancelOrderBySaga ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrderBySaga：Redis 命中 → 改 CANCELLED 寫回 Redis")
    void shouldCancelOrderInRedisWhenFound() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse existing = new OrderResponse(orderId, "u1", "iPhone 15", 2, "CREATED", "2026-03-10T00:00:00");
        String existingJson = new ObjectMapper().writeValueAsString(existing);

        given(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).willReturn(existingJson);

        service.cancelOrderBySaga(orderId);

        then(valueOps).should().set(eq(RedisConfig.ORDER_KEY_PREFIX + orderId), anyString(), eq(RedisConfig.ORDER_TTL));
        then(orderRepository).should(never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("cancelOrderBySaga：Redis miss + DB 命中 → 直接更新 DB")
    void shouldCancelOrderInDbWhenRedisMiss() {
        String orderId = UUID.randomUUID().toString();
        Order order = buildOrder(orderId, OrderStatus.CREATED);

        given(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).willReturn(null);
        given(orderRepository.findById(UUID.fromString(orderId))).willReturn(Optional.of(order));

        service.cancelOrderBySaga(orderId);

        then(orderRepository).should().save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelOrderBySaga：Redis miss + DB 也 miss → log warn 不拋例外")
    void shouldSkipWhenOrderNotFoundAnywhere() {
        String orderId = UUID.randomUUID().toString();

        given(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).willReturn(null);
        given(orderRepository.findById(UUID.fromString(orderId))).willReturn(Optional.empty());

        assertThatCode(() -> service.cancelOrderBySaga(orderId)).doesNotThrowAnyException();
        then(orderRepository).should(never()).save(any());
    }

    private Order buildOrder(String orderId, OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.fromString(orderId));
        order.setUserId("u1");
        order.setProductName("iPhone 15");
        order.setQuantity(2);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    @Test
    @DisplayName("cancelOrder 成功：訂單標記 CANCELLED 並寫回 Redis")
    void shouldCancelOrderAndWriteToRedis() throws Exception {
        String orderId = UUID.randomUUID().toString();
        OrderResponse existing = new OrderResponse(orderId, "u1", "Apple", 3, "CREATED", "2026-03-06T12:00:00");
        String existingJson = new ObjectMapper().writeValueAsString(existing);

        when(valueOps.get(startsWith(RedisConfig.IDEM_KEY_PREFIX))).thenReturn(null);
        when(valueOps.get(RedisConfig.ORDER_KEY_PREFIX + orderId)).thenReturn(existingJson);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.cancelOrder(orderId, "cancel-idem-new");

        verify(valueOps).set(eq(RedisConfig.ORDER_KEY_PREFIX + orderId), anyString(), eq(RedisConfig.ORDER_TTL));
        verify(setOps).add(eq(RedisConfig.PENDING_RELAY_KEY), eq(orderId));
    }
}
