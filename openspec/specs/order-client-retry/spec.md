## ADDED Requirements

### Requirement: 暫時性錯誤自動 retry
OrderServiceClient 呼叫 order-service 時，若收到 5xx response 或遇到 IOException，系統 SHALL 自動重試最多 3 次，使用 exponential backoff（起始 200ms，最大 2s，jitter 0.5）。

#### Scenario: 5xx 第一次失敗，第二次成功
- **WHEN** OrderServiceClient 呼叫 order-service 收到 5xx，第二次重試收到 200
- **THEN** 方法回傳正確結果，不拋出例外

#### Scenario: 5xx 連續 3 次 retry 全部失敗
- **WHEN** OrderServiceClient 呼叫 order-service 連續 4 次（首次 + 3 retry）均收到 5xx
- **THEN** 拋出 ServiceTransientException，訊息來自最後一次 5xx 的 error body

#### Scenario: IOException 觸發 retry
- **WHEN** OrderServiceClient 呼叫 order-service 時 WebClient 拋出 IOException
- **THEN** 系統自動重試，行為同 5xx retry

### Requirement: 4xx 不觸發 retry
OrderServiceClient 收到 4xx response 時，系統 SHALL 立即拋出 ServiceBusinessException，不做任何 retry。

#### Scenario: 4xx 直接失敗
- **WHEN** OrderServiceClient 呼叫 order-service 收到 4xx
- **THEN** 立即拋出 ServiceBusinessException，不重試，訊息來自 error body

### Requirement: 例外類型語意
- `ServiceTransientException` SHALL 表示系統暫時不可用（5xx / IOException），可重試
- `ServiceBusinessException` SHALL 表示業務規則拒絕（4xx），不可重試

#### Scenario: ServiceTransientException 來自 5xx
- **WHEN** toFriendlyError() 收到 5xx status code 的 response
- **THEN** 回傳 ServiceTransientException，訊息為解析後的 error.message 或 fallback

#### Scenario: ServiceBusinessException 來自 4xx
- **WHEN** toFriendlyError() 收到 4xx status code 的 response
- **THEN** 回傳 ServiceBusinessException，訊息為解析後的 error.message 或 fallback
