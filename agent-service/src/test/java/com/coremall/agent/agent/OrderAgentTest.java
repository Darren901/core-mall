package com.coremall.agent.agent;

import com.coremall.agent.tool.OrderAgentTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAgent - @Tool ask 方法")
class OrderAgentTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private OrderAgentTools orderAgentTools;

    @Test
    @DisplayName("ask：userId 以 [客戶 ID: xxx] 前綴組入 user message")
    void shouldPrependUserIdInUserMessage() {
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        when(chatClient.prompt().user(messageCaptor.capture()).tools(any()).call().content())
                .thenReturn("訂單已建立，ID: ORD-123");

        OrderAgent agent = new OrderAgent(chatClient, orderAgentTools);
        agent.ask("建立訂單 iPhone 15 數量 1", "U001");

        assertThat(messageCaptor.getValue()).contains("[客戶 ID: U001]");
        assertThat(messageCaptor.getValue()).contains("建立訂單 iPhone 15 數量 1");
    }

    @Test
    @DisplayName("ask：回傳 chatClient 結果")
    void shouldReturnChatClientResponse() {
        when(chatClient.prompt().user(anyString()).tools(any()).call().content())
                .thenReturn("訂單已建立，ID: ORD-456");

        OrderAgent agent = new OrderAgent(chatClient, orderAgentTools);
        String result = agent.ask("建立訂單", "U002");

        assertThat(result).isEqualTo("訂單已建立，ID: ORD-456");
    }

    @Test
    @DisplayName("ask：chatClient 拋出例外時回傳 BUSINESS_ERROR| 前綴")
    void shouldReturnBusinessErrorOnException() {
        when(chatClient.prompt().user(anyString()).tools(any()).call().content())
                .thenThrow(new RuntimeException("LLM timeout"));

        OrderAgent agent = new OrderAgent(chatClient, orderAgentTools);
        String result = agent.ask("建立訂單", "U001");

        assertThat(result).startsWith("BUSINESS_ERROR|");
        assertThat(result).contains("LLM timeout");
    }
}
