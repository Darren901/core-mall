package com.coremall.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ── 消費端：監聽 order.events exchange ────────────────────────────────────

    /** 既有 exchange（由 order-service 建立，宣告式冪等）*/
    public static final String ORDER_EXCHANGE = "order.events";
    /** 本服務專屬 queue，不與 order-service 共用 */
    public static final String ORDER_QUEUE = "inventory.order.queue";
    public static final String ORDER_CREATED_ROUTING_KEY  = "order.ORDER_CREATED";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.ORDER_CANCELLED";
    /** 向下相容舊引用 */
    public static final String ORDER_ROUTING_KEY = ORDER_CREATED_ROUTING_KEY;

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue inventoryOrderQueue() {
        return new Queue(ORDER_QUEUE, true);
    }

    @Bean
    public Binding inventoryOrderCreatedBinding(Queue inventoryOrderQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(inventoryOrderQueue).to(orderExchange).with(ORDER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Binding inventoryOrderCancelledBinding(Queue inventoryOrderQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(inventoryOrderQueue).to(orderExchange).with(ORDER_CANCELLED_ROUTING_KEY);
    }

    // ── 發佈端：補償事件 inventory.events exchange ────────────────────────────

    public static final String INVENTORY_EXCHANGE = "inventory.events";
    public static final String INVENTORY_QUEUE = "inventory.events.queue";
    public static final String INSUFFICIENT_ROUTING_KEY = "inventory.INSUFFICIENT";

    @Bean
    public TopicExchange inventoryExchange() {
        return new TopicExchange(INVENTORY_EXCHANGE);
    }

    @Bean
    public Queue inventoryEventsQueue() {
        return new Queue(INVENTORY_QUEUE, true);
    }

    @Bean
    public Binding inventoryEventsBinding(Queue inventoryEventsQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(inventoryEventsQueue).to(inventoryExchange).with("inventory.#");
    }

    // ── 共用 ──────────────────────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
