package com.coremall.order.service;

import com.coremall.order.config.RabbitMQConfig;
import com.coremall.order.jpa.entity.OutboxEvent;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher - outbox_events → RabbitMQ")
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxPublisher publisher;

    @Test
    @DisplayName("publish：讀取未發送事件，發佈到 exchange 並標記 published=true")
    void shouldPublishUnpublishedEventsAndMarkAsPublished() {
        OutboxEvent event = OutboxEvent.of(UUID.randomUUID(), "ORDER_CREATED", "{\"id\":\"x\"}");
        when(outboxEventRepository.findByPublishedFalse()).thenReturn(List.of(event));

        publisher.publish();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq("order.ORDER_CREATED"),
                eq("{\"id\":\"x\"}"),
                any(MessagePostProcessor.class)
        );
        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("publish：無未發送事件時不呼叫 RabbitTemplate")
    void shouldDoNothingWhenNoUnpublishedEvents() {
        when(outboxEventRepository.findByPublishedFalse()).thenReturn(List.of());

        publisher.publish();

        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("publishOne：發佈時 messageId 設為 outbox event UUID")
    void shouldSetMessageIdToOutboxEventId() {
        OutboxEvent event = OutboxEvent.of(UUID.randomUUID(), "ORDER_CANCELLED", "{\"id\":\"y\"}");

        publisher.publishOne(event);

        ArgumentCaptor<MessagePostProcessor> captor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq("order.ORDER_CANCELLED"),
                eq("{\"id\":\"y\"}"),
                captor.capture()
        );

        // 驗證 messageId 有被設進 MessageProperties
        Message msg = org.springframework.amqp.core.MessageBuilder
                .withBody("{}".getBytes())
                .andProperties(new org.springframework.amqp.core.MessageProperties())
                .build();
        captor.getValue().postProcessMessage(msg);
        assertThat(msg.getMessageProperties().getMessageId())
                .isEqualTo(event.getId().toString());
    }
}
