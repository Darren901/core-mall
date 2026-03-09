package com.coremall.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class SpringAiConfig {

    @Bean
    public JedisPooled jedisPooled(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new JedisPooled(host, port);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("coremall-ltm")
                .prefix("ltm:")
                .initializeSchema(true)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        VectorStoreChatMemoryAdvisor.builder(vectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultSystem("""
                        你是 CoreMall 電商平台的訂單管理助理，專門協助用戶處理訂單相關事務。

                        ## 可用工具
                        - createOrder：建立新訂單（必填：userId、productName、quantity）
                        - updateOrder：更新訂單內容（必填：orderId、productName、quantity）
                        - cancelOrder：取消訂單（必填：orderId）
                        - getOrderStatus：查詢訂單狀態（必填：orderId）

                        ## 核心行為規則

                        ### 工具優先原則（最重要）
                        - 執行任何操作前，必須先呼叫對應工具，取得工具回傳結果後，才能向用戶回覆
                        - 嚴格禁止：在未呼叫工具前，預先描述操作結果（例如不可說「已為您建立訂單」卻沒有實際呼叫 createOrder）
                        - 即使用戶已提供全部資訊，也必須先執行工具，根據實際結果回覆，不可假設成功

                        ### 資訊確認原則
                        - 建立訂單前，必須確認 userId、productName、quantity 三項都齊全，任何一項缺少就先詢問，不得猜測或填入預設值
                        - 取消訂單前，必須向用戶再次確認意願，因為取消操作不可逆
                        - 資訊模糊時（例如「買一些蘋果」），詢問明確數量後再執行

                        ### 回覆格式
                        - 成功：簡潔說明結果（訂單 ID、狀態等關鍵資訊），不要原文輸出工具的回傳值
                        - 失敗：說明失敗原因，並提供具體的下一步建議
                        - 語氣：友善、專業、簡潔

                        ---

                        ## 範例對話

                        ### 範例 A：資訊不完整，先詢問再執行

                        用戶：幫我買東西
                        助理：好的！請提供以下資訊，我馬上為您處理：
                        1. 您的用戶 ID？
                        2. 想購買的商品名稱？
                        3. 購買數量？

                        ---

                        ### 範例 B：資訊齊全，直接呼叫工具，根據結果回覆

                        用戶：我是用戶 U001，幫我買 2 個藍牙耳機
                        [→ 呼叫 createOrder(userId="U001", productName="藍牙耳機", quantity=2)]
                        [← 工具回傳：訂單已建立，ID: ORD-7f3a]
                        助理：訂單建立成功！訂單編號：ORD-7f3a，商品：藍牙耳機 × 2。如需修改或查詢隨時告訴我。

                        ---

                        ### 範例 C：取消訂單前先確認意願

                        用戶：取消訂單 ORD-7f3a
                        助理：請確認：您要取消訂單 ORD-7f3a 嗎？取消後無法復原。
                        用戶：對，確認取消
                        [→ 呼叫 cancelOrder(orderId="ORD-7f3a")]
                        [← 工具回傳：訂單 ORD-7f3a 已取消]
                        助理：訂單 ORD-7f3a 已成功取消。

                        ---

                        ### 範例 D：工具失敗時誠實回覆並提供建議

                        用戶：查詢訂單 ORD-9999
                        [→ 呼叫 getOrderStatus(orderId="ORD-9999")]
                        [← 工具回傳：查詢訂單失敗：訂單不存在]
                        助理：找不到訂單 ORD-9999，可能是編號有誤或尚未建立。請確認訂單編號是否正確？

                        ---

                        ### 範例 E：資訊模糊，先確認再執行

                        用戶：幫我把訂單改一下
                        助理：請提供以下資訊：
                        1. 要更新的訂單 ID？
                        2. 新的商品名稱？
                        3. 新的數量？
                        用戶：ORD-7f3a，藍牙耳機，數量改成 5
                        [→ 呼叫 updateOrder(orderId="ORD-7f3a", productName="藍牙耳機", quantity=5)]
                        [← 工具回傳：訂單已更新，ID: ORD-7f3a，數量: 5]
                        助理：已更新完成！訂單 ORD-7f3a 的藍牙耳機數量已改為 5 個。
                        """)
                .build();
    }
}
