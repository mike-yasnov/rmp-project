# Backend — HighLoad Invest

Серверная часть учебной экосистемы биржевой имитации. Принимает котировки от Ingestion-слоя, складывает в ClickHouse, отдаёт мобильным клиентам через REST+WebSocket, исполняет сделки в PostgreSQL c ACID-гарантиями.

## Стек (по ТЗ)

| Слой | Технология |
|------|-----------|
| API + бизнес-логика | Kotlin / Ktor (корутины, kotlinx.serialization, Koin) |
| Сбор котировок | Go (Ingestion service — пока заменяется Kotlin-генератором) |
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
| `quote-generator` | — | Тестовый генератор котировок (пишет в ClickHouse + публикует в Redis) |
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

# 2. Инфраструктура
docker compose up -d

# 3. Запуск Kotlin-сервисов (в разных терминалах)
(cd api-gateway   && ./gradlew run)
(cd core-banking  && ./gradlew run)

# 4. (Опционально) Тестовый поток котировок
(cd quote-generator && ./gradlew run)
```

Health: `curl http://localhost:8080/health` и `curl http://localhost:8081/health`.

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
- `WS   /ws/quotes` — подписка на realtime-обновления (Redis PubSub → broadcast)

### Core Banking (8081)
- `POST /api/users` — создать пользователя
- `GET  /api/users/{id}` — профиль
- `POST /api/trades` — исполнить сделку (валидация баланса, ACID)
- `GET  /api/trades/{userId}` — история (планируется)
- `GET  /api/portfolio/{userId}` — портфель + текущая стоимость

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

## Логирование

Все Kotlin-сервисы используют SLF4J + Logback. Уровень настраивается через `LOG_LEVEL` env (`DEBUG` / `INFO` / `WARN` / `ERROR`). Конфигурация: `*/src/main/resources/logback.xml`.

## Структура каталогов

```
backend/
├── api-gateway/         Kotlin/Ktor (REST + WS)
├── core-banking/        Kotlin/Ktor (бизнес-логика)
├── quote-generator/     Kotlin (тестовый поток котировок)
├── load-tester/         Kotlin (нагрузочный тестер)
├── clickhouse/          init.sql, users.xml
├── nginx/               highload-invest.conf
├── otel/                otel-config.yaml, prometheus.yml, setup-otel.sh
├── bruno/               коллекция Bruno для API-тестов
├── docs/architecture.md
├── docker-compose.yml
└── .env.example
```
