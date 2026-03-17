package com.coremall.agent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockChatModel - loadtest 用假 ChatModel，不呼叫外部 LLM")
class MockChatModelTest {

    private MockChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = new MockChatModel();
    }

    @Test
    @DisplayName("call() 回傳非 null 的 ChatResponse")
    void shouldReturnNonNullChatResponse() {
        Prompt prompt = new Prompt("查詢訂單 ORD-123");

        ChatResponse response = mockChatModel.call(prompt);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("call() 的 output content 不為空")
    void shouldReturnNonEmptyContent() {
        Prompt prompt = new Prompt("建立訂單");

        ChatResponse response = mockChatModel.call(prompt);

        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }

    @Test
    @DisplayName("call() 不論 prompt 內容，都回傳相同固定回應（loadtest 固定模式）")
    void shouldReturnSameResponseRegardlessOfPrompt() {
        String reply1 = mockChatModel.call(new Prompt("查詢訂單")).getResult().getOutput().getText();
        String reply2 = mockChatModel.call(new Prompt("建立訂單 userId=U001 productName=藍牙耳機 quantity=2"))
                .getResult().getOutput().getText();

        assertThat(reply1).isEqualTo(reply2);
    }

    @Test
    @DisplayName("call() 回傳的 output 為 AssistantMessage 型別")
    void shouldReturnAssistantMessageAsOutput() {
        Prompt prompt = new Prompt("測試訊息");

        ChatResponse response = mockChatModel.call(prompt);

        assertThat(response.getResult().getOutput()).isInstanceOf(AssistantMessage.class);
    }
}
