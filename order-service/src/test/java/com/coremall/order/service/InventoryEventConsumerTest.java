package com.coremall.order.service;

import com.coremall.order.jpa.entity.ProcessedEvent;
import com.coremall.order.jpa.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryEventConsumer - 庫存補償事件消費")
class InventoryEventConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OrderCommandService orderCommandService;

    private InventoryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InventoryEventConsumer(processedEventRepository, orderCommandService, new ObjectMapper());
    }

    @Test
    @DisplayName("正常消費 INSUFFICIENT 事件 → 呼叫 cancelOrderBySaga")
    void shouldCancelOrderOnInsufficientEvent() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String payload = new ObjectMapper().writeValueAsString(
                new TestPayload(orderId, "iPhone 15", 10, 3));

        Message message = buildMessage(messageId, payload);
        given(processedEventRepository.existsById(messageId)).willReturn(false);

        consumer.consume(message);

        then(orderCommandService).should().cancelOrderBySaga(orderId);
        then(processedEventRepository).should().save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("重複 messageId → 冪等跳過，不呼叫 cancelOrderBySaga")
    void shouldSkipDuplicateMessage() throws Exception {
        String messageId = UUID.randomUUID().toString();
        Message message = buildMessage(messageId,
                new ObjectMapper().writeValueAsString(new TestPayload(UUID.randomUUID().toString(), "MacBook", 5, 0)));

        given(processedEventRepository.existsById(messageId)).willReturn(true);

        consumer.consume(message);

        then(orderCommandService).should(never()).cancelOrderBySaga(anyString());
        then(processedEventRepository).should(never()).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("messageId 為 null → 跳過處理")
    void shouldSkipWhenMessageIdIsNull() {
        Message message = buildMessage(null, "{\"orderId\":\"x\",\"productName\":\"p\",\"requestedQty\":1,\"availableQty\":0}");

        consumer.consume(message);

        then(orderCommandService).should(never()).cancelOrderBySaga(anyString());
    }

    @Test
    @DisplayName("payload 解析失敗 → 拋出 RuntimeException")
    void shouldThrowOnParseError() {
        String messageId = UUID.randomUUID().toString();
        Message message = buildMessage(messageId, "not-json");
        given(processedEventRepository.existsById(messageId)).willReturn(false);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("消費失敗");
    }

    private Message buildMessage(String messageId, String body) {
        MessageProperties props = new MessageProperties();
        if (messageId != null) props.setMessageId(messageId);
        return MessageBuilder.withBody(body.getBytes()).andProperties(props).build();
    }

    record TestPayload(String orderId, String productName, int requestedQty, int availableQty) {}
}
