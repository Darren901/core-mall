## ADDED Requirements

### Requirement: HTTP 請求自動 tracing
gateway-service、order-service、agent-service、inventory-service 的所有 HTTP 請求 SHALL 自動建立 span 並傳播 traceId/spanId。

#### Scenario: 跨服務請求帶 traceId
- **WHEN** 外部請求進入 gateway-service
- **THEN** gateway-service log 包含 traceId
- **THEN** 後續下游服務（order-service 等）log 包含相同 traceId

#### Scenario: Zipkin 收到 trace 資料
- **WHEN** 任一服務收到 HTTP 請求
- **THEN** Zipkin UI（localhost:9411）可查詢到對應 trace

### Requirement: Log 包含 traceId 與 spanId
4 個服務的所有 log 輸出 SHALL 包含 traceId 與 spanId 欄位。

#### Scenario: 正常請求 log 格式
- **WHEN** 服務收到 HTTP 請求並輸出 log
- **THEN** log 格式為 `HH:mm:ss.SSS [thread] LEVEL [traceId,spanId] logger - message`

#### Scenario: 無 trace context 時 log 格式
- **WHEN** 服務在無 HTTP context 下輸出 log（如啟動 log）
- **THEN** log 中 traceId 與 spanId 欄位為空字串（不顯示 `null`）

### Requirement: 100% sampling（Dev 環境）
Dev 環境 SHALL 使用 100% trace sampling，確保所有請求都送至 Zipkin。

#### Scenario: Sampling 設定生效
- **WHEN** 服務以 dev profile 啟動
- **THEN** `management.tracing.sampling.probability` 為 1.0
- **THEN** 每次 HTTP 請求都在 Zipkin 可查詢到 trace
