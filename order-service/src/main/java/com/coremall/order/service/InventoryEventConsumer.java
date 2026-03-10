package com.coremall.order.service;

import com.coremall.order.config.RabbitMQConfig;
import com.coremall.order.dto.InsufficientStockPayload;
import com.coremall.order.jpa.entity.ProcessedEvent;
import com.coremall.order.jpa.repository.ProcessedEventRepository;
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
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderCommandService orderCommandService;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(ProcessedEventRepository processedEventRepository,
                                  OrderCommandService orderCommandService,
                                  ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.orderCommandService = orderCommandService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.INVENTORY_QUEUE)
    @Transactional
    public void consume(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            log.warn("[InventoryConsumer] messageId 為 null，跳過訊息");
            return;
        }

        if (processedEventRepository.existsById(messageId)) {
            log.info("[InventoryConsumer] 重複訊息，冪等跳過：messageId={}", messageId);
            return;
        }

        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            InsufficientStockPayload payload = objectMapper.readValue(body, InsufficientStockPayload.class);

            log.info("[InventoryConsumer] 收到庫存不足補償事件：orderId={} product={} requested={} available={}",
                    payload.orderId(), payload.productName(), payload.requestedQty(), payload.availableQty());

            orderCommandService.cancelOrderBySaga(payload.orderId());
            processedEventRepository.save(ProcessedEvent.of(messageId));

        } catch (JsonProcessingException e) {
            log.error("[InventoryConsumer] 解析補償事件失敗：messageId={}", messageId, e);
            throw new RuntimeException("消費失敗，觸發 NACK：messageId=" + messageId, e);
        }
    }
}
