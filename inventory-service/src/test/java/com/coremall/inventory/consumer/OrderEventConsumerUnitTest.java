package com.coremall.inventory.consumer;

import com.coremall.inventory.jpa.entity.ProcessedEvent;
import com.coremall.inventory.jpa.repository.ProcessedEventRepository;
import com.coremall.inventory.service.InventoryService;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer 單元測試 - 覆蓋邊界分支")
class OrderEventConsumerUnitTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private InventoryService inventoryService;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(processedEventRepository, inventoryService, new ObjectMapper());
    }

    @Test
    @DisplayName("messageId 為 null 時，跳過處理，不呼叫 inventoryService")
    void shouldSkipWhenMessageIdIsNull() {
        MessageProperties props = new MessageProperties();
        // messageId 不設定，預設為 null
        Message message = MessageBuilder
                .withBody("{\"id\":\"o1\",\"userId\":\"U1\",\"productName\":\"iPhone 15\",\"quantity\":1,\"status\":\"CREATED\",\"createdAt\":\"2026\"}".getBytes())
                .andProperties(props)
                .build();

        consumer.consume(message);

        then(inventoryService).should(never()).deductStock(anyString(), anyString(), any(int.class));
        then(processedEventRepository).should(never()).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("payload 為直接 JSON（非 double-encoded）時，正常解析並處理")
    void shouldParseDirectJsonPayload() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-direct-001");
        // 直接送 JSON 物件，不 double-encode（不以 " 開頭）
        String directJson = "{\"id\":\"order-x\",\"userId\":\"U1\",\"productName\":\"AirPods\",\"quantity\":2,\"status\":\"CREATED\",\"createdAt\":\"2026\"}";
        Message message = MessageBuilder
                .withBody(directJson.getBytes())
                .andProperties(props)
                .build();

        given(processedEventRepository.existsById("msg-direct-001")).willReturn(false);

        consumer.consume(message);

        then(inventoryService).should().deductStock("order-x", "AirPods", 2);
        then(processedEventRepository).should().save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("payload 解析失敗時，拋出 RuntimeException（觸發 NACK）")
    void shouldThrowRuntimeExceptionOnParseError() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-bad-001");
        Message message = MessageBuilder
                .withBody("not-valid-json".getBytes())
                .andProperties(props)
                .build();

        given(processedEventRepository.existsById("msg-bad-001")).willReturn(false);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("消費失敗");
    }
}
