#!/usr/bin/env bash
# Generate a small burst of API traffic to verify OTEL traces/metrics flow.
# Default targets local docker-compose stack.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
COUNT="${COUNT:-5}"

echo "[smoke] target=$BASE count=$COUNT"

for i in $(seq 1 "$COUNT"); do
    USER_JSON=$(curl -fsS -X POST "$BASE/api/users" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"smoke_$RANDOM\",\"email\":\"smoke_$RANDOM@x.test\",\"initialBalance\":1000000}")
    USER_ID=$(printf '%s' "$USER_JSON" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
    echo "[smoke] created user $USER_ID"

    curl -fsS -X POST "$BASE/api/accounts/$USER_ID/deposit" \
        -H 'Content-Type: application/json' \
        -d '{"amount":50000}' >/dev/null

    for action in BUY BUY SELL BUY; do
        curl -fsS -X POST "$BASE/api/trades" \
            -H 'Content-Type: application/json' \
            -d "{\"userId\":\"$USER_ID\",\"ticker\":\"SBER\",\"action\":\"$action\",\"lots\":1,\"pricePerLot\":250.0}" >/dev/null || true
    done

    curl -fsS "$BASE/api/portfolio/$USER_ID" >/dev/null
    curl -fsS "$BASE/api/quotes" >/dev/null
done

echo "[smoke] done — check Jaeger for traces and Grafana for metrics"
