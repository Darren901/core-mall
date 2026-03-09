## Context

agent-service 目前以 Spring AI 1.1.2 + Gemini 2.5 Flash 實作 Function Calling + SSE streaming。每次 `POST /agent/runs` 建立一個 `AgentRun`，由 `AgentRunExecutor`（`@Async`）執行，透過 `ChatClient` 呼叫 LLM。

現有基礎設施：
- Redis（共用，port 6379）：已用於冪等 key（`idem:*`）與 Tool Call 冪等（`step:*`）
- Gateway：`JwtAuthGatewayFilterFactory` 已將 JWT 中的 `userId` 注入為 `X-User-Id` header

## Goals / Non-Goals

**Goals:**
- Agent 對話跨 run 保留 per-user 歷史（`userId` = `conversationId`）
- 使用 Spring AI `RedisChatMemoryRepository` + `MessageWindowChatMemory`（maxMessages=20）
- 提供 `DELETE /agent/memory/{userId}` 清除記憶 API
- Redis 升級至 Redis Stack（向下相容現有 key）

**Non-Goals:**
- 對話摘要或長期記憶壓縮（留給未來 RAG 階段）
- 多 session 管理（per-user 單一 conversationId，不支援平行 session）
- 記憶 TTL 設定（本次固定不過期，未來可設定化）

## Decisions

### 1. Redis Stack 取代普通 Redis

**決策**：`docker-compose.yml` 的 Redis image 從 `redis:alpine` 換成 `redis/redis-stack`。

**理由**：Spring AI `RedisChatMemoryRepository` 依賴 RedisJSON 模組儲存訊息 JSON document，以及 RediSearch 模組建立索引以支援 `deleteByConversationId`。普通 Redis 不具備這兩個模組。

**替代方案**：自行實作 `ChatMemory` 介面（用 `StringRedisTemplate` 序列化 JSON）。否決原因：需自行維護序列化、window 截斷、清除邏輯，Spring AI 已有完整實作，無需重造輪子。

**相容性**：`redis/redis-stack` 完整包含 Redis 核心，現有 `StringRedisTemplate` 操作（`idem:*`、`step:*`）無需任何修改。

---

### 2. `userId` 從 `X-User-Id` Header 讀取，不放在 Request Body

**決策**：`AgentChatController` 從 `@RequestHeader("X-User-Id")` 取得 userId，`ChatRequest` 不新增欄位。

**理由**：Gateway 已驗證 JWT 並注入 `X-User-Id`，由 controller 重複使用此值符合 DRY 原則，也避免 client 偽造 userId。

---

### 3. `MessageChatMemoryAdvisor` 注入在 call 時而非 bean 層級

**決策**：在 `AgentRunExecutor.execute()` 內動態建立 `MessageChatMemoryAdvisor(memory, userId, 20)`，而非在 `ChatClient` bean 的 `defaultAdvisors()` 設定。

**理由**：`conversationId`（userId）是 runtime 資訊，每個 request 不同，必須在 call 時注入；`defaultAdvisors()` 適合靜態、全域的 advisor（例如 logging）。

## Risks / Trade-offs

- **Redis Stack 升級風險**：現有 docker-compose 若有其他服務共用 Redis，需確認 `redis/redis-stack` 相容性。→ 本專案 Redis 為共用單一實例，升級後對 order-service 等無影響（純 key-value 操作不依賴 Stack 模組）。

- **記憶污染**：LLM 可能被舊對話的錯誤狀態（例如舊的失敗訊息）誤導。→ 提供 `DELETE /agent/memory/{userId}` 讓用戶或管理員手動清除。

- **Token 成長**：maxMessages=20 限制 window 大小，但單條訊息若很長仍可能消耗大量 token。→ 本次接受此風險，未來可加入 token counting 或摘要機制。

## Migration Plan

1. 更新 `infra/docker-compose.yml`（Redis image）→ `docker compose up -d redis` 重啟 Redis
2. 現有 Redis key（`idem:*`、`step:*`）不受影響，無需資料遷移
3. 部署新版 agent-service（含 `RedisChatMemoryRepository` 設定）
4. **Rollback**：將 image 換回 `redis:alpine`，移除 `spring-ai-starter-model-chat-memory-repository-redis` 依賴即可

## Open Questions

- `maxMessages=20` 是否為合理初始值？（可在 `application.yml` 設定化，本次先 hardcode）
- `DELETE /agent/memory/{userId}` 是否需要鑑權（只允許本人或 admin 清除）？→ 本次透過 Gateway JWT 確保呼叫者為本人，`userId` 從 header 讀取。
