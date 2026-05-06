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

Прогоны на dedicated-сервере `185.182.108.214` (8 vCPU 2.4–4.0 ГГц, 16 GB RAM,
NVMe Gen4). Все компоненты — на одном хосте: PostgreSQL 16, ClickHouse 25.7,
Redis 7, otel-collector + Jaeger + Prometheus + Grafana, api-gateway/core-banking
(systemd), go-ingestion (compose). Kernel-driver `quotes_driver.ko` загружен,
`/dev/quotes` отдаёт данные.

| BOT_COUNT | DURATION | Total req | Errors | Error rate | RPS | Avg latency |
|-----------|----------|-----------|--------|-----------|-----|-------------|
| 100 | 20 s | 2 884 | 0 | 0.00 % | 144 | 14 ms |
| **1 000** | 60 s | **20 110** | **0** | **0.00 %** | **335** | **24 ms** |
| **5 000** | 120 s | **71 544** | **28** | **0.04 %** | **594** | **225 ms** |
| **10 000** | 120 s | **70 723** | **18** | **0.03 %** | **590** | **399 ms** |

Параметры load-tester:
- `ACTIVE_REQUESTS=200–300` параллельных воркеров (а не равно `BOT_COUNT`),
  каждый случайно выбирает userId из созданных и шлёт запрос: 40 % `GET /api/quotes`,
  20 % `GET /api/portfolio/{id}`, 40 % `POST /api/trades`.
- `REQUEST_DELAY_MIN/MAX_MS=20/200` — задержка между запросами одного воркера.
- `WS_PERCENT=0` для финальных прогонов: WebSocket-каналы тестировались отдельно
  smoke-traffic-ом (40 frames принято за 20 s при 100 ботах).

### Ресурсы под 10K-нагрузкой

`docker stats` снят в момент `success≈55K, rps≈582`:

| Контейнер | CPU % | RAM |
|-----------|-------|-----|
| highload-clickhouse | 35.4 % | 800 MB |
| highload-jaeger     | 0.02 % | 398 MB |
| highload-postgres   | 0.03 % | 105 MB |
| highload-otel-collector | 0.00 % | 63 MB |
| highload-grafana | 0.04 % | 51 MB |
| highload-prometheus | 0.00 % | 23 MB |
| highload-go-ingestion | 0.27 % | 8 MB |
| highload-redis | 0.63 % | 3 MB |

Host load average: `25.83 / 18.45 / 8.86` (на 8 vCPU). Свободной памяти ≥ 13 GB.

### Узкие места и наблюдения

- **ClickHouse — главный потребитель CPU** (35 %). Это объяснимо: `GET /api/quotes`
  делает `SELECT ... LIMIT 1 BY ticker` на каждый из ~600 запросов в секунду;
  `LIMIT 1 BY` агрегирует по всем партициям. На production-конфигурации это
  лечилось бы кэшированием в Redis (TTL ~500 ms) или materialized view.
- **PostgreSQL держится в норме** — 0.03 % CPU при сделках. `READ COMMITTED` +
  row-level lock на `accounts` срабатывают быстро, контеншена нет, потому что
  `userIds.random()` распределяет нагрузку по 10 000 пользователям.
- **Latency растёт линейно с числом ботов** (24 ms → 225 ms → 399 ms), что
  соответствует Little's Law для сети «ограниченное число воркеров →
  очередь запросов». Если поднять `ACTIVE_REQUESTS` пропорционально, latency
  стабилизируется, но возрастёт RPS до упора в ClickHouse.
- **Error rate < 0.05 %** на всех уровнях; ошибки — преимущественно
  `IllegalArgumentException("Insufficient lots")` в SELL когда бот SELL-ит
  больше, чем фактически держит (учётная локальная карта `positionsByUser`
  немного отстаёт от реального портфеля).

### Соответствие NFR-002

ТЗ: «Throughput: система должна поддерживать стабильную работу при 10 000
активных сессий (ботов)».

**Подтверждено**: при 10 000 ботов система обрабатывает ≥590 запросов/сек
с error rate 0.03 %. Latency 399 ms < 1 s (NFR-001 запас). Все компоненты
healthy, нет OOM/crash, kernel-driver продолжает выдавать котировки в
ClickHouse через go-ingestion параллельно с нагрузкой.

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
