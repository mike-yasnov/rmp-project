# Описание реализации backend

Этот документ описывает структуру исходного кода каждого backend-сервиса
HighLoad Invest и ключевые технические решения. Архитектурный обзор
см. [`architecture.md`](architecture.md).

## 1. Общие принципы

Все Kotlin-сервисы организованы по **Clean Architecture** с четырьмя слоями:

```
src/main/kotlin/com/highloadinvest/<service>/
├── Application.kt                     # composition root + DI вручную
├── domain/                            # Pure Kotlin, без зависимостей
│   ├── entities/                      # data classes (Quote, User, Trade, ...)
│   └── repositories/                  # interfaces, не классы
├── application/usecases/              # бизнес-сценарии, принимают репозитории-интерфейсы
├── infrastructure/                    # JDBC / HTTP / OTel — конкретные реализации
└── presentation/                      # Ktor-маршруты + DTO
```

Зависимости идут только в направлении внутрь:
`presentation → application → domain ← infrastructure`. Domain ничего не знает
ни о Ktor, ни о PostgreSQL, ни о ClickHouse — это позволяет тестировать use cases
с моками репозиториев без I/O.

### Внедрение зависимостей

Изначально планировался Koin, но в Ktor 3.x обнаружился classloader-конфликт
(см. ADR-005). Поэтому композиция выполняется вручную в `Application.module()`:

```kotlin
fun Application.module() {
    val openTelemetry = Telemetry.init()
    val metrics = BankingMetrics(openTelemetry)
    DatabaseFactory.init(environment.config)

    val userRepo  = PostgresUserRepository()
    val accountRepo = PostgresAccountRepository()
    ...

    val executeTrade = PostgresTradeExecutor(openTelemetry, metrics)
    ...

    routing {
        route("/api") {
            userRoutes(userRepo, accountRepo, metrics)
            tradeRoutes(executeTrade, tradeRepo)
            ...
        }
    }
}
```

### Логирование

SLF4J + Logback. Конфигурация `src/main/resources/logback.xml` читает уровень
из переменной окружения `LOG_LEVEL` (DEBUG / INFO / WARN / ERROR), формат
сообщений — `[timestamp] [level] [logger] - message`. Это позволяет в
production снизить шум до INFO без перекомпиляции.

## 2. api-gateway (Kotlin / Ktor)

### Назначение

Единая точка входа для мобильных клиентов. Реализует:
- REST для котировок и свечей (читает ClickHouse напрямую через HTTP API);
- HTTP-прокси `/api/users`, `/api/trades`, `/api/portfolio`, `/api/accounts`
  на core-banking;
- WebSocket `/ws/quotes` — подписку на realtime-обновления цены.

### Точка входа

[`Application.kt`](../api-gateway/src/main/kotlin/com/highloadinvest/gateway/Application.kt):

1. Поднимает OpenTelemetry SDK через `Telemetry.init()`.
2. Создаёт `ClickHouseQuoteRepository`, `RedisQuoteSubscriber`, `QuoteWebSocketHandler`.
3. Подписывает `RedisQuoteSubscriber` на канал `quotes:updates` и при каждом
   событии вызывает `wsHandler.broadcast(message)` — сообщение уходит всем
   активным WS-сессиям.
4. Регистрирует `KtorServerTelemetry` для авто-инструментации HTTP-входящих.
5. Routing объявляет `/health`, `/api/quotes/...`, `/api/users → /api/portfolio`
   (через `bankingProxyRoutes`), `/ws/quotes`.

### ClickHouse-репозиторий

[`ClickHouseQuoteRepository.kt`](../api-gateway/src/main/kotlin/com/highloadinvest/gateway/infrastructure/clickhouse/ClickHouseQuoteRepository.kt)
делает HTTP-запросы к `clickhouse:8123/?query=...&FORMAT=JSONEachRow`.

Каждый вызов оборачивается в OTel-span `clickhouse.<operation>` через
helper `traced()`:

```kotlin
override suspend fun getLatestQuotes(): List<Quote> = traced("getLatestQuotes", "SELECT ...") {
    ...
}
```

`traced()` автоматически:
- открывает span `SpanKind.CLIENT` с атрибутами `db.system=clickhouse` и
  `db.statement=<первые 500 символов SQL>`;
- ловит исключения и помечает span ошибкой;
- меряет длительность через `metrics.clickhouseQueryDuration` гистограмму.

### Redis pubsub → WebSocket fan-out

[`RedisQuoteSubscriber.kt`](../api-gateway/src/main/kotlin/com/highloadinvest/gateway/infrastructure/redis/RedisQuoteSubscriber.kt)
использует Jedis `JedisPubSub` в отдельном daemon-потоке. Колбэк `onMessage`
просто вызывает переданный лямбда `(String) -> Unit`, переданный из
`Application.module()`.

[`QuoteWebSocketHandler.kt`](../api-gateway/src/main/kotlin/com/highloadinvest/gateway/presentation/websocket/QuoteWebSocket.kt):
- хранит активные сессии в `ConcurrentHashMap.newKeySet()`;
- инкрементирует/декрементирует `ws.active_connections` гейдж при connect/disconnect;
- `broadcast(message)` отправляет сообщение каждой сессии через `runBlocking { send(...) }`;
  сессии, бросившие исключение, удаляются из набора (живой backpressure).

Коэффициент усиления: один Redis-message → N WebSocket-фреймов (N — текущее
число подписчиков). Это и есть ключевой паттерн «fan-out» для NFR-001.

## 3. core-banking (Kotlin / Ktor / PostgreSQL)

### Назначение

Хранит пользователей, счета и сделки. Все финансовые операции в транзакциях
PostgreSQL с уровнем изоляции `READ COMMITTED` и `SELECT ... FOR UPDATE` для
блокировки строк баланса и портфеля во время сделки.

### Подключение к БД

[`DatabaseFactory.kt`](../core-banking/src/main/kotlin/com/highloadinvest/banking/infrastructure/postgres/DatabaseFactory.kt)
создаёт HikariCP `DataSource` с пулом размером `DATABASE_MAX_POOL_SIZE`
(по умолчанию 20). При старте читает SQL-миграцию
`db/migration/V1__init.sql` и выполняет её — таблицы `users`, `accounts`,
`trades`, `portfolio`.

Каждый Repository получает соединение через `DatabaseFactory.connection()`,
которое нужно закрыть через `.use { conn -> ... }` — оно вернётся в пул.

Транзакции — через `conn.autoCommit = false` и явный `commit()` / `rollback()`.
Это нужно для multi-step операций (балансы + позиции).

### PostgresTradeExecutor

[`PostgresTradeExecutor.kt`](../core-banking/src/main/kotlin/com/highloadinvest/banking/infrastructure/postgres/PostgresTradeExecutor.kt)
— самая сложная транзакция в системе. Алгоритм для BUY:

1. `BEGIN` (`autoCommit = false`, `READ COMMITTED`).
2. `SELECT balance FROM accounts WHERE user_id = ? FOR UPDATE` — блокировка
   строки баланса до конца транзакции.
3. Если `balance < totalAmount` → `IllegalArgumentException`, `rollback`.
4. `UPDATE accounts SET balance = balance - totalAmount`.
5. `INSERT INTO portfolio (...) ON CONFLICT (user_id, ticker) DO UPDATE SET
    lots = portfolio.lots + EXCLUDED.lots,
    avg_price = (...weighted average...)` — upsert позиции с пересчётом средней
   цены по weighted average.
6. `INSERT INTO trades (...)` — запись в историю.
7. `COMMIT`.

Для SELL — симметрично: блокируется и баланс, и позиция в портфеле, проверяется
наличие достаточного количества лотов, затем уменьшается позиция (с удалением
строки при `lots = 0`) и увеличивается баланс.

Span `trade.execute` оборачивает всю транзакцию; в атрибуты пишутся `trade.id`,
`user_id`, `ticker`, `action`, `lots`, `total_amount`. На finally —
`metrics.tradeDuration.record(...)` плюс `tradesSuccess` / `tradesFailed`.

### Маршруты Ktor

- [`UserRoutes.kt`](../core-banking/src/main/kotlin/com/highloadinvest/banking/presentation/routes/UserRoutes.kt)
  — `POST /api/users` (создание + initial balance), `GET /api/users/{id}`,
  `POST /api/accounts/{userId}/deposit`. Депозит — учебный (без интеграции с
  внешним эквайером).
- [`TradeRoutes.kt`](../core-banking/src/main/kotlin/com/highloadinvest/banking/presentation/routes/TradeRoutes.kt)
  — `POST /api/trades` (вызывает `executeTrade.execute(...)`),
  `GET /api/trades/{userId}`.
- [`PortfolioRoutes.kt`](../core-banking/src/main/kotlin/com/highloadinvest/banking/presentation/routes/PortfolioRoutes.kt)
  — `GET /api/portfolio/{userId}`. Возвращает список позиций с агрегатной
  стоимостью.

Ошибки конвертируются в JSON через `StatusPages` плагин:
`IllegalArgumentException` → 400, `NoSuchElementException` → 404, `Throwable` → 500.

## 4. go-ingestion (Go)

### Назначение

Читает котировки из `/dev/quotes` (символьное устройство ядра), батчует и
загружает в ClickHouse, публикует в Redis pubsub `quotes:updates`. Если
устройство недоступно — переключается на встроенный fallback-генератор
с теми же тикерами и волатильностями.

### Точка входа

[`main.go`](../go-ingestion/main.go) `main()`:

1. Читает env: `QUOTES_DEVICE`, `CLICKHOUSE_HTTP`, `REDIS_ADDR`,
   `BATCH_SIZE`, `INTERVAL_MS`, `OTEL_EXPORTER_OTLP_ENDPOINT`,
   `QUOTES_REQUIRE_DEVICE`, `QUOTES_DEVICE_WAIT_SEC`.
2. `initTelemetry()` — создаёт TracerProvider и MeterProvider с OTLP HTTP-экспортёром
   на эндпоинт `OTEL_EXPORTER_OTLP_ENDPOINT/v1/traces` и `/v1/metrics`.
3. `waitForDevice(path, timeout)` — пытается открыть `/dev/quotes` до
   `QUOTES_DEVICE_WAIT_SEC` секунд (даёт времени драйверу инициализироваться
   при старте host-а). Если требование жёсткое (`QUOTES_REQUIRE_DEVICE=true`)
   — `log.Fatalf` при отсутствии.
4. Запускает `readDevice()` (snapshot-based чтение) или `generateFallback()`.
5. Главный цикл: получает котировки из канала, аккумулирует в буфер,
   при достижении `BATCH_SIZE` — вызывает `flush()` (insert в ClickHouse),
   также по таймеру 1 раз в секунду.

### Чтение драйвера

`readDevice(path, interval, out, allowFallback)` использует snapshot-based
подход:

```go
seen := make(map[string]time.Time)  // дедупликация по ticker+ts
for {
    lines := readDeviceSnapshot(path)
    for _, line := range lines {
        if q, ok := parseDriverLine(line); ok {
            if t, exists := seen[q.Ticker+q.Timestamp]; !exists || time.Since(t) > 1*Second {
                seen[q.Ticker+q.Timestamp] = time.Now()
                out <- q
            }
        }
    }
    time.Sleep(interval)
}
```

`readDeviceSnapshot()` открывает устройство, читает все доступные строки,
закрывает — это совместимо с тем, как драйвер отдаёт сглаженный «snapshot»
ring-buffer-а через `read()`.

### Запись в ClickHouse

`insertClickHouse(baseURL, rows)` собирает TSV-payload и отправляет POST на
`?query=INSERT INTO quotes ... FORMAT TabSeparated`. Тело — ASCII-байты с
`\t`-разделителем, что заметно эффективнее JSON для bulk-вставок.

OTel-span `clickhouse.insert_batch` с атрибутом `batch.size`,
метрика `clickhouse.batch_size` (гистограмма) и `clickhouse.insert.duration`.

### Публикация в Redis

`publishQuote(ctx, rdb, q)` сериализует Quote в JSON и публикует в
`quotes:updates`. Span `redis.publish` с атрибутами `messaging.system=redis`,
`messaging.destination=quotes:updates`, `ticker`.

## 5. quote-generator и load-tester (учебные)

[`quote-generator/`](../quote-generator/) — Kotlin-аналог Go ingestion для
случаев, когда драйвер недоступен и хочется управлять параметрами генерации
из той же среды (Kotlin team). Подключается через `compose --profile legacy-generator`.

[`load-tester/`](../load-tester/) — Kotlin-приложение на Ktor HTTP-клиенте,
имитирующее N клиентов:
- `CREATE_PARALLELISM` — сколько ботов создаются параллельно;
- `ACTIVE_REQUESTS` — лимит одновременных in-flight запросов на бот;
- `WS_PERCENT` — процент ботов, открывающих WebSocket-подписку;
- `BOT_COUNT`, `DURATION_SEC` — общая нагрузка.

Подробнее — в разделе нагрузочного тестирования [`testing.md`](testing.md).

## 6. Observability в коде

Каждый Kotlin-сервис имеет пакет `infrastructure/observability/`:
- `Telemetry.kt` — инициализация SDK (OTLP gRPC экспортёр на 4317);
- `Metrics.kt` — `Meter.counterBuilder`, `histogramBuilder` для domain-метрик
  (`trades.success`, `clickhouse.query.duration`, `ws.active_connections`, …).

Go-сервис использует OTLP HTTP (4318) — встроенная функция `initTelemetry`
в `main.go`.

Все экспортируемые метрики и спаны попадают в `otel-collector`, который
маршрутизирует traces в Jaeger и metrics в Prometheus, см.
[`backend/otel/otel-config.yaml`](../otel/otel-config.yaml).
