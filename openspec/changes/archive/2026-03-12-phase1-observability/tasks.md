## 1. 基礎設施

- [x] 1.1 docker-compose 新增 Zipkin 3 container（port 9411，volume zipkin_data）

## 2. 依賴新增（4 個服務）

- [x] 2.1 gateway-service pom.xml 新增 actuator、micrometer-tracing-bridge-brave、zipkin-reporter-brave
- [x] 2.2 order-service pom.xml 新增 actuator、micrometer-tracing-bridge-brave、zipkin-reporter-brave
- [x] 2.3 inventory-service pom.xml 新增 actuator、micrometer-tracing-bridge-brave、zipkin-reporter-brave
- [x] 2.4 agent-service pom.xml 新增 actuator、micrometer-tracing-bridge-brave、zipkin-reporter-brave、context-propagation

## 3. 設定（application-dev.yml）

- [x] 3.1 gateway-service application-dev.yml 加 tracing sampling 100%、zipkin base-url、log pattern（traceId/spanId）
- [x] 3.2 order-service application-dev.yml 加 tracing sampling 100%、zipkin base-url、log pattern（traceId/spanId）
- [x] 3.3 inventory-service application-dev.yml 加 tracing sampling 100%、zipkin base-url、log pattern（traceId/spanId）
- [x] 3.4 agent-service application-dev.yml 加 tracing sampling 100%、zipkin base-url、log pattern（traceId/spanId）、reactor context-propagation auto

## 4. agent-service Agent Span 實作

- [x] 4.1 AgentRunExecutor 注入 Tracer，execute() 建立 root span "agent.run"（tag: runId、userId），finally 關閉
- [x] 4.2 OrderAgentTools 注入 Tracer，createOrder() 建立 child span "agent.tool.createOrder"（tag: runId），finally 關閉
- [x] 4.3 OrderAgentTools updateOrder() 建立 child span "agent.tool.updateOrder"（tag: runId），finally 關閉
- [x] 4.4 OrderAgentTools cancelOrder() 建立 child span "agent.tool.cancelOrder"（tag: runId），finally 關閉
- [x] 4.5 OrderAgentTools getOrderStatus() 建立 child span "agent.tool.getOrderStatus"（tag: runId），finally 關閉

## 5. 測試

- [x] 5.1 AgentRunExecutorTracingTest：驗證 execute() 建立 agent.run span（使用 micrometer-tracing-test SimpleTracer）
- [x] 5.2 OrderAgentToolsTracingTest：驗證各 @Tool 方法建立對應 child span（mock OrderServiceClient）
- [x] 5.3 執行 mvn clean verify 確認 4 個服務 JaCoCo 覆蓋率仍 ≥ 80%
