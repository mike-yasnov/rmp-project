#!/usr/bin/env bash
# Deploy backend with full OTEL stack to ssh backend (2.26.49.82)
# Run from rmp-project/backend/ directory
set -euo pipefail

SERVER="${SERVER:-backend}"
REMOTE_DIR="${REMOTE_DIR:-~/highload-invest}"

echo "[deploy] Syncing config files to $SERVER:$REMOTE_DIR"
ssh "$SERVER" "mkdir -p $REMOTE_DIR/otel/grafana/provisioning/datasources $REMOTE_DIR/otel/grafana/provisioning/dashboards $REMOTE_DIR/otel/grafana/dashboards $REMOTE_DIR/clickhouse"

scp docker-compose.yml "$SERVER:$REMOTE_DIR/docker-compose.yml"
scp .env.example       "$SERVER:$REMOTE_DIR/.env.example"
scp -r otel/.          "$SERVER:$REMOTE_DIR/otel/"
scp -r clickhouse/.    "$SERVER:$REMOTE_DIR/clickhouse/"

echo "[deploy] Updating .env on server (preserve existing if present)"
ssh "$SERVER" "[ -f $REMOTE_DIR/.env ] || cp $REMOTE_DIR/.env.example $REMOTE_DIR/.env"

echo "[deploy] Starting observability stack"
ssh "$SERVER" "cd $REMOTE_DIR && docker compose up -d otel-collector jaeger prometheus grafana"

echo "[deploy] Waiting for OTEL collector to become healthy"
ssh "$SERVER" "until curl -fsS http://localhost:4318/v1/traces -X POST -d '{}' >/dev/null 2>&1 || curl -fsS http://localhost:8889/metrics >/dev/null 2>&1; do sleep 2; done; echo collector OK"

echo "[deploy] Done. Available endpoints:"
echo "  Jaeger UI:    http://2.26.49.82/jaeger/"
echo "  Grafana:      http://2.26.49.82/grafana/  (admin / from .env)"
echo "  Prometheus:   http://2.26.49.82:9090/  (only if exposed)"
