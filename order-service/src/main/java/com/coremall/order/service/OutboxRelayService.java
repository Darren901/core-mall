package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.jpa.entity.Order;
import com.coremall.order.jpa.entity.OrderStatus;
import com.coremall.order.jpa.entity.OutboxEvent;
import com.coremall.order.jpa.repository.OrderRepository;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxRelayService(StringRedisTemplate redisTemplate,
                              OrderRepository orderRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 每 5 秒掃描 pending-relay set，將未持久化的訂單寫入 DB + outbox_events。
     */
    @Scheduled(fixedDelay = 5000)
    public void relay() {
        Set<String> pendingIds = redisTemplate.opsForSet().members(RedisConfig.PENDING_RELAY_KEY);
        if (pendingIds == null || pendingIds.isEmpty()) return;

        for (String orderId : pendingIds) {
            try {
                relayOne(orderId);
            } catch (Exception e) {
                log.error("[Relay] Failed to relay orderId={}", orderId, e);
            }
        }
    }

    /**
     * 將單一訂單從 Redis relay 到 PostgreSQL（含 outbox_event），同一個 transaction。
     * 冪等：orderRepository.save() 在 id 已存在時執行 UPDATE（無副作用）。
     */
    @Transactional
    public void relayOne(String orderId) {
        String json = redisTemplate.opsForValue().get(RedisConfig.ORDER_KEY_PREFIX + orderId);
        if (json == null) {
            // 已被 TTL 清除，直接移出 pending
            redisTemplate.opsForSet().remove(RedisConfig.PENDING_RELAY_KEY, orderId);
            return;
        }

        try {
            OrderResponse response = objectMapper.readValue(json, OrderResponse.class);

            Order order = new Order();
            order.setId(UUID.fromString(response.id()));
            order.setUserId(response.userId());
            order.setProductName(response.productName());
            order.setQuantity(response.quantity());
            order.setStatus(OrderStatus.valueOf(response.status()));
            order.setCreatedAt(LocalDateTime.parse(response.createdAt()));
            order.setUpdatedAt(LocalDateTime.now());

            orderRepository.save(order);

            OutboxEvent event = OutboxEvent.of(
                    UUID.fromString(response.id()),
                    "ORDER_" + response.status(),
                    json
            );
            outboxEventRepository.save(event);

            redisTemplate.opsForSet().remove(RedisConfig.PENDING_RELAY_KEY, orderId);

        } catch (JsonProcessingException e) {
            log.error("[Relay] JSON parse error for orderId={}", orderId, e);
        }
    }
}
