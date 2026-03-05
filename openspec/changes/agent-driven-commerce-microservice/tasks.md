## 1. 基礎建設：Discovery + Gateway

- [x] 1.1 建立 `discovery-service` 模組（Eureka Server），加入 parent pom
- [x] 1.2 建立 `gateway-service` 模組（Spring Cloud Gateway），加入 parent pom
- [x] 1.3 設定 gateway 路由：`/api/v1/auth/**` → user-service，`/api/v1/agent/**` → agent-service
- [x] 1.4 實作 Gateway JWT 驗證 Filter（`JwtAuthGatewayFilterFactory`，route-level）：驗證通過後注入 `X-User-Id` header
- [x] 1.5 各服務 application-dev.yml 加入 Eureka 註冊設定，驗證服務能互相發現

## 2. shared-kernel

- [ ] 2.1 實作 JWT 工具類（sign / parse），以 `@ConfigurationProperties` 管理 secret 與 TTL
- [ ] 2.2 定義共用 Response 格式（ApiResponse wrapper）與 ErrorCode 常數
- [ ] 2.3 確認 shared-kernel 能被其他模組引用，mvn clean compile 通過

## 3. user-service

- [ ] 3.1 建立 Flyway migration：`V1__create_users_table.sql`
- [ ] 3.2 實作 `User` entity 與 `UserRepository`
- [ ] 3.3 實作 `AuthService`：register（BCrypt）、login（驗密碼 + 簽 JWT）
- [ ] 3.4 實作 `AuthController`：`POST /api/v1/auth/register`（201）、`POST /api/v1/auth/login`（200）
- [ ] 3.5 寫單元測試：register 重複 email 回 409、login 錯誤密碼回 401
- [ ] 3.6 寫整合測試（Testcontainers PostgreSQL）：完整 register → login 流程

## 4. order-service：核心 CRUD + Redis Write-Behind

- [ ] 4.1 建立 Flyway migration：`V1__create_orders_table.sql`、`V2__create_outbox_events_table.sql`
- [ ] 4.2 實作 `Order` entity、`OutboxEvent` entity 與對應 Repository
- [ ] 4.3 實作 `RedisConfig`：StringRedisTemplate，定義 key 命名規則與 TTL
- [ ] 4.4 實作 `OrderCommandService`：write-behind 流程（寫 Redis → 回傳）
- [ ] 4.5 實作 `OrderQueryService`：Redis 優先查詢，cache miss fallback 到 PostgreSQL
- [ ] 4.6 實作 `OrderController`（`/internal/v1/orders/**`）：POST / PATCH / DELETE / GET
- [ ] 4.7 實作分散式鎖（Redis SETNX + TTL），整合進 update / cancel 流程
- [ ] 4.8 實作冪等鍵檢查（Redis，key = `idem:{idempotency-key}`）
- [ ] 4.9 實作 `OutboxRelayService`（`@Scheduled`）：讀 Redis → 寫 PostgreSQL + outbox_event（同交易）
- [ ] 4.10 寫單元測試：冪等鍵重複請求回傳同一訂單、並發鎖衝突回傳 transient error
- [ ] 4.11 寫整合測試（Testcontainers PostgreSQL + Redis）：write-behind relay 後 DB 有資料

## 5. order-service：Outbox + RabbitMQ

- [ ] 5.1 實作 `RabbitMQConfig`：exchange / queue / binding 定義
- [ ] 5.2 實作 Outbox Relay Publisher：讀 unpublished events → publish → 標記 published
- [ ] 5.3 實作消費端冪等性：`processed_events` table，重複訊息只處理一次
- [ ] 5.4 寫整合測試（Testcontainers RabbitMQ）：outbox event 成功 publish 且不重複消費

## 6. agent-service：ChatClient + Function Calling

- [ ] 6.1 建立 Flyway migration：`V1__create_agent_runs_table.sql`、`V2__create_agent_steps_table.sql`
- [ ] 6.2 實作 `AgentRun` / `AgentStep` entity 與 Repository
- [ ] 6.3 設定 `WebClientConfig`：order-service 內部呼叫用的 WebClient（base URL 從 Eureka 解析）
- [ ] 6.4 設定 `SpringAiConfig`：ChatClient bean，綁定 Gemini API key
- [ ] 6.5 實作 `OrderAgentTools`：四個 `@Tool` 方法（createOrder / updateOrder / cancelOrder / getOrderStatus）
- [ ] 6.6 每個 @Tool 方法：先查 Redis 冪等鍵（同步），再呼叫 order-service，`@Async` 寫 AgentStep
- [ ] 6.7 實作 `AgentRunService`：建立 AgentRun → 背景執行 ChatClient → Sinks 推 SSE 事件
- [ ] 6.8 實作 `AgentChatController`：`POST /api/v1/agent/chat`（202）、`GET /api/v1/agent/sessions/{id}/stream`（SSE）
- [ ] 6.9 寫單元測試：mock ChatClient，驗證三步驟訊息觸發正確的 tool 序列
- [ ] 6.10 寫整合測試：完整 chat → SSE 事件序列 → AgentStep DB 記錄驗證

## 7. 驗證與品質

- [ ] 7.1 所有服務 `mvn clean verify`，line / branch coverage ≥ 80%
- [ ] 7.2 啟動全部服務（docker-compose + 5 個 Spring Boot），執行端對端手動測試
- [ ] 7.3 測試多步驟訊息（e.g. 「訂5個蘋果，改成3個，查狀態」）→ 驗證 3 個 AgentStep 記錄與 SSE 事件
- [ ] 7.4 測試冪等性：相同 idempotency key 重複送出，確認訂單不重複建立
- [ ] 7.5 整理實作筆記：每個高併發模式的學習心得
