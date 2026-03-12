## Why

目前所有服務完全缺乏可觀測性：log 沒有 traceId、跨服務請求無法追蹤、Agent tool call 執行時間不可見。在進入 Phase 3 壓測之前，必須先建立 tracing 基礎，才能診斷延遲瓶頸與跨服務鏈路問題。

## What Changes

- 為 gateway-service、order-service、agent-service、inventory-service 加入 Micrometer Tracing (Brave) + Zipkin reporter 依賴
- docker-compose 新增 Zipkin 3 container（port 9411）
- 各服務 application-dev.yml 加入 tracing 設定（100% sampling）與 log pattern（含 traceId/spanId）
- agent-service 新增手動 Span：`AgentRunExecutor.execute()` 建立 root span `agent.run`，`OrderAgentTools` 每個 @Tool 方法建立 child span `agent.tool.{toolName}`
- agent-service 補充 tracing 相關單元測試

## Capabilities

### New Capabilities

- `distributed-tracing`: 跨服務分散式追蹤，含 HTTP 自動 instrumentation、log MDC 注入（traceId/spanId）、Zipkin 匯出
- `agent-span-tracking`: Agent Run 與 Tool Call 的手動 Span 追蹤，可在 Zipkin 看到完整 agent.run → agent.tool.* 階層

### Modified Capabilities

## Impact

- **依賴異動**：gateway-service、order-service、agent-service、inventory-service 各新增 3 個 dependency（actuator、tracing-bridge-brave、zipkin-reporter-brave）；agent-service 額外加 context-propagation
- **設定異動**：4 個服務的 application-dev.yml
- **程式碼異動**：agent-service 的 `AgentRunExecutor`、`OrderAgentTools`（注入 Tracer，加 span 建立/關閉）
- **基礎設施**：docker-compose.yml 新增 Zipkin container
- **無 breaking change**，不影響現有 API 行為
