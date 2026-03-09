## 1. 依賴與設定

- [x] 1.1 在 `agent-service/pom.xml` 新增 `spring-ai-advisors-vector-store` 依賴
- [x] 1.2 在 `agent-service/pom.xml` 新增 `spring-ai-starter-vector-store-redis` 依賴
- [x] 1.3 在 `application-dev.yml` 新增 vector store 設定（`initialize-schema: true`、`index-name: coremall-ltm`、`prefix: ltm:`）

## 2. RedisVectorStore Bean

- [x] 2.1 在 `SpringAiConfig` 注入 `JedisPooled` 與 `EmbeddingModel`，建立 `RedisVectorStore` bean（index: `coremall-ltm`、prefix: `ltm:`、`initializeSchema: true`）

## 3. ChatClient 串接雙層 Advisor

- [x] 3.1 修改 `SpringAiConfig.chatClient()` 加入 `VectorStoreChatMemoryAdvisor`（長期層，排在 `MessageChatMemoryAdvisor` 之前）

## 4. 清除記憶擴充

- [x] 4.1 修改 `AgentMemoryService.clear(userId)`，在清除 `RedisChatMemory` 之後，同時呼叫 `VectorStore.delete(userId filter)` 清除長期向量記憶
- [x] 4.2 更新 `AgentMemoryServiceTest`，補充驗證 `VectorStore.delete()` 有被呼叫

## 5. 測試

- [x] 5.1 驗證 `AgentRunExecutorTest` 中現有測試仍通過（advisor chain 變動不應破壞既有測試）
- [x] 5.2 執行 `mvn clean verify` 確認 JaCoCo 覆蓋率 ≥ 80%
