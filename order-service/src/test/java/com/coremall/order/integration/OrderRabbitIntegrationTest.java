package com.coremall.order.integration;

import com.coremall.order.config.RabbitMQConfig;
import com.coremall.order.jpa.entity.OutboxEvent;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import com.coremall.order.jpa.repository.ProcessedEventRepository;
import com.coremall.order.service.OrderEventConsumer;
import com.coremall.order.service.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.4 整合測試：outbox_events → RabbitMQ publish + 消費端冪等性。
 *
 * 使用 @MockBean OrderEventConsumer 停用 @RabbitListener，
 * 讓 Publisher 的測試可以直接從 queue 取訊息驗證；
 * 消費端冪等性則直接呼叫真實 consumer bean（不經 queue）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.autoconfigure.exclude="}
)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("OrderRabbitIntegrationTest - Outbox publish + 消費冪等性")
class OrderRabbitIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void setProps(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** 停用 @RabbitListener，讓 Publisher 測試可手動 receive */
    @MockBean
    private OrderEventConsumer orderEventConsumer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    @DisplayName("OutboxPublisher：發佈後 outbox_event 標記 published=true，訊息可從 queue 取出")
    void shouldPublishOutboxEventToQueue() {
        OutboxEvent event = OutboxEvent.of(UUID.randomUUID(), "ORDER_CREATED", "{\"id\":\"test-1\"}");
        outboxEventRepository.save(event);

        outboxPublisher.publish();

        // outbox_event 應標記為 published
        OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.isPublished()).isTrue();

        // Queue 應可取到訊息，messageId = event UUID
        Message received = rabbitTemplate.receive(RabbitMQConfig.QUEUE, 3000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getMessageId())
                .isEqualTo(event.getId().toString());
        assertThat(new String(received.getBody())).contains("test-1");
    }

    @Test
    @DisplayName("OrderEventConsumer 冪等性：相同 messageId 重複消費，processedEvents 只有 1 筆")
    void shouldNotDuplicateProcessedEventOnRetry() {
        // 建立真實 consumer bean 直接呼叫（不透過 queue，驗證冪等邏輯）
        OrderEventConsumer realConsumer = new OrderEventConsumer(processedEventRepository);

        String messageId = UUID.randomUUID().toString();
        Message message = buildMessage(messageId, "{\"id\":\"order-dup\"}");

        realConsumer.consume(message);
        realConsumer.consume(message); // 重複

        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    private Message buildMessage(String messageId, String payload) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId);
        return MessageBuilder.withBody(payload.getBytes()).andProperties(props).build();
    }
}
