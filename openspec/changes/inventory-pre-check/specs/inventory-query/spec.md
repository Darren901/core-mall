## ADDED Requirements

### Requirement: 庫存查詢 REST API
系統 SHALL 提供 `GET /api/inventory/{productName}` 端點，回傳指定商品的當前可用庫存數量。

#### Scenario: 查詢存在的商品
- **WHEN** 呼叫 `GET /api/inventory/iPhone 15`，該商品庫存為 10
- **THEN** 系統 SHALL 回傳 HTTP 200 及 `{ "productName": "iPhone 15", "quantity": 10 }`

#### Scenario: 查詢不存在的商品
- **WHEN** 呼叫 `GET /api/inventory/不存在商品`
- **THEN** 系統 SHALL 回傳 HTTP 404
