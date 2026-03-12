## Context

core-mall 目前 6 個微服務完全沒有分散式追蹤能力：log 缺乏 traceId、跨服務呼叫（Gateway → agent-service → order-service → inventory-service）無法串聯、Agent LLM tool call 的執行時間與錯誤無從追蹤。Phase 3 壓測需要可觀測性作為診斷基礎。

本次範圍限定 4 個核心服務：gateway-service、order-service、agent-service、inventory-service。discovery-service 與 user-service 排除（無業務 trace 價值）。

## Goals / Non-Goals

**Goals:**
- 所有 HTTP 請求自動帶上 traceId/spanId，log 可見
- 跨服務 trace 在 Zipkin UI 可視化
- agent-service 的 Agent Run 與 Tool Call 在 Zipkin 呈現獨立 span 階層

**Non-Goals:**
- Metrics（Prometheus / Grafana）不在本次範圍
- Log aggregation（ELK / Loki）不在本次範圍
- Production sampling 策略（Phase 3 再調整）

## Decisions

### D1：選用 Brave 而非 OpenTelemetry

Spring Boot 3.3.x 的 `micrometer-tracing` 支援 Brave（Zipkin）與 OTel 兩種 bridge。

選擇 **Brave**（`micrometer-tracing-bridge-brave`）理由：
- Zipkin 是本次目標後端，Brave 是 Zipkin 原生 client，整合最直接
- OTel 適合需要多後端（Jaeger/Tempo/Zipkin）的場景，本專案目前只需 Zipkin
- Brave 的 `Tracer` API 較簡單，手動 span 程式碼更少

### D2：Agent Span 用手動 Tracer API 而非 @NewSpan

`@NewSpan` 依賴 Spring AOP proxy。`AgentRunExecutor.execute()` 透過 `@Async` 在獨立執行緒執行；`OrderAgentTools` 的 `@Tool` 方法由 Spring AI 直接反射呼叫，均無法保證走 AOP proxy。

選擇**手動 `Tracer` API**（`tracer.nextSpan().name(...).tag(...).start()`）：
- 明確控制 span 生命週期與 scope
- 可附加業務屬性（`runId`、`toolName`）
- 不依賴 proxy，適合 `@Async` 跨執行緒場景

### D3：Sampling 100%（Dev）

本專案為練習用途，100% sampling 方便 debug，不考慮生產效能。Phase 3 壓測前可視需求降低（`management.tracing.sampling.probability: 0.1`）。

### D4：agent-service 啟用 reactor context propagation

agent-service 使用 WebFlux + `@Async`，若不開啟 `spring.reactor.context-propagation: auto`，traceId 在 reactive pipeline 與非同步邊界處會丟失。

## Risks / Trade-offs

- **[@Async 執行緒切換]** → Brave 的 `CurrentTraceContext` 預設支援執行緒本地（ThreadLocal）傳播，`@Async` 切換執行緒後 span context 會丟失，需在 `execute()` 中手動呼叫 `tracer.withSpan(span)` 建立新 scope
- **[Zipkin single point of failure]** → Dev 環境可接受，不需 HA；若 Zipkin 掛掉，服務僅丟失 trace 資料，不影響業務功能（reporter 預設非同步、fire-and-forget）
- **[agent-service WebFlux + Brave 相容性]** → Spring Boot 3.3.x 已針對 WebFlux 提供 `context-propagation` auto-config，風險低

## Migration Plan

1. 更新 docker-compose，啟動 Zipkin container
2. 各服務 pom.xml 加依賴
3. 各服務 application-dev.yml 加設定
4. agent-service 注入 Tracer、修改 AgentRunExecutor 與 OrderAgentTools
5. 加測試、執行 `mvn clean verify` 驗證覆蓋率通過
6. 回滾：移除依賴與設定即可，無 DB schema 異動

## Open Questions

- 無
