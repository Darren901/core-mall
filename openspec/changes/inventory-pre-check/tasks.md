## 1. inventory-service：庫存查詢 API

- [x] 1.1 新增 `InventoryResponse` record（`productName`, `quantity`）
- [x] 1.2 新增 `InventoryController`：`GET /api/inventory/{productName}`，呼叫 `InventoryService.getStock()`
- [x] 1.3 新增 `InventoryService.getStock(productName)`：查 H2，不存在回傳 `Optional.empty()`
- [x] 1.4 新增 `InventoryControllerTest`（`@WebMvcTest`）：200 回傳庫存、404 商品不存在

## 2. order-service：InventoryClient

- [x] 2.1 新增 `InventoryClient`（RestClient，`lb://inventory-service`）：`checkStock(productName, qty)` — 不足或 404 拋 `InsufficientStockException`，連線失敗拋 `ServiceUnavailableException`
- [x] 2.2 新增 `InsufficientStockException`（422）與 `ServiceUnavailableException`（503）
- [x] 2.3 在 `GlobalExceptionHandler` 新增對應 handler，回傳 `INSUFFICIENT_STOCK` / `INVENTORY_SERVICE_UNAVAILABLE` 錯誤碼
- [x] 2.4 新增 `InventoryClientTest`（MockRestServiceServer）：足夠通過、不足拋例外、404 拋例外、連線失敗拋 503 例外

## 3. order-service：OrderCommandService 預檢整合

- [x] 3.1 在 `OrderCommandService` constructor 注入 `InventoryClient`
- [x] 3.2 `createOrder()` 冪等檢查後、寫 Redis 前，呼叫 `inventoryClient.checkStock()`
- [x] 3.3 更新 `OrderCommandServiceTest`：新增庫存不足回 422、inventory-service 不可用回 503 測試案例；既有測試 mock `InventoryClient` 回傳通過

## 4. 前端：錯誤訊息處理

- [x] 4.1 確認 gateway-service 前端頁面：order 建立失敗時顯示 `error.message`（`INSUFFICIENT_STOCK` → 「庫存不足，無法建立訂單」）
