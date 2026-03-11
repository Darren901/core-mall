# Core Mall — Event Map

## Exchange 總覽

| Exchange | 類型 | 建立者 | 用途 |
|---|---|---|---|
| `order.events` | Topic | order-service | 訂單生命週期事件 |
| `inventory.events` | Topic | inventory-service | 庫存補償事件 |

---

## Queue 總覽

| Queue | Binding Exchange | Routing Key | 消費者 | 說明 |
|---|---|---|---|---|
| `order.events.queue` | `order.events` | `order.#` | order-service（整合測試用） | OutboxPublisher 驗證用，無業務 consumer |
| `inventory.order.queue` | `order.events` | `order.ORDER_CREATED` | inventory-service | 扣庫存 |
| `inventory.order.queue` | `order.events` | `order.ORDER_CANCELLED` | inventory-service | 返庫 |
| `inventory.events.queue` | `inventory.events` | `inventory.#` | order-service | 接收庫存不足補償事件 |

> `inventory.order.queue` 有兩個 binding，同一個 queue 同時訂閱 CREATED 和 CANCELLED。

---

## Routing Key 定義

| Routing Key | 發佈者 | 觸發時機 |
|---|---|---|
| `order.ORDER_CREATED` | order-service OutboxPublisher | 訂單建立並 relay 至 DB |
| `order.ORDER_UPDATED` | order-service OutboxPublisher | 訂單更新並 relay 至 DB |
| `order.ORDER_CANCELLED` | order-service OutboxPublisher | **使用者主動取消**（庫存已扣，需返庫） |
| `order.ORDER_SAGA_CANCELLED` | order-service OutboxPublisher | **Saga 補償取消**（庫存未扣，不返庫） |
| `inventory.INSUFFICIENT` | inventory-service | 庫存不足，觸發 Saga 補償 |

---

## 完整事件流程

### 正常下單流程

```
Agent / Client
    │ POST /api/orders
    ▼
order-service
    ├── 寫 Redis（CREATED）
    └── OutboxRelayService（@Scheduled 5s）
            ├── 寫 PostgreSQL（orders table）
            └── 寫 outbox_events（ORDER_CREATED）
                        │
            OutboxPublisher（@Scheduled）
                        │ order.events exchange
                        │ routing key: order.ORDER_CREATED
                        ▼
            ┌───────────────────────────┐
            │    inventory.order.queue  │
            └───────────┬───────────────┘
                        │
            inventory-service OrderEventConsumer
                        │ payload.status == "CREATED"
                        ▼
            InventoryService.deductStock()
                        ├── 庫存足夠 → 扣庫存，結束 ✅
                        └── 庫存不足 → 發補償事件
                                    │ inventory.events exchange
                                    │ routing key: inventory.INSUFFICIENT
                                    ▼
                        ┌──────────────────────────┐
                        │  inventory.events.queue  │
                        └──────────┬───────────────┘
                                   │
                        order-service InventoryEventConsumer
                                   │ cancelOrderBySaga(orderId)
                                   ▼
                        訂單狀態 → SAGA_CANCELLED
                        relay → ORDER_SAGA_CANCELLED outbox
                        （inventory 不訂閱此 routing key，不返庫）
```

### 使用者取消流程（庫存返還）

```
Agent / Client
    │ DELETE /api/orders/{id}
    ▼
order-service
    ├── 寫 Redis（CANCELLED）
    └── OutboxRelayService
            ├── 寫 PostgreSQL（status=CANCELLED）
            └── 寫 outbox_events（ORDER_CANCELLED）
                        │
            OutboxPublisher
                        │ order.events exchange
                        │ routing key: order.ORDER_CANCELLED
                        ▼
            ┌───────────────────────────┐
            │    inventory.order.queue  │
            └───────────┬───────────────┘
                        │
            inventory-service OrderEventConsumer
                        │ payload.status == "CANCELLED"
                        ▼
            InventoryService.restockInventory()  ✅ 返庫
```

### Saga 補償取消流程（不返庫）

```
                        （接續正常下單流程的庫存不足分支）

order-service InventoryEventConsumer
    │ cancelOrderBySaga(orderId)
    ▼
訂單狀態 → SAGA_CANCELLED

OutboxRelayService
    └── 寫 outbox_events（ORDER_SAGA_CANCELLED）
                │
    OutboxPublisher
                │ order.events exchange
                │ routing key: order.ORDER_SAGA_CANCELLED
                ▼
    inventory.order.queue 沒有 ORDER_SAGA_CANCELLED 的 binding
    → 訊息不進入 inventory queue
    → 庫存不返還（本來就沒扣過）✅
```

---

## Event Payload 定義

### ORDER_CREATED / ORDER_CANCELLED / ORDER_SAGA_CANCELLED

payload 為 `OrderResponse` JSON（由 outbox_events.payload 欄位存放）：

```json
{
  "id": "uuid",
  "userId": "U001",
  "productName": "iPhone 15",
  "quantity": 2,
  "status": "CREATED | CANCELLED | SAGA_CANCELLED",
  "createdAt": "2026-03-11T10:00:00"
}
```

> **注意**：order-service OutboxPublisher 透過 Jackson2JsonMessageConverter 發送 String payload，
> 導致 body 為 double-encoded JSON（body starts with `"`）。
> inventory-service OrderEventConsumer 已處理此情況。

### inventory.INSUFFICIENT

payload 為 `InsufficientStockEvent` JSON：

```json
{
  "orderId": "uuid",
  "productName": "MacBook Pro",
  "requestedQty": 10,
  "availableQty": 3
}
```

> `__TypeId__` header 已在 MessagePostProcessor 中移除，避免 order-service 嘗試 load inventory 類別。

---

## 冪等保護

| 服務 | 機制 | Key |
|---|---|---|
| inventory-service OrderEventConsumer | `processed_events` table（H2） | `messageId` |
| order-service InventoryEventConsumer | `processed_events` table（PostgreSQL） | `messageId` |
| order-service HTTP 層 | Redis | `idem:{idempotency-key}` |
| agent-service Tool Call 層 | Redis | `step:{runId}:{toolName}:{params}` |

---

## 關鍵設計決策

### 為什麼 SAGA_CANCELLED 和 CANCELLED 要分開？

- `ORDER_CANCELLED`（使用者取消）：建立訂單時庫存**已扣**，取消後必須**返庫**
- `ORDER_SAGA_CANCELLED`（Saga 補償）：因庫存不足而取消，庫存**從未扣過**，不需返庫

透過不同 routing key，inventory-service 在 binding 層就過濾掉 `ORDER_SAGA_CANCELLED`，不需在 consumer 內部額外判斷。

### 為什麼 inventory.order.queue 用同一個 queue 接收兩種 routing key？

消費邏輯簡單（看 status 欄位路由），不需要獨立 consumer class。同一個 queue 多個 binding 是 RabbitMQ Topic Exchange 的標準用法。
