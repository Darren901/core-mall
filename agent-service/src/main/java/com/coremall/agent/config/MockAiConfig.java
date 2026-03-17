package com.coremall.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * loadtest profile 專用的 AI 設定。
 * 以 MockChatModel 替換真實 LLM，讓壓測流量通過完整架構（SSE、DB、冪等）
 * 但不消耗 LLM API quota 也不產生費用。
 * VectorStore（長期記憶）在 loadtest 中跳過，避免 EmbeddingModel 依賴與 Redis RediSearch 額外壓力。
 */
@Configuration
@Profile("loadtest")
public class MockAiConfig {

    private static final String SYSTEM_PROMPT = "[LOADTEST MODE] 固定回應，不呼叫真實 LLM。";

    @Bean("geminiChatClient")
    public ChatClient geminiChatClient(ChatMemory chatMemory) {
        return buildChatClient(chatMemory);
    }

    @Bean("anthropicChatClient")
    public ChatClient anthropicChatClient(ChatMemory chatMemory) {
        return buildChatClient(chatMemory);
    }

    private ChatClient buildChatClient(ChatMemory chatMemory) {
        return ChatClient.builder(new MockChatModel())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    /** loadtest 用 no-op VectorStore，讓 AgentMemoryService 可以啟動，操作皆靜默忽略。 */
    @Bean
    public VectorStore noOpVectorStore() {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {}

            @Override
            public void delete(List<String> idList) {}

            @Override
            public void delete(Filter.Expression filterExpression) {}

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                return List.of();
            }
        };
    }
}
