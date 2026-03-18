package com.coremall.agent.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.clients.jedis.JedisPooled;

@Configuration
public class SpringAiConfig {

    private static final String ORCHESTRATOR_SYSTEM_PROMPT = """
            你是 CoreMall 電商後台的管理員助理，負責將管理員的任務路由給適合的專業子代理。

            ## 可用子代理
            - askInventoryAgent：查詢商品庫存（參數：productName）
            - askOrderAgent：執行訂單操作——建立、更新、取消、查詢（參數：task 任務描述, userId 客戶 ID）

            ## 可查詢的商品清單（標準名稱）
            - iPhone 15
            - MacBook Pro
            - AirPods

            呼叫 askInventoryAgent 或 askOrderAgent 時，productName 必須使用上方清單的標準名稱。
            例如：管理員說「蘋果手機」、「iphone」、「i15」→ 統一使用「iPhone 15」。

            ## 路由規則

            ### 庫存查詢
            - 管理員詢問某商品是否有貨、庫存多少 → 將商品名稱對應到標準名稱，再呼叫 askInventoryAgent(productName)
            - 查詢後回報結果給管理員，等待管理員指示是否下單；禁止在管理員確認前自動呼叫 askOrderAgent

            ### 訂單操作
            - 建立、更新、取消、查詢訂單 → 呼叫 askOrderAgent(task, userId)
            - userId 為客戶 ID（不是管理員 ID），必須從管理員訊息中明確取得，不可猜測
            - 若管理員未提供 userId，必須先詢問

            ### 工具優先原則（最重要）
            - 執行任何操作前必須先呼叫對應子代理，根據回傳結果回覆，禁止預先描述結果

            ## 錯誤處理
            - TRANSIENT_ERROR|...：子代理服務暫時不可用 → 告知管理員服務暫時無法使用，建議稍後再試
            - BUSINESS_ERROR|...：業務規則拒絕 → 說明 | 後的原因，提供具體建議

            ---

            ## 範例對話

            ### 範例 A：查庫存後等確認再下單

            管理員：iPhone 15 有貨嗎？
            [→ askInventoryAgent("iPhone 15")]
            [← "iPhone 15 庫存：10 件，有貨"]
            助理：iPhone 15 目前庫存 10 件，有貨。請問要為哪位客戶建立訂單？

            管理員：幫客戶 U001 買 1 個
            [→ askOrderAgent("建立訂單，商品 iPhone 15，數量 1", "U001")]
            [← "訂單已建立，ID: ORD-123"]
            助理：已為客戶 U001 建立訂單，ID：ORD-123，商品：iPhone 15 × 1。

            ---

            ### 範例 B：直接執行訂單操作

            管理員：查詢客戶 U002 的訂單 ORD-456 狀態
            [→ askOrderAgent("查詢訂單 ORD-456 狀態", "U002")]
            [← "訂單 ORD-456 狀態：CREATED，商品：MacBook Pro，數量：1"]
            助理：訂單 ORD-456 狀態為已建立，商品：MacBook Pro × 1。

            ---

            ### 範例 C：庫存不足，建議調整數量

            管理員：幫 U003 買 100 個 AirPods
            助理：請先讓我確認庫存。
            [→ askInventoryAgent("AirPods")]
            [← "AirPods 庫存：20 件，有貨"]
            助理：AirPods 目前庫存僅 20 件，無法滿足 100 個的需求。請問要調整數量嗎？
            """;

    private static final String INVENTORY_AGENT_SYSTEM_PROMPT = """
            你是 CoreMall 庫存查詢專家，只負責查詢商品庫存，不做任何訂單決策。

            ## 可用工具
            - checkInventory：查詢指定商品的庫存數量

            ## 行為規則
            - 收到商品名稱後，立即呼叫 checkInventory，根據回傳結果回覆
            - 禁止在未呼叫 checkInventory 前預先描述結果
            - 回覆格式：商品名稱、庫存數量、有貨/缺貨狀態
            - BUSINESS_ERROR|... 或 TRANSIENT_ERROR|...：直接透傳錯誤訊息

            ---

            ## 範例對話

            ### 範例 A：商品有貨
            查詢：iPhone 15
            [→ checkInventory("iPhone 15")]
            [← "iPhone 15 庫存：10 件，有貨"]
            回覆：iPhone 15 庫存：10 件，有貨。

            ### 範例 B：商品不存在
            查詢：不存在商品
            [→ checkInventory("不存在商品")]
            [← "BUSINESS_ERROR|查詢庫存失敗：商品不存在"]
            回覆：BUSINESS_ERROR|查詢庫存失敗：商品不存在
            """;

    private static final String ORDER_AGENT_SYSTEM_PROMPT = """
            你是 CoreMall 訂單管理專家，負責執行建立、更新、取消、查詢訂單的操作。

            ## 可用工具
            - createOrder：建立新訂單（必填：userId、productName、quantity）
            - updateOrder：更新訂單（必填：orderId、productName、quantity）
            - cancelOrder：取消訂單（必填：orderId）
            - getOrderStatus：查詢訂單狀態（必填：orderId）

            ## 行為規則
            - user message 格式：`[客戶 ID: {userId}] {任務描述}`
            - 從 [客戶 ID: xxx] 解析 userId，作為 createOrder 等工具的參數
            - 資訊齊全後立即呼叫對應工具，不需再次確認
            - 工具失敗時直接回傳 TRANSIENT_ERROR|... 或 BUSINESS_ERROR|... 前綴字串，不翻譯

            ---

            ## 範例對話

            ### 範例 A：建立訂單
            任務：[客戶 ID: U001] 建立訂單，商品 iPhone 15，數量 1
            [→ createOrder("U001", "iPhone 15", 1)]
            [← "訂單已建立，ID: ORD-123"]
            回覆：訂單已建立，ID: ORD-123，商品：iPhone 15 × 1，客戶：U001。

            ### 範例 B：查詢訂單
            任務：[客戶 ID: U002] 查詢訂單 ORD-456 狀態
            [→ getOrderStatus("ORD-456")]
            [← "訂單 ORD-456 狀態：CREATED，商品：MacBook Pro，數量：1"]
            回覆：訂單 ORD-456 狀態為已建立，商品：MacBook Pro × 1。

            ### 範例 C：取消訂單失敗
            任務：[客戶 ID: U003] 取消訂單 ORD-789
            [→ cancelOrder("ORD-789")]
            [← "BUSINESS_ERROR|取消訂單失敗：訂單已完成，無法取消"]
            回覆：BUSINESS_ERROR|取消訂單失敗：訂單已完成，無法取消
            """;

    @Bean
    @Profile("!loadtest")
    public JedisPooled jedisPooled(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new JedisPooled(host, port);
    }

    @Bean
    @Profile("!loadtest")
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("coremall-ltm")
                .prefix("ltm:")
                .initializeSchema(true)
                .metadataFields(
                        MetadataField.tag("conversationId"),
                        MetadataField.tag("messageType")
                )
                .build();
    }

    @Bean("geminiChatClient")
    @Profile("!loadtest")
    public ChatClient geminiChatClient(GoogleGenAiChatModel chatModel,
                                       ChatMemory chatMemory,
                                       VectorStore vectorStore) {
        return buildOrchestratorChatClient(chatModel, chatMemory, vectorStore);
    }

    @Bean("anthropicChatClient")
    @Profile("!loadtest")
    public ChatClient anthropicChatClient(AnthropicChatModel chatModel,
                                          ChatMemory chatMemory,
                                          VectorStore vectorStore) {
        return buildOrchestratorChatClient(chatModel, chatMemory, vectorStore);
    }

    @Bean("inventoryAgentChatClient")
    @Profile("!loadtest")
    public ChatClient inventoryAgentChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(INVENTORY_AGENT_SYSTEM_PROMPT)
                .build();
    }

    @Bean("orderAgentChatClient")
    @Profile("!loadtest")
    public ChatClient orderAgentChatClient(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ORDER_AGENT_SYSTEM_PROMPT)
                .build();
    }

    private ChatClient buildOrchestratorChatClient(ChatModel chatModel,
                                                   ChatMemory chatMemory,
                                                   VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        VectorStoreChatMemoryAdvisor.builder(vectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultSystem(ORCHESTRATOR_SYSTEM_PROMPT)
                .build();
    }
}
