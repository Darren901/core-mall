## 1. 模組骨架與設定

- [x] 1.1 根目錄 `pom.xml` 新增 `<module>inventory-service</module>`
- [x] 1.2 建立 `inventory-service/pom.xml`（繼承根 parent，加入 Spring Boot Web、Data JPA、AMQP、H2、Testcontainers RabbitMQ 依賴，設定 JaCoCo）
- [x] 1.3 建立 `InventoryServiceApplication.java`
- [x] 1.4 建立 `application.yml`（port 8084、H2 datasource、jpa ddl-auto: create-drop、rabbitmq localhost:5672、eureka）

## 2. JPA Entities 與 Repositories（TDD）

- [x] 2.1 撰寫 `InventoryRepository` 單元測試（@DataJpaTest）：findById by productName
- [x] 2.2 實作 `Inventory` entity（productName PK、quantity）與 `InventoryRepository`
- [x] 2.3 撰寫 `ProcessedEventRepository` 單元測試（@DataJpaTest）：existsById
- [x] 2.4 實作 `ProcessedEvent` entity（messageId PK）與 `ProcessedEventRepository`

## 3. RabbitMQ 設定

- [x] 3.1 實作 `RabbitMQConfig`：consumer exchange/queue/binding（order.events → inventory.order.queue，routing key order.ORDER_CREATED）、publisher exchange/queue/binding（inventory.events → inventory.events.queue，routing key inventory.#）、Jackson2JsonMessageConverter

## 4. InventoryService（TDD）

- [x] 4.1 撰寫 `InventoryServiceTest`（Mockito 單元測試）：庫存足夠扣減成功；庫存不足發補償事件（驗證 rabbitTemplate 被呼叫，payload 正確）；商品不存在 log warn 不拋例外
- [x] 4.2 實作 `InventoryService.deductStock(orderId, productName, requestedQty)`

## 5. OrderEventConsumer（TDD）

- [x] 5.1 撰寫 `OrderEventConsumerTest`（Testcontainers RabbitMQ 整合測試）：正常消費 ORDER_CREATED → 庫存扣減；重複 messageId → 冪等跳過，庫存只扣一次；庫存不足 → inventory.events.queue 收到補償訊息
- [x] 5.2 實作 `OrderEventConsumer`（@RabbitListener，冪等保護 → 解析 payload → 呼叫 InventoryService → 存 ProcessedEvent）

## 6. 種子資料

- [x] 6.1 實作 `DataInitializer`（@Component + CommandLineRunner）：寫入 iPhone 15 × 10、MacBook Pro × 5、AirPods × 20

## 7. 驗證

- [x] 7.1 執行 `mvn clean compile -pl inventory-service` 確認編譯通過
- [x] 7.2 執行 `mvn test -pl inventory-service` 確認所有測試通過
- [x] 7.3 執行 `mvn clean verify -pl inventory-service` 確認 JaCoCo 覆蓋率 Line ≥ 80%、Branch ≥ 80%
