package com.coremall.agent.service;

import com.coremall.agent.dto.MessageRecord;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentMemoryService {

    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;

    public AgentMemoryService(ChatMemory chatMemory, VectorStore vectorStore) {
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
    }

    public List<MessageRecord> getHistory(String userId) {
        return chatMemory.get(userId).stream()
                .map(m -> new MessageRecord(
                        MessageType.ASSISTANT.equals(m.getMessageType()) ? "assistant" : "user",
                        m.getText()))
                .toList();
    }

    public void clear(String userId) {
        chatMemory.clear(userId);

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.eq("conversationId", userId).build());
    }
}
