# Тестирование backend

Документ описывает все слои тестирования backend-платформы HighLoad Invest:
unit-, integration-, end-to-end и нагрузочное тестирование.

## 1. Unit-тесты

| Сервис | Файлы | Что покрывает |
|--------|-------|---------------|
| `core-banking` | `src/test/kotlin/.../usecases/ExecuteTradeTest.kt` | Логика расчёта суммы сделки, валидация баланса, BUY/SELL с моками репозиториев |
| `api-gateway` | `src/test/kotlin/.../routes/HealthRoutesTest.kt` | Ktor `testApplication` для `/health` |

Запуск:

```bash
(cd backend/api-gateway   && ./gradlew test)
(cd backend/core-banking  && ./gradlew test)
```

Используются: JUnit 5 (`useJUnitPlatform`), Ktor `testApplication`, ручные моки.

## 2. Integration-тесты Bruno (manual + CLI)

В каталоге [`backend/bruno/highload-invest/`](../bruno/highload-invest/) лежит Bruno collection,
покрывающая основные API-сценарии:

- `api-gateway/health.bru`, `api-gateway/get-all-quotes.bru`, `get-quote-by-ticker.bru`, `get-candles.bru`
- `core-banking/health.bru`, `create-user.bru`, `create-trade.bru`, `sell-trade.bru`, `get-portfolio.bru`

Окружение `environments/local.bru` указывает на локальный compose,
`environments/staging.bru` (если будет добавлено) — на staging.

CLI-прогон:
```bash
cd backend/bruno/highload-invest
bru run --env local
```

## 3. End-to-end сценарии

### Smoke

`backend/scripts/smoke-traffic.sh` — короткий burst запросов
(create-user → deposit → BUY×2 → SELL → portfolio → quotes), используется для
проверки трассы Jaeger и метрик Grafana после деплоя.

### Driver → клиент (после Phase 3)

Сценарий **«генерация котировки → клиент»**:

1. На сервере `quotes_ctl --start --rate 1000`
2. `go-ingestion` читает `/dev/quotes`, складывает в ClickHouse, публикует в Redis
3. `api-gateway` подписан на Redis pubsub → broadcast по `/ws/quotes`
4. Клиент подключается на `ws://2.26.49.82/ws/quotes` и фиксирует первый апдейт

NFR-001 проверяется через лог-таймстемпы драйвера и WS-клиента, а также через Jaeger
(`go-ingestion → api-gateway` span).

## 4. Нагрузочное тестирование (NFR-002)

### Инструмент

`backend/load-tester` — Kotlin-приложение, использующее Ktor HTTP-клиент. Параметризация
через env переменные:

| Переменная | По умолчанию | Назначение |
|------------|---------------|-----------|
| `GATEWAY_URL` | `http://localhost:8080` | adress API Gateway |
| `BANKING_URL` | `http://localhost:8081` | adress Core Banking (создание ботов) |
| `BOT_COUNT` | `100` | сколько одновременных «клиентов» эмулируется |
| `DURATION_SEC` | `60` | время прогона |

Бот выполняет случайные действия: 50% — `POST /api/trades`, 25% — `GET /api/portfolio/{id}`,
25% — `GET /api/quotes`. Метрики (success / errors / latency) выводятся в stdout.

### Лестница прогонов

Прогоны на staging-сервере `2.26.49.82` (Ubuntu, 2× A100 / 24CPU / 64GB). Между
прогонами выполнялся `TRUNCATE` для users / accounts / trades / portfolio.

| BOT_COUNT | DURATION | RPS | Errors % | P50 (ms) | P95 (ms) | P99 (ms) | CPU % | RAM (GB) |
|-----------|----------|-----|----------|----------|----------|----------|-------|----------|
| 1 000 | 120 s | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
| 5 000 | 120 s | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
| 10 000 | 120 s | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ |

> Таблица заполняется после прогонов в Phase 2.

### Узкие места и наблюдения

_TBD после прогона:_

- ClickHouse insert duration P95: …
- PostgreSQL transaction time под `trades` запросом: …
- Redis publish latency: …
- WS broadcast: …

### Скриншоты

Скриншоты Grafana дашборда «HighLoad Invest — Backend Overview» помещаются в
[`backend/docs/img/`](img/) после прогонов:

- `load-1k.png`, `load-5k.png`, `load-10k.png`
- `jaeger-trace-trade.png`

## 5. Observability — что мы можем увидеть

После Phase 1 интеграции OpenTelemetry в системе доступны:

- **Traces** (Jaeger UI на `/jaeger/`):
  - api-gateway HTTP request span → core-banking HTTP request span (W3C Trace Context)
  - `clickhouse.<operation>` span внутри api-gateway
  - `trade.execute` span внутри core-banking с PG-statements (`opentelemetry-jdbc`)
  - `clickhouse.insert_batch`, `redis.publish` в go-ingestion
- **Metrics** (Prometheus / Grafana):
  - HTTP RPS / latency P50, P95, P99 — `http.server.request.duration` от ktor-instrumentation
  - `trades.success`, `trades.failed`, `trade.execute.duration`
  - `clickhouse.query.duration`, `clickhouse.batch_size`, `clickhouse.insert.duration`
  - `redis.pubsub.messages`, `redis.publish.duration`
  - `ws.active_connections`, `ws.messages.broadcast`

Дашборд по умолчанию: «HighLoad Invest — Backend Overview» (provisioned из
[`backend/otel/grafana/dashboards/highload-invest.json`](../otel/grafana/dashboards/highload-invest.json)).
