#!/bin/bash
# Download OpenTelemetry Java Agent and deploy OTel stack
set -euo pipefail

echo "[otel] Downloading OpenTelemetry Java Agent..."
curl -sL https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar \
  -o /root/highload-invest/opentelemetry-javaagent.jar
echo "[otel] Agent downloaded"

echo "[otel] Starting OTel stack (Collector + Jaeger + Prometheus + Grafana)..."
cd /root/highload-invest/otel
docker compose -f docker-compose.otel.yml up -d
echo "[otel] Stack started"

echo "[otel] Updating systemd services with OTel agent..."

# Update api-gateway service
cat > /etc/systemd/system/api-gateway.service << 'EOF'
[Unit]
Description=HighLoad Invest API Gateway
After=network.target docker.service

[Service]
Type=simple
WorkingDirectory=/root/highload-invest/api-gateway
Environment=CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/default
Environment=REDIS_HOST=localhost
Environment=REDIS_PORT=6379
Environment=LOG_LEVEL=INFO
Environment=OTEL_SERVICE_NAME=api-gateway
Environment=OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
Environment=OTEL_METRICS_EXPORTER=otlp
Environment=OTEL_TRACES_EXPORTER=otlp
ExecStart=/usr/bin/java -javaagent:/root/highload-invest/opentelemetry-javaagent.jar -jar /root/highload-invest/api-gateway/app.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Update core-banking service
cat > /etc/systemd/system/core-banking.service << 'EOF'
[Unit]
Description=HighLoad Invest Core Banking
After=network.target docker.service

[Service]
Type=simple
WorkingDirectory=/root/highload-invest/core-banking
Environment=DATABASE_URL=jdbc:postgresql://localhost:5432/highload_invest
Environment=DATABASE_USER=postgres
Environment=DATABASE_PASSWORD=postgres
Environment=LOG_LEVEL=INFO
Environment=OTEL_SERVICE_NAME=core-banking
Environment=OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
Environment=OTEL_METRICS_EXPORTER=otlp
Environment=OTEL_TRACES_EXPORTER=otlp
ExecStart=/usr/bin/java -javaagent:/root/highload-invest/opentelemetry-javaagent.jar -jar /root/highload-invest/core-banking/app.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Quote generator service
cat > /etc/systemd/system/quote-generator.service << 'EOF'
[Unit]
Description=HighLoad Invest Quote Generator
After=network.target docker.service

[Service]
Type=simple
WorkingDirectory=/root/highload-invest/quote-generator
Environment=CLICKHOUSE_URL=jdbc:clickhouse://localhost:8123/default
Environment=REDIS_HOST=localhost
Environment=REDIS_PORT=6379
Environment=INTERVAL_MS=500
Environment=BATCH_SIZE=10
ExecStart=/usr/bin/java -jar /root/highload-invest/quote-generator/app.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
echo "[otel] Done! Restart services with: systemctl restart api-gateway core-banking"
