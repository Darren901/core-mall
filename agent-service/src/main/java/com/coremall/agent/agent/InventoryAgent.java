package com.coremall.agent.agent;

import com.coremall.agent.tool.InventoryAgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 庫存查詢子代理。
 * 持有 stateless ChatClient（無記憶），透過 @Tool ask(productName) 暴露給 Orchestrator 呼叫。
 */
@Component
public class InventoryAgent {

    private static final Logger log = LoggerFactory.getLogger(InventoryAgent.class);

    private final ChatClient chatClient;
    private final InventoryAgentTools inventoryAgentTools;

    public InventoryAgent(@Qualifier("inventoryAgentChatClient") ChatClient chatClient,
                          InventoryAgentTools inventoryAgentTools) {
        this.chatClient = chatClient;
        this.inventoryAgentTools = inventoryAgentTools;
    }

    @Tool(description = "查詢指定商品的庫存狀況，回傳庫存數量與是否有貨。")
    public String ask(
            @ToolParam(description = "要查詢庫存的商品名稱") String productName) {
        log.info("[InventoryAgent] ask productName={}", productName);
        try {
            return chatClient.prompt()
                    .user(productName)
                    .tools(inventoryAgentTools)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[InventoryAgent] ask failed: {}", e.getMessage());
            return "BUSINESS_ERROR|庫存查詢子代理失敗：" + e.getMessage();
        }
    }
}
