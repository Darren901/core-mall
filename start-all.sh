#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

echo "▶ 啟動基礎設施..."
docker compose -f "$PROJECT_DIR/infra/docker-compose.yml" up -d
echo "⏳ 等待基礎設施就緒 (10s)..."
sleep 10

echo "▶ 啟動 discovery-service..."
mvn -f "$PROJECT_DIR/pom.xml" spring-boot:run -pl discovery-service \
  > "$LOG_DIR/discovery.log" 2>&1 &
echo "⏳ 等待 Eureka 啟動 (20s)..."
sleep 10

echo "▶ 啟動 gateway-service..."
mvn -f "$PROJECT_DIR/pom.xml" spring-boot:run -pl gateway-service \
  > "$LOG_DIR/gateway.log" 2>&1 &

echo "▶ 啟動 user-service..."
mvn -f "$PROJECT_DIR/pom.xml" spring-boot:run -pl user-service \
  > "$LOG_DIR/user.log" 2>&1 &

echo "▶ 啟動 order-service..."
mvn -f "$PROJECT_DIR/pom.xml" spring-boot:run -pl order-service \
  > "$LOG_DIR/order.log" 2>&1 &

echo "▶ 啟動 inventory-service..."
mvn -f "$PROJECT_DIR/pom.xml" spring-boot:run -pl inventory-service \
  > "$LOG_DIR/inventory.log" 2>&1 &

echo "▶ 啟動 agent-service..."
mvn -f "$PROJECT_DIR/pom.xml" spring-boot:run -pl agent-service \
  > "$LOG_DIR/agent.log" 2>&1 &

echo ""
echo "✅ 所有服務啟動中，log 位於 logs/ 目錄"
echo ""
echo "服務位址："
echo "  Gateway    http://localhost:8080"
echo "  Eureka     http://localhost:8761"
echo "  RabbitMQ   http://localhost:15672  (guest/guest)"
echo "  RedisInsight http://localhost:8001"
echo ""
echo "停止所有服務請執行：./stop-all.sh"
