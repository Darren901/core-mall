package com.coremall.order.service;

import com.coremall.order.config.RabbitMQConfig;
import com.coremall.order.jpa.entity.OutboxEvent;
import com.coremall.order.jpa.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 每 5 秒掃描 outbox_events（published=false），逐一發佈到 RabbitMQ。
     */
    @Scheduled(fixedDelay = 5000)
    public void publish() {
        List<OutboxEvent> unpublished = outboxEventRepository.findByPublishedFalse();
        for (OutboxEvent event : unpublished) {
            try {
                publishOne(event);
            } catch (Exception e) {
                log.error("[OutboxPublisher] Failed to publish eventId={}", event.getId(), e);
            }
        }
    }

    /**
     * 發佈單一 outbox event 到 RabbitMQ，messageId 設為 event UUID（供 consumer 冪等判斷）。
     * 發佈成功後標記 published=true 並存回 DB。
     */
    @Transactional
    public void publishOne(OutboxEvent event) {
        String routingKey = "order." + event.getEventType();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                routingKey,
                event.getPayload(),
                message -> {
                    message.getMessageProperties().setMessageId(event.getId().toString());
                    return message;
                }
        );
        event.setPublished(true);
        outboxEventRepository.save(event);
        log.info("[OutboxPublisher] Published eventId={} routingKey={}", event.getId(), routingKey);
    }
}
