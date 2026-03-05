## Why

以單一聊天視窗驅動電商訂單操作，透過 AI Agent（LLM + function calling）取代傳統 CRUD API，同時作為學習微服務架構、分散式可靠性模式（分散式鎖、冪等性、Outbox + MQ、Redis Write-Behind）的實踐專案。

## What Changes

- 建立 Maven multi-module 微服務骨架：`discovery-service`（Eureka）、`gateway-service`（Spring Cloud Gateway）、`user-service`、`order-service`、`agent-service`、`shared-kernel`
- 前端唯一入口為 Agent 聊天 API，不公開訂單 CRUD endpoint
- 導入 Spring AI + Gemini function calling（`@Tool`），由 LLM 決策觸發訂單操作
- `order-service` 採 Redis Write-Behind：先寫 Redis 立即回傳，非同步 relay 至 PostgreSQL
- `order-service` 採 Outbox Pattern + RabbitMQ 確保跨服務最終一致性
- Agent 每個執行 step 留下 audit 記錄，冪等鍵防止重試時重複建單
- SSE（Flux）串流輸出 Agent 執行進度給前端

## Capabilities

### New Capabilities
- `user-authentication`: 用戶註冊、登入、JWT 簽發，Gateway 統一驗證並注入 X-User-Id header
- `order-lifecycle`: 訂單建立/更新/取消/查詢，Redis Write-Behind、分散式鎖、冪等性、Outbox + RabbitMQ
- `agent-chat-orchestration`: ChatClient + @Tool function calling、202 立即回傳、SSE 串流、Step audit 記錄、非同步 DB 寫入

### Modified Capabilities

（無既有 capability）

## Impact

- **新增服務**：discovery-service、gateway-service、user-service、order-service、agent-service、shared-kernel
- **對外 API**：僅 `/api/v1/auth/**`（user-service）與 `/api/v1/agent/**`（agent-service）經 Gateway 對外；order-service 僅供 agent-service 內部呼叫（`/internal/v1/orders/**`）
- **Dependencies**：Spring Boot 3、Spring Cloud（Gateway、Eureka）、Spring AI（Google Gemini）、PostgreSQL、Redis、RabbitMQ、Testcontainers、Virtual Thread（Java 21）
- **學習目標**：服務拆分邊界、JWT 跨服務傳遞、Redis Write-Behind、Outbox Pattern、分散式鎖、Agent step 冪等性
