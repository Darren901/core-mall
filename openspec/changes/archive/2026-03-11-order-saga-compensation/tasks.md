## 1. RabbitMQ 設定擴充

- [x] 1.1 在 `order-service/config/RabbitMQConfig.java` 新增 `inventory.events` TopicExchange bean、`inventory.events.queue` Queue bean（durable）、Binding（routing key `inventory.#`）

## 2. OrderCommandService 新增 cancelOrderBySaga（TDD）

- [x] 2.1 撰寫 `OrderCommandServiceTest` 補充測試：Redis 命中 → 改 CANCELLED 寫回 Redis；Redis miss + DB 命中 → 直接更新 DB；Redis miss + DB 也 miss → log warn 不拋例外
- [x] 2.2 實作 `OrderCommandService.cancelOrderBySaga(orderId)`：Redis 優先 → miss 時 fallback DB 直接 save CANCELLED → 兩者都 miss 則 log warn

## 3. InventoryEventConsumer（TDD）

- [x] 3.1 撰寫 `InventoryEventConsumerTest`（Mockito 單元測試）：正常消費 INSUFFICIENT 事件 → 呼叫 cancelOrderBySaga；重複 messageId → 冪等跳過
- [x] 3.2 實作 `InventoryEventConsumer`（@RabbitListener `inventory.events.queue`，冪等保護 → 解析 payload → 呼叫 cancelOrderBySaga → 存 ProcessedEvent）

## 4. 驗證

- [x] 4.1 執行 `mvn clean compile -pl order-service` 確認編譯通過
- [x] 4.2 執行 `mvn test -pl order-service` 確認所有測試通過
- [x] 4.3 執行 `mvn clean verify -pl order-service` 確認 JaCoCo 覆蓋率 Line ≥ 80%、Branch ≥ 80%
