package com.coremall.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ChatClientFactoryImpl implements ChatClientFactory {

    private final ChatClient geminiChatClient;
    private final ChatClient anthropicChatClient;

    public ChatClientFactoryImpl(
            @Qualifier("geminiChatClient") ChatClient geminiChatClient,
            @Qualifier("anthropicChatClient") ChatClient anthropicChatClient) {
        this.geminiChatClient = geminiChatClient;
        this.anthropicChatClient = anthropicChatClient;
    }

    @Override
    public ChatClient getClient(String model) {
        if (model == null || "google".equals(model)) {
            return geminiChatClient;
        }
        if ("anthropic".equals(model)) {
            return anthropicChatClient;
        }
        throw new IllegalArgumentException("不支援的模型: " + model);
    }
}
