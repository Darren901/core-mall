package com.coremall.agent.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class AgentMemoryService {

    private final ChatMemory chatMemory;

    public AgentMemoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public void clear(String userId) {
        chatMemory.clear(userId);
    }
}
