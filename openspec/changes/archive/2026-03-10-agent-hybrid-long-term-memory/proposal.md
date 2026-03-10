## Why

目前 Agent 的對話記憶採用 MessageWindow（最多 20 條），超過視窗的舊訊息將被永久捨棄。當用戶在同一個電商場景中進行長時間或多輪互動時（例如多次詢問訂單、表達偏好），Agent 無法引用早期對話，導致用戶需重複提供相同資訊，體驗下降。

## What Changes

- 新增 `RedisVectorStore` bean，使用既有 Redis Stack（RedisJSON + RediSearch）儲存對話 embedding
- 在 `ChatClient` 加入 `VectorStoreChatMemoryAdvisor`，實現語意搜尋式長期記憶層
- 保留現有 `MessageChatMemoryAdvisor`（近因層），確保最近 20 條訊息仍以結構化方式注入
- 新增 `pom.xml` 依賴：`spring-ai-advisors-vector-store`、`spring-ai-starter-vector-store-redis`
- 新增 `application-dev.yml` vector store 設定（index name、key prefix）

## Capabilities

### New Capabilities

- `agent-long-term-memory`：Agent 透過 vector store 語意搜尋保留並檢索超過 20 條的歷史對話，實現跨 run 的長期記憶

### Modified Capabilities

- `agent-chat-memory`：記憶架構從單層 MessageWindow 升級為雙層（近因 MessageWindow + 長期 VectorStore）

## Impact

- **agent-service/pom.xml**：新增兩個 Spring AI dependency
- **agent-service/src/main/java/.../config/SpringAiConfig.java**：新增 `VectorStore` bean，修改 `ChatClient` bean 加入第二個 advisor
- **agent-service/src/main/resources/application-dev.yml**：新增 vector store 設定
- **infra/docker-compose.yml**：已使用 `redis/redis-stack`（RedisJSON + RediSearch），無需變更
- **不影響** AgentRunExecutor、AgentRunService、AgentChatController（conversationId 傳遞機制不變）
