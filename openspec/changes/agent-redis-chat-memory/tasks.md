## 1. 基礎設施升級

- [x] 1.1 將 `infra/docker-compose.yml` 的 Redis image 從 `redis:alpine` 改為 `redis/redis-stack`
- [x] 1.2 重啟 Redis 容器，確認 Redis Stack 正常啟動（port 6379 可連線、RedisJSON 模組已載入）

## 2. 依賴與設定

- [x] 2.1 在 `agent-service/pom.xml` 新增 `spring-ai-starter-model-chat-memory-repository-redis` 依賴
- [x] 2.2 在 `application-dev.yml` 新增 Redis Chat Memory 設定（index name、key prefix）

## 3. ChatMemory Bean 設定

- [x] 3.1 在 `SpringAiConfig` 注入 `RedisChatMemoryRepository` bean（使用 auto-configuration）
- [x] 3.2 在 `SpringAiConfig` 建立 `MessageWindowChatMemory` bean（maxMessages=20）

## 4. Agent Run 串接記憶

- [x] 4.1 在 `AgentChatController` 的 run 端點加入 `@RequestHeader("X-User-Id") String userId` 參數，並驗證不可為空
- [x] 4.2 修改 `AgentRunService.start()` 接收並傳遞 `userId` 給 `AgentRunExecutor`
- [x] 4.3 修改 `AgentRunExecutor.execute()` 接收 `userId`，在 ChatClient call 時加入 `MessageChatMemoryAdvisor(memory, userId, 20)`

## 5. 清除記憶 API

- [x] 5.1 新增 `AgentMemoryService`，實作 `clear(userId)` 呼叫 `chatMemoryRepository.deleteByConversationId(userId)`
- [x] 5.2 新增 `AgentMemoryController`，提供 `DELETE /agent/memory/{userId}` 端點，回傳 `204 No Content`

## 6. 測試

- [x] 6.1 為 `AgentMemoryService` 撰寫單元測試（clear 成功、clear 不存在 userId 不拋例外）
- [x] 6.2 為 `AgentMemoryController` 撰寫單元測試（204 回應、委派正確 userId）
- [x] 6.3 為 `AgentRunExecutor` 撰寫單元測試，驗證 `MessageChatMemoryAdvisor` 有被正確注入
- [x] 6.4 執行 `mvn clean verify` 確認 JaCoCo 覆蓋率 ≥ 80%
