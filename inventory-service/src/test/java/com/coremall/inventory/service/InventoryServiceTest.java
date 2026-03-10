package com.coremall.inventory.service;

import com.coremall.inventory.config.RabbitMQConfig;
import com.coremall.inventory.dto.InsufficientStockEvent;
import com.coremall.inventory.jpa.entity.Inventory;
import com.coremall.inventory.jpa.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService - 庫存扣減邏輯")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory iphoneInventory;

    @BeforeEach
    void setUp() {
        iphoneInventory = new Inventory("iPhone 15", 10);
    }

    @Test
    @DisplayName("庫存足夠時，扣減成功，不發補償事件")
    void shouldDeductStockWhenSufficient() {
        given(inventoryRepository.findById("iPhone 15")).willReturn(Optional.of(iphoneInventory));

        inventoryService.deductStock("order-001", "iPhone 15", 3);

        assertThat(iphoneInventory.getQuantity()).isEqualTo(7);
        then(inventoryRepository).should().save(iphoneInventory);
        then(rabbitTemplate).should(never()).convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("庫存不足時，不扣減，發布 INSUFFICIENT 補償事件")
    void shouldPublishCompensationEventWhenInsufficientStock() {
        given(inventoryRepository.findById("MacBook Pro")).willReturn(Optional.of(new Inventory("MacBook Pro", 3)));

        inventoryService.deductStock("order-002", "MacBook Pro", 10);

        then(inventoryRepository).should(never()).save(any());

        ArgumentCaptor<InsufficientStockEvent> eventCaptor = ArgumentCaptor.forClass(InsufficientStockEvent.class);
        then(rabbitTemplate).should().convertAndSend(
                eq(RabbitMQConfig.INVENTORY_EXCHANGE),
                eq(RabbitMQConfig.INSUFFICIENT_ROUTING_KEY),
                eventCaptor.capture(),
                any(MessagePostProcessor.class)
        );

        InsufficientStockEvent event = eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo("order-002");
        assertThat(event.productName()).isEqualTo("MacBook Pro");
        assertThat(event.requestedQty()).isEqualTo(10);
        assertThat(event.availableQty()).isEqualTo(3);
    }

    @Test
    @DisplayName("商品不存在時，log warn，不拋例外，不發補償事件")
    void shouldLogWarnAndSkipWhenProductNotFound() {
        given(inventoryRepository.findById("不存在的商品")).willReturn(Optional.empty());

        assertThatCode(() -> inventoryService.deductStock("order-003", "不存在的商品", 1))
                .doesNotThrowAnyException();

        then(inventoryRepository).should(never()).save(any());
        then(rabbitTemplate).should(never()).convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));
    }
}
