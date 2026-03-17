# k6 壓測腳本

## 前置條件

```bash
brew install k6
docker compose -f infra/docker-compose.yml up -d
```

## 腳本說明


| 腳本                    | 驗證目標                       | 打哪個服務                                 |
| ----------------------- | ------------------------------ | ------------------------------------------ |
| `order-concurrent.js`   | 冪等性 + 分散式鎖              | order-service :8082                        |
| `inventory-oversell.js` | 庫存超賣防護（100 VU 搶 5 個） | order-service :8082                        |
| `agent-sse.js`          | Agent SSE 全流程               | agent-service :8083（需 loadtest profile） |

## 執行方式

### 場景一：冪等性 + 分散式鎖

```bash
k6 run k6/order-concurrent.js
```

### 場景二：庫存超賣防護

```bash
# 先確認 inventory-service 中 '超賣測試商品' 庫存 = 5
k6 run k6/inventory-oversell.js
```

### 場景三：Agent SSE（需 loadtest profile）

```bash
# 啟動 agent-service
cd agent-service
mvn spring-boot:run -Dspring-boot.run.profiles=loadtest

# 另開視窗跑壓測
k6 run k6/agent-sse.js
```

## 自訂 URL

```bash
k6 run -e ORDER_URL=http://localhost:8082 k6/order-concurrent.js
k6 run -e AGENT_URL=http://localhost:8083 k6/agent-sse.js
```
