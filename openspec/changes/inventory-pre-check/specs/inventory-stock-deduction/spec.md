## ADDED Requirements

### Requirement: 庫存查詢不影響扣減邏輯
庫存查詢端點為唯讀操作，不鎖定庫存。非同步 MQ 扣減流程與 Saga 補償保持不變，作為 TOCTOU race condition 的最後防線。

#### Scenario: 查詢後下單，庫存仍由 MQ 扣減
- **WHEN** 預檢通過後建立訂單，Outbox 事件觸發 inventory-service 扣減
- **THEN** 扣減邏輯與先前相同，不受查詢影響
