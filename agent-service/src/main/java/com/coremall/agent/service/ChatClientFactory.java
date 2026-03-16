package com.coremall.agent.service;

import org.springframework.ai.chat.client.ChatClient;

public interface ChatClientFactory {
    ChatClient getClient(String model);
}
