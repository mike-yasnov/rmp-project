# Backend — HighLoad Invest

Серверная часть учебной экосистемы биржевой имитации. Принимает котировки от Ingestion-слоя, складывает в ClickHouse, отдаёт мобильным клиентам через REST+WebSocket, исполняет сделки в PostgreSQL c ACID-гарантиями.

## Стек (по ТЗ)

| Слой | Технология |
|------|-----------|
| API + бизнес-логика | Kotlin / Ktor (корутины, kotlinx.serialization, Koin) |
| Сбор котировок | Go ingestion: `/dev/quotes` от kernel module или fallback-генератор |
| Драйвер | C, Linux kernel module |
| Котировки и история | ClickHouse |
| Пользователи и финансы | PostgreSQL (raw SQL, HikariCP) |
| Кэш + брокер сообщений | Redis / KeyDB |
| Транспорт | REST, WebSocket |
| Observability | OpenTelemetry → Jaeger + Prometheus + Grafana |
| Контейнеризация | Docker + Docker Compose |

Архитектура (Clean Architecture внутри Kotlin-сервисов, ADR, диаграммы) — [docs/architecture.md](docs/architecture.md).

## Сервисы

| Сервис | Порт | Назначение |
|--------|------|-----------|
| `api-gateway` | 8080 | REST + WebSocket для мобильных клиентов |
| `core-banking` | 8081 | Балансы, сделки, портфели — PostgreSQL ACID |
| `go-ingestion` | — | Go ingestion: читает `/dev/quotes`, пишет в ClickHouse + публикует в Redis; без драйвера включает fallback-генератор |
| `quote-driver` | — | Privileged compose-сервис: собирает и загружает Linux kernel module, создаёт `/dev/quotes` |
| `quote-generator` | — | Старый Kotlin-генератор котировок, доступен как compose profile `legacy-generator` |
| `load-tester` | — | Имитатор N клиентов (для NFR-002 — 10K сессий) |
| ClickHouse | 8123 / 9000 | Источник правды для котировок |
| PostgreSQL | 5432 | Финансовые данные |
| Redis | 6379 | Кэш + PubSub `quotes:updates` |
| Jaeger | 16686 | Distributed tracing UI |
| Prometheus | 9090 | Метрики |
| Grafana | 3000 | Дашборды |

## Quickstart

```bash
# 1. Конфигурация
cp .env.example .env

# 2. Вся серверная часть: БД, Redis, Ktor-сервисы, Go ingestion
docker compose up -d --build
```

Health: `curl http://localhost:8080/health` и `curl http://localhost:8081/health`.

Observability можно добавить тем же стеком через compose override:

```bash
docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml up -d --build
```

UI:
- Jaeger: http://localhost:16686
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (`admin` / `admin`)

Ktor-сервисы отправляют telemetry через OpenTelemetry Java Agent, Go ingestion отправляет OTLP traces/metrics напрямую в collector.

По умолчанию Go ingestion запускает fallback-генератор, чтобы стенд работал без прав на загрузку модуля ядра.

## Запуск с kernel module

Driver-режим работает только на native Linux, потому что контейнер загружает `.ko` в ядро хоста. На хосте должны быть установлены kernel headers для текущего ядра:

```bash
sudo apt install "linux-headers-$(uname -r)"
```

Запуск всего backend вместе с драйвером:

```bash
docker compose -f docker-compose.yml -f docker-compose.driver.yml up -d --build
```

Что делает override:
- `quote-driver` запускается privileged, собирает `module_for_generate_quotes/kernel/quotes_driver.ko`, делает `insmod`, создаёт `/dev/quotes` на хосте и выставляет права на чтение;
- `go-ingestion` в этом override тоже запускается privileged, ждёт `/host-dev/quotes`, читает реальные котировки из драйвера, пишет их в ClickHouse и публикует в Redis.

Проверка:

```bash
docker compose -f docker-compose.yml -f docker-compose.driver.yml ps
head -5 /dev/quotes
cat /proc/quotes_stat
curl 'http://localhost:8123/?query=SELECT%20count()%20FROM%20quotes'
```

Остановка driver-режима:

```bash
docker compose -f docker-compose.yml -f docker-compose.driver.yml down
```

При остановке контейнер, который сам загрузил модуль, пытается выгрузить `quotes_driver`.

## Сборка fat-JAR

```bash
(cd api-gateway   && ./gradlew buildFatJar)
(cd core-banking  && ./gradlew buildFatJar)
# JAR'ы в */build/libs/*-all.jar
```

## API-эндпоинты

### API Gateway (8080)
- `GET  /api/quotes` — все текущие котировки
- `GET  /api/quotes/{ticker}` — по тикеру
- `GET  /api/quotes/{ticker}/candles` — свечи
- `POST /api/users` — прокси к Core Banking, регистрация пользователя
- `GET  /api/users/{id}` — профиль пользователя
- `POST /api/accounts/{userId}/deposit` — учебное пополнение счёта
- `POST /api/trades` — покупка/продажа активов
- `GET  /api/trades/{userId}` — история сделок
- `GET  /api/portfolio/{userId}` — портфель
- `WS   /ws/quotes` — подписка на realtime-обновления (Redis PubSub → broadcast)

### Core Banking (8081)
- `POST /api/users` — создать пользователя
- `GET  /api/users/{id}` — профиль
- `POST /api/accounts/{userId}/deposit` — учебное пополнение счёта
- `POST /api/trades` — исполнить сделку (валидация баланса, ACID)
- `GET  /api/trades/{userId}` — история
- `GET  /api/portfolio/{userId}` — портфель + текущая стоимость

Пример smoke-test:
```bash
USER_JSON=$(curl -fsS -X POST http://localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","email":"demo@example.test","initialBalance":1000000}')
USER_ID=$(printf '%s' "$USER_JSON" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

curl -fsS -X POST "http://localhost:8080/api/accounts/$USER_ID/deposit" \
  -H 'Content-Type: application/json' \
  -d '{"amount":50000}'

curl -fsS -X POST http://localhost:8080/api/trades \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"$USER_ID\",\"ticker\":\"SBER\",\"action\":\"BUY\",\"lots\":2,\"pricePerLot\":250.0}"

curl -fsS "http://localhost:8080/api/portfolio/$USER_ID"
```

API-сценарии для Bruno: [bruno/](bruno/).

## Деплой на staging

Сервер `2.26.49.82` (alias `ssh backend`). Сервисы запущены через systemd (`api-gateway.service`, `core-banking.service`), nginx проксирует `:80` → `:8080` / `:8081`.

Деплой свежей сборки:
```bash
(cd api-gateway   && ./gradlew buildFatJar)
(cd core-banking  && ./gradlew buildFatJar)
scp api-gateway/build/libs/api-gateway-all.jar    backend:~/highload-invest/api-gateway/app.jar
scp core-banking/build/libs/core-banking-all.jar  backend:~/highload-invest/core-banking/app.jar
ssh backend 'systemctl restart api-gateway core-banking'
```

Всё через nginx: **http://2.26.49.82/** (см. [nginx/highload-invest.conf](nginx/highload-invest.conf)).

## Тесты

```bash
(cd api-gateway   && ./gradlew test)
(cd core-banking  && ./gradlew test)
```

Покрытие: unit-тесты use cases, integration-тесты Ktor `testApplication`.

Нагрузочный тест:

```bash
# Короткая проверка REST + WebSocket
docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml -f docker-compose.driver.yml run --rm \
  -e BOT_COUNT=50 \
  -e DURATION_SEC=10 \
  -e CREATE_PARALLELISM=20 \
  -e ACTIVE_REQUESTS=20 \
  -e WS_PERCENT=20 \
  load-tester

# Проверка 10k логических клиентов REST-сценария
docker compose -f docker-compose.yml -f otel/docker-compose.otel.yml -f docker-compose.driver.yml run --rm \
  -e BOT_COUNT=10000 \
  -e DURATION_SEC=15 \
  -e CREATE_PARALLELISM=100 \
  -e ACTIVE_REQUESTS=50 \
  -e WS_PERCENT=0 \
  -e REQUEST_DELAY_MIN_MS=100 \
  -e REQUEST_DELAY_MAX_MS=300 \
  load-tester
```

`BOT_COUNT` задаёт количество логических пользователей, `ACTIVE_REQUESTS` ограничивает число одновременно выполняемых REST-запросов, чтобы локальный стенд проверял backend, а не ломался об лимиты клиентского контейнера.

## Логирование

Все Kotlin-сервисы используют SLF4J + Logback. Уровень настраивается через `LOG_LEVEL` env (`DEBUG` / `INFO` / `WARN` / `ERROR`). Конфигурация: `*/src/main/resources/logback.xml`.

## Структура каталогов

```
backend/
├── api-gateway/         Kotlin/Ktor (REST + WS)
├── core-banking/        Kotlin/Ktor (бизнес-логика)
├── go-ingestion/        Go (сбор котировок из /dev/quotes или fallback)
├── quote-driver/        Docker-загрузчик Linux kernel module
├── quote-generator/     Kotlin (legacy тестовый поток котировок)
├── load-tester/         Kotlin (нагрузочный тестер)
├── clickhouse/          init.sql, users.xml
├── nginx/               highload-invest.conf
├── otel/                otel-config.yaml, prometheus.yml, setup-otel.sh
├── bruno/               коллекция Bruno для API-тестов
├── docs/architecture.md
├── docker-compose.yml
├── docker-compose.driver.yml
└── .env.example
```
