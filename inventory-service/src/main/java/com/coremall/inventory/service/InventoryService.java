package com.coremall.inventory.service;

import com.coremall.inventory.config.RabbitMQConfig;
import com.coremall.inventory.dto.InsufficientStockEvent;
import com.coremall.inventory.jpa.entity.Inventory;
import com.coremall.inventory.jpa.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final RabbitTemplate rabbitTemplate;

    public InventoryService(InventoryRepository inventoryRepository, RabbitTemplate rabbitTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void deductStock(String orderId, String productName, int requestedQty) {
        Optional<Inventory> found = inventoryRepository.findById(productName);

        if (found.isEmpty()) {
            log.warn("[Inventory] 商品不存在，跳過扣減：productName={} orderId={}", productName, orderId);
            return;
        }

        Inventory inventory = found.get();

        if (inventory.getQuantity() >= requestedQty) {
            inventory.deduct(requestedQty);
            inventoryRepository.save(inventory);
            log.info("[Inventory] 庫存扣減成功：productName={} qty={} remaining={}",
                    productName, requestedQty, inventory.getQuantity());
        } else {
            log.warn("[Inventory] 庫存不足，發補償事件：productName={} requested={} available={}",
                    productName, requestedQty, inventory.getQuantity());
            InsufficientStockEvent event = new InsufficientStockEvent(
                    orderId, productName, requestedQty, inventory.getQuantity());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.INVENTORY_EXCHANGE,
                    RabbitMQConfig.INSUFFICIENT_ROUTING_KEY,
                    event);
        }
    }
}
