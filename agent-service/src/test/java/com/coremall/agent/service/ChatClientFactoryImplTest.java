package com.coremall.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatClientFactoryImpl - LLM 模型路由")
class ChatClientFactoryImplTest {

    @Mock
    private ChatClient geminiChatClient;

    @Mock
    private ChatClient anthropicChatClient;

    private ChatClientFactoryImpl factory;

    @BeforeEach
    void setUp() {
        factory = new ChatClientFactoryImpl(geminiChatClient, anthropicChatClient);
    }

    @Test
    @DisplayName("model 為 null 時回傳 Gemini ChatClient")
    void shouldReturnGeminiClientWhenModelIsNull() {
        ChatClient result = factory.getClient(null);
        assertThat(result).isSameAs(geminiChatClient);
    }

    @Test
    @DisplayName("model 為 'google' 時回傳 Gemini ChatClient")
    void shouldReturnGeminiClientWhenModelIsGoogle() {
        ChatClient result = factory.getClient("google");
        assertThat(result).isSameAs(geminiChatClient);
    }

    @Test
    @DisplayName("model 為 'anthropic' 時回傳 Anthropic ChatClient")
    void shouldReturnAnthropicClientWhenModelIsAnthropic() {
        ChatClient result = factory.getClient("anthropic");
        assertThat(result).isSameAs(anthropicChatClient);
    }

    @Test
    @DisplayName("model 為未知值時拋出 IllegalArgumentException，訊息含 model 名稱")
    void shouldThrowIllegalArgumentExceptionForUnknownModel() {
        assertThatThrownBy(() -> factory.getClient("gpt-4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gpt-4");
    }
}
