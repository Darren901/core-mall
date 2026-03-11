package com.coremall.inventory.consumer;

import com.coremall.inventory.config.RabbitMQConfig;
import com.coremall.inventory.dto.OrderEventPayload;
import com.coremall.inventory.jpa.entity.ProcessedEvent;
import com.coremall.inventory.jpa.repository.ProcessedEventRepository;
import com.coremall.inventory.service.InventoryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(ProcessedEventRepository processedEventRepository,
                              InventoryService inventoryService,
                              ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    @Transactional
    public void consume(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            log.warn("[Consumer] messageId 為 null，跳過訊息");
            return;
        }

        if (processedEventRepository.existsById(messageId)) {
            log.info("[Consumer] 重複訊息，冪等跳過：messageId={}", messageId);
            return;
        }

        try {
            OrderEventPayload payload = parsePayload(message);
            String status = payload.status();

            if ("CREATED".equals(status)) {
                log.info("[Consumer] 處理 ORDER_CREATED：messageId={} orderId={} product={} qty={}",
                        messageId, payload.id(), payload.productName(), payload.quantity());
                inventoryService.deductStock(payload.id(), payload.productName(), payload.quantity());
            } else if ("CANCELLED".equals(status)) {
                log.info("[Consumer] 處理 ORDER_CANCELLED：messageId={} orderId={} product={} qty={}",
                        messageId, payload.id(), payload.productName(), payload.quantity());
                inventoryService.restockInventory(payload.id(), payload.productName(), payload.quantity());
            } else {
                log.warn("[Consumer] 未知 status，跳過：messageId={} status={}", messageId, status);
            }

            processedEventRepository.save(ProcessedEvent.of(messageId));

        } catch (Exception e) {
            log.error("[Consumer] 解析或處理訊息失敗：messageId={}", messageId, e);
            throw new RuntimeException("消費失敗，觸發 NACK：messageId=" + messageId, e);
        }
    }

    /**
     * OutboxPublisher 使用 Jackson2JsonMessageConverter 發送 String payload，
     * 導致訊息 body 為 double-encoded JSON string（外層有引號）。
     * 先 readValue 為 String，再 readValue 為 OrderEventPayload。
     */
    private OrderEventPayload parsePayload(Message message) throws JsonProcessingException {
        String bodyStr = new String(message.getBody(), StandardCharsets.UTF_8);
        // 若為 double-encoded（外層是 JSON string），先解一層
        if (bodyStr.startsWith("\"")) {
            bodyStr = objectMapper.readValue(bodyStr, String.class);
        }
        return objectMapper.readValue(bodyStr, OrderEventPayload.class);
    }
}
