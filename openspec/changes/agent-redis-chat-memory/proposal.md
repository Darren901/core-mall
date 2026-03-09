## Why

目前 agent-service 每次 `POST /agent/runs` 都是全新對話，Agent 無法記住上下文（例如用戶身份、先前操作的訂單），用戶必須每次重複提供資訊。企業級電商場景需要跨 run 的連貫對話體驗。

## What Changes

- 為 agent-service 引入 **Redis Stack** 作為 Chat Memory 後端
- 使用 Spring AI `RedisChatMemoryRepository` + `MessageWindowChatMemory`（maxMessages=20）實作對話記憶
- 記憶以 `userId` 為 `conversationId`，跨多個 run 保留對話歷史
- Gateway 已透過 `X-User-Id` header 傳遞 userId，無需修改認證流程
- 新增 `DELETE /agent/memory/{userId}` API，讓用戶可清除記憶（避免 LLM 被舊狀態誤導）
- `infra/docker-compose.yml` 將 Redis image 換成 `redis/redis-stack`（向下相容）

## Capabilities

### New Capabilities

- `agent-chat-memory`: Agent 跨 run 對話記憶，包含記憶讀寫與清除功能

### Modified Capabilities

（無，現有 spec 不涉及 requirement 層級變更）

## Impact

**程式碼**
- `agent-service/pom.xml`：新增 `spring-ai-starter-model-chat-memory-repository-redis`
- `agent-service/src/main/java/com/coremall/agent/config/SpringAiConfig.java`：注入 ChatMemory bean
- `agent-service/src/main/java/com/coremall/agent/service/AgentRunService.java`：傳遞 userId
- `agent-service/src/main/java/com/coremall/agent/service/AgentRunExecutor.java`：加入 `MessageChatMemoryAdvisor`
- 新增 `AgentMemoryController`、`AgentMemoryService`

**基礎設施**
- `infra/docker-compose.yml`：Redis image 換成 `redis/redis-stack`

**API**
- 新增端點：`DELETE /agent/memory/{userId}`
