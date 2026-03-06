package com.coremall.order.service;

import com.coremall.order.jpa.entity.ProcessedEvent;
import com.coremall.order.jpa.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer - 消費端冪等性")
class OrderEventConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private OrderEventConsumer consumer;

    @Test
    @DisplayName("首次收到訊息：處理並記錄 processedEvent")
    void shouldProcessAndRecordFirstTimeMessage() {
        String messageId = UUID.randomUUID().toString();
        Message message = buildMessage(messageId, "{\"id\":\"order-1\"}");
        when(processedEventRepository.existsById(messageId)).thenReturn(false);

        consumer.consume(message);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("重複訊息：messageId 已處理過，直接跳過不重複儲存")
    void shouldSkipDuplicateMessage() {
        String messageId = UUID.randomUUID().toString();
        Message message = buildMessage(messageId, "{\"id\":\"order-1\"}");
        when(processedEventRepository.existsById(messageId)).thenReturn(true);

        consumer.consume(message);

        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("訊息無 messageId：仍能正常處理（以 payload hash 或 skip）")
    void shouldHandleMessageWithoutMessageId() {
        MessageProperties props = new MessageProperties();
        // messageId 為 null
        Message message = MessageBuilder.withBody("{\"id\":\"order-2\"}".getBytes())
                .andProperties(props)
                .build();
        when(processedEventRepository.existsById(any())).thenReturn(false);

        consumer.consume(message);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    private Message buildMessage(String messageId, String payload) {
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId);
        return MessageBuilder.withBody(payload.getBytes()).andProperties(props).build();
    }
}
