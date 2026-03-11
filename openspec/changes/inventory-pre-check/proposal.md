## Why

目前下單流程採用樂觀 Saga：order-service 無條件接受訂單（201 OK），等庫存扣減失敗後才補償取消，導致用戶先看到「建立成功」隨後收到「訂單取消」的負面體驗。透過在建立訂單前同步查詢庫存可用量，大多數庫存不足的情況能即時回報 400，Saga 補償僅作為極端 race condition 的最後防線。

## What Changes

- **inventory-service** 新增 `GET /api/inventory/{productName}` 查詢端點，回傳當前庫存可用數量
- **order-service** 建立訂單前呼叫 inventory-service，庫存不足時直接回傳 400，不建立訂單
- Saga 補償流程**保留**，處理兩人同時搶最後一個庫存的 TOCTOU race condition

## Capabilities

### New Capabilities
- `inventory-query`: inventory-service 對外暴露庫存查詢 REST API

### Modified Capabilities
- `order-lifecycle`: 建立訂單新增庫存預檢步驟，庫存不足回傳 400 而非 201 + 後補取消
- `inventory-stock-deduction`: 新增庫存查詢 requirement（非同步扣減邏輯不變）

## Impact

- `inventory-service`：新增 Controller、DTO（`InventoryResponse`）
- `order-service`：新增 `InventoryClient`（WebClient），`OrderCommandService.createOrder()` 加入預檢邏輯
- API 行為變更：庫存不足時 `POST /api/orders` 由原本的 201 改為 400
- 測試：兩個服務各需新增測試覆蓋預檢路徑
