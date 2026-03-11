## MODIFIED Requirements

### Requirement: 內部訂單操作
**變更**：建立訂單前新增庫存預檢步驟。

#### Scenario: 建立訂單，庫存充足
- **WHEN** agent-service 呼叫 `POST /internal/v1/orders`，inventory-service 回傳庫存 >= 請求數量
- **THEN** 系統 SHALL 建立訂單並回傳 HTTP 201

#### Scenario: 建立訂單，庫存不足
- **WHEN** agent-service 呼叫 `POST /internal/v1/orders`，inventory-service 回傳庫存 < 請求數量
- **THEN** 系統 SHALL 回傳 HTTP 422，錯誤碼 `INSUFFICIENT_STOCK`，不建立訂單

#### Scenario: 建立訂單，商品不存在
- **WHEN** agent-service 呼叫 `POST /internal/v1/orders`，inventory-service 回傳 404
- **THEN** 系統 SHALL 回傳 HTTP 422，錯誤碼 `INSUFFICIENT_STOCK`，不建立訂單

#### Scenario: 建立訂單，inventory-service 無回應
- **WHEN** agent-service 呼叫 `POST /internal/v1/orders`，inventory-service 無法連線
- **THEN** 系統 SHALL 回傳 HTTP 503，錯誤碼 `INVENTORY_SERVICE_UNAVAILABLE`
