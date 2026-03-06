package com.coremall.order.service;

import com.coremall.order.config.RabbitMQConfig;
import com.coremall.order.jpa.entity.ProcessedEvent;
import com.coremall.order.jpa.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;

    public OrderEventConsumer(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * 消費 order.events.queue 的訊息。
     * 冪等性：以 messageId 查詢 processed_events，已處理過則直接略過。
     * messageId 為 null 時以 payload UUID 為 key。
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    @Transactional
    public void consume(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }

        if (processedEventRepository.existsById(messageId)) {
            log.info("[Consumer] Duplicate message skipped: messageId={}", messageId);
            return;
        }

        String payload = new String(message.getBody());
        log.info("[Consumer] Processing order event: messageId={} payload={}", messageId, payload);

        processedEventRepository.save(ProcessedEvent.of(messageId));
    }
}
