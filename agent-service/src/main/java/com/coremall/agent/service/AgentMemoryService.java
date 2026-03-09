package com.coremall.agent.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

@Service
public class AgentMemoryService {

    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;

    public AgentMemoryService(ChatMemory chatMemory, VectorStore vectorStore) {
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
    }

    public void clear(String userId) {
        chatMemory.clear(userId);

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.eq("conversationId", userId).build());
    }
}
