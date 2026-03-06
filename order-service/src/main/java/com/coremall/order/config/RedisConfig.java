package com.coremall.order.config;

import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Redis key 命名規則與 TTL 常數。
 * StringRedisTemplate 由 Spring Boot 的 RedisAutoConfiguration 自動建立。
 */
@Configuration
public class RedisConfig {

    public static final String ORDER_KEY_PREFIX      = "order:";
    public static final String IDEM_KEY_PREFIX       = "idem:";
    public static final String LOCK_KEY_PREFIX       = "lock:order:";
    public static final String PENDING_RELAY_KEY     = "orders:pending-relay";

    public static final Duration ORDER_TTL = Duration.ofHours(24);
    public static final Duration IDEM_TTL  = Duration.ofHours(24);
    public static final Duration LOCK_TTL  = Duration.ofSeconds(30);
}
