#!/usr/bin/env bash
# Full deploy script for HighLoad Invest backend.
# Builds fat JARs, copies them to staging server, restarts systemd services,
# brings up the docker-compose observability stack on the same host.
set -euo pipefail

SERVER="${SERVER:-backend}"
REMOTE_DIR="${REMOTE_DIR:-~/highload-invest}"

echo "[deploy] === 1. Build Kotlin services ==="
(cd api-gateway   && ./gradlew --no-daemon buildFatJar -q)
(cd core-banking  && ./gradlew --no-daemon buildFatJar -q)
echo "[deploy] JARs:"
ls -lh api-gateway/build/libs/*-all.jar core-banking/build/libs/*-all.jar

echo "[deploy] === 2. Sync config / OTEL stack to $SERVER:$REMOTE_DIR ==="
ssh "$SERVER" "mkdir -p $REMOTE_DIR/{otel,clickhouse,api-gateway,core-banking} \
                       $REMOTE_DIR/otel/grafana/provisioning/datasources \
                       $REMOTE_DIR/otel/grafana/provisioning/dashboards \
                       $REMOTE_DIR/otel/grafana/dashboards"

scp docker-compose.yml "$SERVER:$REMOTE_DIR/docker-compose.yml"
scp .env.example       "$SERVER:$REMOTE_DIR/.env.example"
scp -r otel/.          "$SERVER:$REMOTE_DIR/otel/"
scp -r clickhouse/.    "$SERVER:$REMOTE_DIR/clickhouse/"

ssh "$SERVER" "[ -f $REMOTE_DIR/.env ] || cp $REMOTE_DIR/.env.example $REMOTE_DIR/.env"

echo "[deploy] === 3. Upload JARs ==="
scp api-gateway/build/libs/api-gateway-all.jar    "$SERVER:$REMOTE_DIR/api-gateway/app.jar"
scp core-banking/build/libs/core-banking-all.jar  "$SERVER:$REMOTE_DIR/core-banking/app.jar"

echo "[deploy] === 4. Bring up observability stack ==="
ssh "$SERVER" "cd $REMOTE_DIR && docker compose up -d --pull always otel-collector jaeger prometheus grafana"

echo "[deploy] === 5. Restart systemd-managed Kotlin services ==="
ssh "$SERVER" "systemctl daemon-reload && systemctl restart api-gateway core-banking && systemctl status api-gateway --no-pager -l | head -10"

echo "[deploy] === Done ==="
echo "  Health:    http://2.26.49.82/gateway/health  http://2.26.49.82/banking/health"
echo "  Jaeger:    http://2.26.49.82/jaeger/"
echo "  Grafana:   http://2.26.49.82/grafana/  (admin / from .env)"
