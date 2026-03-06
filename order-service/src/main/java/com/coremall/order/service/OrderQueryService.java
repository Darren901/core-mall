package com.coremall.order.service;

import com.coremall.order.config.RedisConfig;
import com.coremall.order.dto.OrderResponse;
import com.coremall.order.exception.OrderNotFoundException;
import com.coremall.order.jpa.entity.Order;
import com.coremall.order.jpa.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderQueryService {

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderQueryService(StringRedisTemplate redisTemplate,
                             OrderRepository orderRepository,
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis 優先；cache miss 時 fallback 到 PostgreSQL。
     */
    public OrderResponse getOrder(String orderId) {
        // 1. Redis 快取查詢
        String json = redisTemplate.opsForValue().get(RedisConfig.ORDER_KEY_PREFIX + orderId);
        if (json != null) {
            return deserialize(json);
        }

        // 2. DB fallback
        return orderRepository.findById(UUID.fromString(orderId))
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId().toString(),
                order.getUserId(),
                order.getProductName(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getCreatedAt().toString()
        );
    }

    private OrderResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, OrderResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize order", e);
        }
    }
}
