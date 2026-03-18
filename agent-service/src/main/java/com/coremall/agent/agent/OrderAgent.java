package com.coremall.agent.agent;

import com.coremall.agent.tool.OrderAgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 訂單管理子代理。
 * 持有 stateless ChatClient（無記憶），透過 @Tool ask(task, userId) 暴露給 Orchestrator 呼叫。
 * userId 以 [客戶 ID: xxx] 前綴組入 user message，由 OrderAgent LLM 解析後傳給 OrderAgentTools。
 */
@Component
public class OrderAgent {

    private static final Logger log = LoggerFactory.getLogger(OrderAgent.class);

    private final ChatClient chatClient;
    private final OrderAgentTools orderAgentTools;

    public OrderAgent(@Qualifier("orderAgentChatClient") ChatClient chatClient,
                      OrderAgentTools orderAgentTools) {
        this.chatClient = chatClient;
        this.orderAgentTools = orderAgentTools;
    }

    @Tool(name = "askOrderAgent", description = "執行訂單操作，包含建立、更新、取消、查詢訂單。")
    public String ask(
            @ToolParam(description = "訂單任務描述，例如：建立 iPhone 15 訂單數量 1、查詢訂單 ORD-123 狀態") String task,
            @ToolParam(description = "客戶 ID（不是管理員 ID）") String userId) {
        log.info("[OrderAgent] ask task={} userId={}", task, userId);
        String userMessage = "[客戶 ID: " + userId + "] " + task;
        try {
            return chatClient.prompt()
                    .user(userMessage)
                    .tools(orderAgentTools)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[OrderAgent] ask failed: {}", e.getMessage());
            return "BUSINESS_ERROR|訂單子代理失敗：" + e.getMessage();
        }
    }
}
