package com.coremall.inventory.consumer;

import com.coremall.inventory.config.RabbitMQConfig;
import com.coremall.inventory.jpa.entity.Inventory;
import com.coremall.inventory.jpa.repository.InventoryRepository;
import com.coremall.inventory.jpa.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OrderEventConsumerTest - 整合測試（Testcontainers RabbitMQ）")
class OrderEventConsumerTest {

    @Container
    @SuppressWarnings("resource")
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void rabbitmqProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
        inventoryRepository.deleteAll();
        inventoryRepository.save(new Inventory("iPhone 15", 10));
        inventoryRepository.save(new Inventory("MacBook Pro", 5));
        inventoryRepository.save(new Inventory("AirPods", 20));
    }

    @Test
    @DisplayName("正常消費 ORDER_CREATED → 庫存扣減")
    void shouldDeductStockOnOrderCreated() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        sendOrderEvent(messageId, orderId, "iPhone 15", 3);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Inventory inventory = inventoryRepository.findById("iPhone 15").orElseThrow();
            assertThat(inventory.getQuantity()).isEqualTo(7);
        });
        assertThat(processedEventRepository.existsById(messageId)).isTrue();
    }

    @Test
    @DisplayName("重複 messageId → 冪等跳過，庫存只扣一次")
    void shouldSkipDuplicateMessage() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        sendOrderEvent(messageId, orderId, "AirPods", 2);
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(messageId)).isTrue());

        // 相同 messageId 再送一次
        sendOrderEvent(messageId, orderId, "AirPods", 2);

        // 等待短暫時間，確認第二次被忽略
        Thread.sleep(1000);

        Inventory inventory = inventoryRepository.findById("AirPods").orElseThrow();
        assertThat(inventory.getQuantity()).isEqualTo(18); // 只扣一次
    }

    @Test
    @DisplayName("庫存不足 → inventory.events.queue 收到補償訊息")
    void shouldPublishCompensationEventWhenInsufficientStock() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        sendOrderEvent(messageId, orderId, "MacBook Pro", 10); // 只有 5，不足

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Message received = rabbitTemplate.receive(RabbitMQConfig.INVENTORY_QUEUE, 1000);
            assertThat(received).isNotNull();
            String body = new String(received.getBody());
            assertThat(body).contains("MacBook Pro");
            assertThat(body).contains(orderId);
        });

        // 庫存不扣減
        Inventory inventory = inventoryRepository.findById("MacBook Pro").orElseThrow();
        assertThat(inventory.getQuantity()).isEqualTo(5);
    }

    /**
     * 模擬 OutboxPublisher 發送的訊息格式：
     * Jackson2JsonMessageConverter 會將 String payload double-encode，
     * 所以 payload 是 JSON string value（外層有引號）。
     */
    private void sendOrderEvent(String messageId, String orderId, String productName, int quantity)
            throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(
                new OrderPayload(orderId, "U001", productName, quantity, "CREATED", "2026-03-10T00:00:00"));
        // convertAndSend(String) 會 double-encode，與 OutboxPublisher 行為一致
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                payloadJson,
                msg -> {
                    msg.getMessageProperties().setMessageId(messageId);
                    return msg;
                });
    }

    record OrderPayload(String id, String userId, String productName, int quantity, String status, String createdAt) {}
}
