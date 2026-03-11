package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.CreateOrderRequest;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.dto.UpdateOrderRequest;
import com.coremall.order.exception.LockConflictException;
import com.coremall.order.exception.OrderNotFoundException;
import com.coremall.order.jpa.entity.OutboxEvent;
import com.coremall.order.jpa.entity.OrderStatus;
import com.coremall.order.jpa.repository.OrderRepository;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    public OrderCommandService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               OrderRepository orderRepository,
                               OutboxEventRepository outboxEventRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * 建立訂單。流程：idem 檢查 → 搶鎖 → 寫 Redis → 釋放鎖
     */
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        log.info("[Order] createOrder start: userId={} product={} qty={} idemKey={}",
                request.userId(), request.productName(), request.quantity(), idempotencyKey);

        // 1. 冪等鍵檢查
        OrderResponse cached = getByIdemKey(idempotencyKey);
        if (cached != null) {
            log.info("[Order] createOrder idem-hit: idemKey={} → orderId={}", idempotencyKey, cached.id());
            return cached;
        }

        // 2. 分散式鎖
        String lockKey = RedisConfig.LOCK_KEY_PREFIX + "create:" + request.userId();
        acquireLockOrThrow(lockKey);
        log.debug("[Order] createOrder lock acquired: key={}", lockKey);
        try {
            // 3. 建立訂單並寫入 Redis
            OrderResponse order = new OrderResponse(
                    UUID.randomUUID().toString(),
                    request.userId(),
                    request.productName(),
                    request.quantity(),
                    "CREATED",
                    LocalDateTime.now().toString()
            );
            writeToRedis(order, idempotencyKey);
            log.info("[Order] createOrder done: orderId={} status=CREATED written to Redis", order.id());
            return order;
        } finally {
            redisTemplate.delete(lockKey);
            log.debug("[Order] createOrder lock released: key={}", lockKey);
        }
    }

    /**
     * 更新訂單。流程：idem 檢查 → 搶鎖 → 讀 Redis → 更新 → 寫回
     */
    public OrderResponse updateOrder(String orderId, UpdateOrderRequest request, String idempotencyKey) {
        log.info("[Order] updateOrder start: orderId={} idemKey={}", orderId, idempotencyKey);

        OrderResponse cached = getByIdemKey(idempotencyKey);
        if (cached != null) {
            log.info("[Order] updateOrder idem-hit: idemKey={} → orderId={}", idempotencyKey, cached.id());
            return cached;
        }

        String lockKey = RedisConfig.LOCK_KEY_PREFIX + orderId;
        acquireLockOrThrow(lockKey);
        log.debug("[Order] updateOrder lock acquired: key={}", lockKey);
        try {
            OrderResponse existing = getFromRedisOrThrow(orderId);
            OrderResponse updated = new OrderResponse(
                    existing.id(),
                    existing.userId(),
                    request.productName() != null ? request.productName() : existing.productName(),
                    request.quantity() != null ? request.quantity() : existing.quantity(),
                    "UPDATED",
                    existing.createdAt()
            );
            writeToRedis(updated, idempotencyKey);
            log.info("[Order] updateOrder done: orderId={} status=UPDATED written to Redis", orderId);
            return updated;
        } finally {
            redisTemplate.delete(lockKey);
            log.debug("[Order] updateOrder lock released: key={}", lockKey);
        }
    }

    /**
     * 取消訂單。流程：idem 檢查 → 搶鎖 → 讀 Redis → 標記 CANCELLED → 寫回
     */
    public void cancelOrder(String orderId, String idempotencyKey) {
        log.info("[Order] cancelOrder start: orderId={} idemKey={}", orderId, idempotencyKey);

        OrderResponse cached = getByIdemKey(idempotencyKey);
        if (cached != null) {
            log.info("[Order] cancelOrder idem-hit: idemKey={}", idempotencyKey);
            return;
        }

        String lockKey = RedisConfig.LOCK_KEY_PREFIX + orderId;
        acquireLockOrThrow(lockKey);
        log.debug("[Order] cancelOrder lock acquired: key={}", lockKey);
        try {
            OrderResponse existing = getFromRedisOrThrow(orderId);
            OrderResponse cancelled = new OrderResponse(
                    existing.id(),
                    existing.userId(),
                    existing.productName(),
                    existing.quantity(),
                    "CANCELLED",
                    existing.createdAt()
            );
            writeToRedis(cancelled, idempotencyKey);
            log.info("[Order] cancelOrder done: orderId={} status=CANCELLED written to Redis", orderId);
        } finally {
            redisTemplate.delete(lockKey);
            log.debug("[Order] cancelOrder lock released: key={}", lockKey);
        }
    }

    /**
     * Saga 補償：將訂單改為 CANCELLED。
     * Redis 優先；TTL 過期時 fallback 直接更新 PostgreSQL。
     * 兩者都找不到則 log warn 跳過（不拋例外）。
     */
    @Transactional
    public void cancelOrderBySaga(String orderId) {
        log.info("[Order] cancelOrderBySaga start: orderId={}", orderId);

        String json = redisTemplate.opsForValue().get(RedisConfig.ORDER_KEY_PREFIX + orderId);
        if (json != null) {
            OrderResponse existing = deserialize(json);
            OrderResponse cancelled = new OrderResponse(
                    existing.id(), existing.userId(), existing.productName(),
                    existing.quantity(), "CANCELLED", existing.createdAt());
            redisTemplate.opsForValue().set(
                    RedisConfig.ORDER_KEY_PREFIX + orderId, serialize(cancelled), RedisConfig.ORDER_TTL);
            redisTemplate.opsForSet().add(RedisConfig.PENDING_RELAY_KEY, orderId);
            log.info("[Order] cancelOrderBySaga Redis hit: orderId={} → CANCELLED, added to pending-relay", orderId);
            return;
        }

        // Redis miss → DB fallback：直接更新 DB 並寫 OutboxEvent（relay 無法從過期 Redis 取資料）
        orderRepository.findById(UUID.fromString(orderId)).ifPresentOrElse(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            OrderResponse cancelled = new OrderResponse(
                    order.getId().toString(), order.getUserId(), order.getProductName(),
                    order.getQuantity(), "CANCELLED", order.getCreatedAt().toString());
            outboxEventRepository.save(OutboxEvent.of(order.getId(), "ORDER_CANCELLED", serialize(cancelled)));
            log.info("[Order] cancelOrderBySaga DB hit: orderId={} → CANCELLED + OutboxEvent saved", orderId);
        }, () -> log.warn("[Order] cancelOrderBySaga: 訂單不存在 orderId={}", orderId));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private OrderResponse getByIdemKey(String idempotencyKey) {
        String cachedOrderId = redisTemplate.opsForValue().get(RedisConfig.IDEM_KEY_PREFIX + idempotencyKey);
        if (cachedOrderId == null) return null;
        return getFromRedisOrThrow(cachedOrderId);
    }

    private OrderResponse getFromRedisOrThrow(String orderId) {
        String json = redisTemplate.opsForValue().get(RedisConfig.ORDER_KEY_PREFIX + orderId);
        if (json == null) throw new OrderNotFoundException(orderId);
        return deserialize(json);
    }

    private void writeToRedis(OrderResponse order, String idempotencyKey) {
        String json = serialize(order);
        redisTemplate.opsForValue().set(RedisConfig.ORDER_KEY_PREFIX + order.id(), json, RedisConfig.ORDER_TTL);
        redisTemplate.opsForValue().set(RedisConfig.IDEM_KEY_PREFIX + idempotencyKey, order.id(), RedisConfig.IDEM_TTL);
        redisTemplate.opsForSet().add(RedisConfig.PENDING_RELAY_KEY, order.id());
        log.debug("[Order] Redis write: order:{}={} idem:{}→{} added to pending-relay",
                order.id(), order.status(), idempotencyKey, order.id());
    }

    private void acquireLockOrThrow(String lockKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisConfig.LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new LockConflictException(lockKey);
        }
    }

    private String serialize(OrderResponse order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order", e);
        }
    }

    private OrderResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, OrderResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize order", e);
        }
    }
}
