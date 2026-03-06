package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.CreateOrderRequest;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.dto.UpdateOrderRequest;
import com.coremall.order.exception.LockConflictException;
import com.coremall.order.exception.OrderNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderCommandService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 建立訂單。流程：idem 檢查 → 搶鎖 → 寫 Redis → 釋放鎖
     */
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        // 1. 冪等鍵檢查（4.8）
        OrderResponse cached = getByIdemKey(idempotencyKey);
        if (cached != null) {
            return cached;
        }

        // 2. 分散式鎖（4.7）
        String lockKey = RedisConfig.LOCK_KEY_PREFIX + "create:" + request.userId();
        acquireLockOrThrow(lockKey);
        try {
            // 3. 建立訂單並寫入 Redis（4.4）
            OrderResponse order = new OrderResponse(
                    UUID.randomUUID().toString(),
                    request.userId(),
                    request.productName(),
                    request.quantity(),
                    "CREATED",
                    LocalDateTime.now().toString()
            );
            writeToRedis(order, idempotencyKey);
            return order;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 更新訂單。流程：idem 檢查 → 搶鎖 → 讀 Redis → 更新 → 寫回
     */
    public OrderResponse updateOrder(String orderId, UpdateOrderRequest request, String idempotencyKey) {
        OrderResponse cached = getByIdemKey(idempotencyKey);
        if (cached != null) {
            return cached;
        }

        String lockKey = RedisConfig.LOCK_KEY_PREFIX + orderId;
        acquireLockOrThrow(lockKey);
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
            return updated;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 取消訂單。流程：idem 檢查 → 搶鎖 → 讀 Redis → 標記 CANCELLED → 寫回
     */
    public void cancelOrder(String orderId, String idempotencyKey) {
        OrderResponse cached = getByIdemKey(idempotencyKey);
        if (cached != null) {
            return;
        }

        String lockKey = RedisConfig.LOCK_KEY_PREFIX + orderId;
        acquireLockOrThrow(lockKey);
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
        } finally {
            redisTemplate.delete(lockKey);
        }
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
