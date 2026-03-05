# Infra Local Stack

本目錄提供 core-mall 本地開發所需基礎服務：
- PostgreSQL x 3（對應 `user-service` / `order-service` / `agent-service`）
- Redis
- RabbitMQ（含 management UI）

## 啟動

```bash
docker compose -f infra/docker-compose.yml up -d
```

## 停止

```bash
docker compose -f infra/docker-compose.yml down
```

## 停止並清除資料

```bash
docker compose -f infra/docker-compose.yml down -v
```

## 連線資訊

- `user-service` PostgreSQL
  - host: `localhost`
  - port: `5432`
  - db: `user_db`
  - username/password: `user` / `user`
- `order-service` PostgreSQL
  - host: `localhost`
  - port: `5433`
  - db: `order_db`
  - username/password: `order` / `order`
- `agent-service` PostgreSQL
  - host: `localhost`
  - port: `5434`
  - db: `agent_db`
  - username/password: `agent` / `agent`
- Redis
  - host: `localhost`
  - port: `6379`
- RabbitMQ
  - AMQP: `localhost:5672`
  - 管理介面: `http://localhost:15672`（guest/guest）

## 快速檢查

```bash
docker compose -f infra/docker-compose.yml ps
```
