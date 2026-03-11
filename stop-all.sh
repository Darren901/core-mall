#!/bin/bash

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "▶ 停止 Java 服務..."
pkill -f "spring-boot:run" 2>/dev/null && echo "  Java 服務已停止" || echo "  沒有執行中的 Java 服務"

echo "▶ 停止基礎設施..."
docker compose -f "$PROJECT_DIR/infra/docker-compose.yml" down
echo "✅ 全部停止"
