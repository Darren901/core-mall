package com.coremall.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一個電商購物助理，可以幫助用戶管理訂單。
                        你可以使用工具建立訂單、更新訂單、取消訂單和查詢訂單狀態。
                        每次操作請使用唯一的 idempotency key（可用 UUID 格式）。
                        """)
                .build();
    }
}
