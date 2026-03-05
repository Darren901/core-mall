## Context

全新綠地專案，以學習微服務架構為主要目標。開發者熟悉 AI Agent（function calling）但對微服務較陌生，因此設計重視概念清晰度與學習梯度。系統以聊天視窗為唯一入口，由 LLM 驅動訂單操作，並實踐高併發可靠性模式。

## Goals / Non-Goals

**Goals:**
- 建立清楚邊界的微服務架構，每個服務職責單一
- 實踐 Redis Write-Behind、Outbox Pattern、分散式鎖、冪等性等高併發模式
- 真實的 LLM function calling（非 keyword matching）
- 每個架構決策都有明確的學習理由

**Non-Goals:**
- 生產級安全強化（mTLS、secret rotation）
- 完整 API Gateway 認證中心（OAuth2 / OIDC）
- 庫存服務、通知服務等延伸模組（未來擴充）
- 前端 UI 實作

## Decisions

### D1：服務拆分

| 服務 | 職責 | 對外 |
|---|---|---|
| `discovery-service` | Eureka Server，服務註冊中心 | 否 |
| `gateway-service` | Spring Cloud Gateway，唯一對外入口，JWT 驗證 | 是（:8080） |
| `user-service` | 註冊、登入、JWT 簽發 | 經 Gateway |
| `order-service` | 訂單 CRUD，內部服務 | 否（/internal/） |
| `agent-service` | ChatClient + SSE，呼叫 order-service | 經 Gateway |
| `shared-kernel` | JWT 工具類、共用 DTO | Library |

**理由**：order-service 不經 Gateway 暴露，強制所有訂單操作必須透過 Agent，符合「聊天視窗為唯一入口」的業務設計。

---

### D2：服務間通訊（C 方案）

- **同步 REST（WebClient）**：agent-service → order-service tool call，LLM 需要立即拿到訂單 ID
- **非同步 MQ（RabbitMQ + Outbox）**：order-service 的跨服務副作用，未來擴充通知、庫存等

**理由**：純 MQ 無法讓 LLM 立即得到 tool call 結果；純 REST 無法保證最終一致性。兩者分工最自然。

---

### D3：JWT 驗證在 Gateway 統一處理

Gateway 驗證 JWT 後注入 `X-User-Id` header，下游服務信任此 header，不重複驗證。
`shared-kernel` 提供 JWT sign/parse 工具，user-service 用於簽發，Gateway 用於驗證。

**理由**：避免各服務重複實作 JWT 驗證邏輯，Gateway 是天然的 cross-cutting concern 處理點。

---

### D4：order-service Redis Write-Behind

```
agent tool call
  → 1. 取得分散式鎖（Redis SETNX + TTL）
  → 2. 檢查冪等鍵（Redis GET）→ 已存在直即回傳
  → 3. 寫入 Redis（order JSON）
  → 4. 釋放鎖，立即回傳結果
  → 5. @Scheduled Relay：Redis → PostgreSQL + outbox_event（同交易）
  → 6. Outbox Relay：publish to RabbitMQ → 標記 published
```

**理由**：高吞吐寫入場景，寫 Redis 毫秒級，DB 寫入退出關鍵路徑。代價是需要處理 Redis crash 的資料遺失風險（學習目標）。

---

### D5：agent-service Step 冪等性

- 冪等鍵存 Redis（毫秒級檢查），防止 LLM retry 重複執行 tool
- AgentStep DB 寫入（STARTED / SUCCEEDED / FAILED）全部 `@Async`，不阻塞 tool call 關鍵路徑
- 冪等鍵檢查**必須同步**（Redis），確保防重準確性

**理由**：Step audit 是 observability 需求，非業務關鍵路徑，非同步寫入不影響正確性。

---

### D6：建置順序（由下往上）

`user-service` → `order-service` → `agent-service`

**理由**：user-service 最乾淨（只有 JPA + JWT），建立標準微服務結構後，order-service 加入複雜的快取/MQ 模式，最後 agent-service 整合所有服務。學習曲線平滑，每層都可獨立啟動驗證。

---

### D7：package 結構（各服務統一）

```
controller/
service/
tool/          ← 僅 agent-service
jpa/
  entity/
  repository/
dto/
config/
```

---

### D8：Virtual Thread

所有服務啟用 `spring.threads.virtual.enabled: true`，blocking I/O（JPA、Redis、WebClient）不佔 carrier thread。

## Risks / Trade-offs

| 風險 | 緩解策略 |
|---|---|
| Redis crash 導致 Write-Behind 資料遺失 | Redis AOF 持久化（infra docker-compose 設定）；學習目標：理解此 trade-off |
| Outbox relay 重試導致 MQ 重複訊息 | Consumer 端冪等性（處理過的 event ID 記錄在 DB） |
| LLM 選錯 tool 或參數不足 | @Tool description 寫清楚；tool call 結果回傳錯誤訊息讓 LLM 修正 |
| agent-service 同步呼叫 order-service 失敗 | WebClient retry + AgentStep 記錄失敗原因，前端可重送 |
| 分散式鎖 TTL 設定不當（鎖過期但操作未完成） | TTL > 最壞情況操作時間；Write-Behind relay 要有冪等保護 |

## Open Questions

- RabbitMQ exchange / queue 命名規範（待 order-service 實作時定義）
- Redis key TTL 策略（order 快取多久過期）
- agent-service 是否需要 session 持久化（目前 Sinks 在記憶體，重啟丟失）
