package com.coremall.agent.agent;

import com.coremall.agent.tool.InventoryAgentTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryAgent - @Tool ask 方法")
class InventoryAgentTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private InventoryAgentTools inventoryAgentTools;

    @Test
    @DisplayName("ask：呼叫 chatClient，回傳 LLM 結果")
    void shouldReturnChatClientResponse() {
        when(chatClient.prompt().user(anyString()).tools(any()).call().content())
                .thenReturn("iPhone 15 庫存：10 件，有貨");

        InventoryAgent agent = new InventoryAgent(chatClient, inventoryAgentTools);
        String result = agent.ask("iPhone 15");

        assertThat(result).isEqualTo("iPhone 15 庫存：10 件，有貨");
    }

    @Test
    @DisplayName("ask：productName 作為 user message 傳入 chatClient")
    void shouldPassProductNameAsUserMessage() {
        when(chatClient.prompt().user("MacBook Pro").tools(any()).call().content())
                .thenReturn("MacBook Pro 庫存：5 件，有貨");

        InventoryAgent agent = new InventoryAgent(chatClient, inventoryAgentTools);
        String result = agent.ask("MacBook Pro");

        assertThat(result).contains("MacBook Pro");
    }

    @Test
    @DisplayName("ask：chatClient 拋出例外時回傳 BUSINESS_ERROR| 前綴")
    void shouldReturnBusinessErrorOnException() {
        when(chatClient.prompt().user(anyString()).tools(any()).call().content())
                .thenThrow(new RuntimeException("LLM 呼叫失敗"));

        InventoryAgent agent = new InventoryAgent(chatClient, inventoryAgentTools);
        String result = agent.ask("iPhone 15");

        assertThat(result).startsWith("BUSINESS_ERROR|");
        assertThat(result).contains("LLM 呼叫失敗");
    }
}
