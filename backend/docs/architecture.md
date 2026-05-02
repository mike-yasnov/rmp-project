# Архитектура системы HighLoad Invest Ecosystem — Backend

## 1. Обоснование выбора архитектуры

| Фактор | Значение | Влияние на выбор |
|--------|----------|------------------|
| Команда | до 10 человек | Микросервисы (задано ТЗ) |
| Сложность домена | Высокая (финансы + realtime) | Элементы DDD |
| Языки | Kotlin, Go, C | Полиглот — микросервисы обязательны |
| Базы данных | ClickHouse, PostgreSQL, Redis | Database-per-service |
| Масштабирование | 10K сессий | Горизонтальное для API Gateway |
| Консистентность | ACID (финансы) + Eventual (котировки) | Разделение ответственности по БД |

Техническое задание жёстко задаёт микросервисную архитектуру с 5 сервисами на 3 языках. Это обосновано:
- Разные языки для разных задач (Kotlin для API, Go для high-throughput ingestion, C для kernel).
- Разные требования к масштабированию (API Gateway — горизонтально, Driver — один экземпляр).
- Разные модели консистентности (ACID vs Eventual).

## 2. Общая схема системы

```plantuml
@startuml
!theme plain
skinparam componentStyle rectangle

package "Mobile Clients" {
  [Android Native\n(Kotlin/Compose)] as AndroidApp
  [Cross-Platform\n(React Native)] as CrossApp
}

package "Backend" {
  [API Gateway\nKotlin/Ktor\nPort 8080] as APIGateway
  [Core Banking\nKotlin] as CoreBanking
  [Ingestion Service\nGo] as Ingestion
  [Load Tester\n10K bots] as LoadTester
}

package "Data Stores" {
  database "PostgreSQL\n(ACID)" as PG
  database "ClickHouse\n(Columnar)" as CH
  database "Redis / KeyDB\n(Cache + PubSub)" as Redis
}

package "Hardware" {
  [Linux Driver\nC kernel module\n/dev/quotes] as Driver
}

AndroidApp -down-> APIGateway : REST + WebSocket
CrossApp -down-> APIGateway : REST + WebSocket

APIGateway -down-> CoreBanking : internal call
APIGateway -down-> CH : read quotes\n(TCP native)
APIGateway -down-> Redis : subscribe\n(PubSub)

CoreBanking -down-> PG : read/write\n(ACID, raw SQL)

Ingestion -down-> CH : batch insert\n(TCP native)
Ingestion -down-> Redis : publish\n(new quotes)
Ingestion -left-> Driver : read()\nsyscall

LoadTester -up-> APIGateway : HTTP + WebSocket\n(simulated clients)

@enduml
```

## 3. Коммуникация между сервисами

```plantuml
@startuml
!theme plain

participant "Mobile Client" as Client
participant "API Gateway\n(Ktor)" as API
participant "Core Banking\n(Kotlin)" as Bank
database "PostgreSQL" as PG
database "ClickHouse" as CH
database "Redis" as Redis
participant "Ingestion\n(Go)" as Ingest
participant "Driver\n(C)" as Driver

== Поток котировок (Data Flow) ==

Driver -> Ingest : read() из /dev/quotes
Ingest -> Ingest : буферизация (батчинг)
Ingest -> CH : batch INSERT
Ingest -> Redis : PUBLISH "new_quotes"
Redis -> API : subscribe notification
API -> CH : SELECT latest quotes
API -> Client : WebSocket push

== Торговая операция (Trade Flow) ==

Client -> API : POST /api/trades\n{ticker, lots, action}
API -> Bank : executeTrade()
Bank -> PG : BEGIN TRANSACTION
Bank -> PG : SELECT balance FROM accounts
Bank -> CH : SELECT last_price FROM quotes
Bank -> PG : UPDATE accounts SET balance = ...
Bank -> PG : INSERT INTO trades ...
Bank -> PG : COMMIT
Bank --> API : TradeResult
API --> Client : 200 OK {trade}

== Получение портфеля ==

Client -> API : GET /api/portfolio
API -> Bank : getPortfolio(userId)
Bank -> PG : SELECT * FROM portfolio\nWHERE user_id = ?
Bank --> API : Portfolio
API -> CH : SELECT current prices\nFOR tickers IN portfolio
API --> Client : 200 OK {portfolio + prices}

@enduml
```

## 4. Таблица коммуникаций

| Связь | Тип | Протокол | Обоснование |
|-------|-----|----------|-------------|
| Client → API Gateway | Синхронный | REST + WebSocket | Стандартный API для мобильных |
| API Gateway → Core Banking | Синхронный | Internal (in-process) | Сделки требуют синхронного ответа |
| API Gateway → ClickHouse | Синхронный | TCP (native client) | Запрос текущих котировок |
| API Gateway ← Redis | Асинхронный | PubSub (subscribe) | Push котировок через WebSocket |
| Ingestion → ClickHouse | Асинхронный | TCP (batch insert) | Батчевая вставка котировок |
| Ingestion → /dev/quotes | Синхронный | read() syscall | Чтение из character device |
| Ingestion → Redis | Асинхронный | PUBLISH | Уведомление о новых данных |
| Load Tester → API Gateway | Синхронный | HTTP + WebSocket | Имитация 10K клиентов |

## 5. Data Patterns — Database-per-Service

```plantuml
@startuml
!theme plain
skinparam componentStyle rectangle

package "Write Path" {
  [Ingestion Service\n(Go)] as IngestW
}

package "Read/Write Path" {
  [API Gateway + Core Banking\n(Kotlin/Ktor)] as APIRW
}

package "Read Path" {
  [API Gateway\n(Kotlin/Ktor)] as APIR
}

database "ClickHouse" as CH {
  card "quotes" as quotes_table
  card "candles" as candles_table
}

database "PostgreSQL" as PG {
  card "users" as users_table
  card "accounts" as accounts_table
  card "trades" as trades_table
  card "portfolio" as portfolio_table
}

database "Redis / KeyDB" as Redis {
  card "cache:quotes:*" as cache_quotes
  card "pubsub:quotes" as pubsub
}

IngestW -down-> quotes_table : INSERT (batch)
IngestW -down-> pubsub : PUBLISH

APIRW -down-> users_table : CRUD
APIRW -down-> accounts_table : CRUD (ACID)
APIRW -down-> trades_table : INSERT
APIRW -down-> portfolio_table : CRUD

APIR -down-> quotes_table : SELECT
APIR -down-> candles_table : SELECT
APIR -down-> cache_quotes : GET/SET
APIR -down-> pubsub : SUBSCRIBE

@enduml
```

**Принцип разделения данных:**
- **ClickHouse** — запись: только Ingestion-сервис; чтение: API Gateway.
- **PostgreSQL** — чтение/запись: Core Banking через API Gateway.
- **Redis** — общий кэш горячих котировок + PubSub для WebSocket push.

## 6. Clean Architecture внутри Kotlin-сервисов

```plantuml
@startuml
!theme plain
skinparam packageStyle frame

package "Presentation Layer" <<Frame>> {
  [QuoteRoutes.kt] as QR
  [TradeRoutes.kt] as TR
  [PortfolioRoutes.kt] as PR
  [WebSocketHandler.kt] as WS
  package "DTO" {
    [QuoteResponse.kt]
    [TradeRequest.kt]
    [PortfolioResponse.kt]
  }
}

package "Application Layer" <<Frame>> {
  [GetCurrentQuotes] as UCQ
  [ExecuteTrade] as UCT
  [SubscribeToQuotes] as UCS
  [GetPortfolio] as UCP
}

package "Domain Layer" <<Frame>> {
  package "Entities" {
    [Quote]
    [Ticker]
    [User]
    [Trade]
    [Account]
  }
  package "Repository Interfaces" {
    [QuoteRepository] as IQR
    [UserRepository] as IUR
    [TradeRepository] as ITR
    [AccountRepository] as IAR
  }
}

package "Infrastructure Layer" <<Frame>> {
  [ClickHouseQuoteRepository] as CHQR
  [PostgresUserRepository] as PGUR
  [PostgresTradeRepository] as PGTR
  [PostgresAccountRepository] as PGAR
  [RedisQuotePublisher] as RQP
  [WebSocketQuoteStream] as WSS
}

QR -down-> UCQ
TR -down-> UCT
PR -down-> UCP
WS -down-> UCS

UCQ -down-> IQR
UCT -down-> ITR
UCT -down-> IAR
UCP -down-> IQR
UCS -down-> IQR

CHQR .up.|> IQR : implements
PGUR .up.|> IUR : implements
PGTR .up.|> ITR : implements
PGAR .up.|> IAR : implements

@enduml
```

**Правило зависимостей:** зависимости направлены строго внутрь. Domain Layer не знает ни о ClickHouse, ни о PostgreSQL, ни о Ktor.

### Структура каталогов

```
api-gateway/
├── src/main/kotlin/
│   ├── domain/                    # Чистая бизнес-логика (без зависимостей)
│   │   ├── entities/
│   │   │   ├── Quote.kt           # Котировка
│   │   │   ├── Ticker.kt          # Тикер акции
│   │   │   ├── User.kt            # Пользователь
│   │   │   ├── Account.kt         # Счёт
│   │   │   └── Trade.kt           # Сделка
│   │   └── repositories/          # Только интерфейсы
│   │       ├── QuoteRepository.kt
│   │       ├── UserRepository.kt
│   │       ├── TradeRepository.kt
│   │       └── AccountRepository.kt
│   │
│   ├── application/               # Use cases
│   │   ├── GetCurrentQuotes.kt
│   │   ├── SubscribeToQuotes.kt
│   │   ├── ExecuteTrade.kt
│   │   └── GetPortfolio.kt
│   │
│   ├── infrastructure/            # Реализации интерфейсов
│   │   ├── clickhouse/
│   │   │   └── ClickHouseQuoteRepository.kt
│   │   ├── postgres/
│   │   │   ├── PostgresUserRepository.kt
│   │   │   ├── PostgresTradeRepository.kt
│   │   │   └── PostgresAccountRepository.kt
│   │   ├── redis/
│   │   │   └── RedisQuotePublisher.kt
│   │   └── websocket/
│   │       └── WebSocketQuoteStream.kt
│   │
│   └── presentation/              # Ktor routes + DTO
│       ├── routes/
│       │   ├── QuoteRoutes.kt
│       │   ├── TradeRoutes.kt
│       │   └── PortfolioRoutes.kt
│       └── dto/
│           ├── QuoteResponse.kt
│           ├── TradeRequest.kt
│           └── PortfolioResponse.kt
│
├── src/test/kotlin/               # Тесты
├── build.gradle.kts
└── Dockerfile
```

## 7. Ключевые архитектурные решения (ADR)

### ADR-001: ClickHouse как источник правды для котировок
- **Контекст:** Требование ТЗ (FR-SYS-03) — все запросы на получение цены обслуживаются выборками из ClickHouse, а не из in-memory кэша.
- **Решение:** Ingestion-сервис пишет в ClickHouse, API Gateway читает из ClickHouse.
- **Последствия:** Задержка обновления (батчинг + disk I/O), компенсируется Redis-кэшем для горячих данных.

### ADR-002: Батчинг в Go Ingestion-сервисе
- **Контекст:** Драйвер генерирует поток котировок непрерывно. ClickHouse оптимизирован для batch insert, а не построчной вставки.
- **Решение:** Go-сервис накапливает котировки в буфер и вставляет пачками (например, каждые 100ms или 1000 записей).
- **Последствия:** Увеличенная пропускная способность, но добавляется задержка буферизации.

### ADR-003: Redis PubSub для push-уведомлений
- **Контекст:** Клиенты должны получать обновления котировок в реальном времени (< 1 сек от генерации).
- **Решение:** Ingestion публикует в Redis PubSub при каждом батче. API Gateway подписан и пушит через WebSocket.
- **Последствия:** Decoupling между Ingestion и API Gateway. Redis как единая точка для pub/sub.

### ADR-004: PostgreSQL с голым SQL (без ORM)
- **Контекст:** Требование ТЗ. Финансовые операции требуют строгого контроля транзакций (ACID).
- **Решение:** Прямые SQL-запросы через JDBC-драйвер (например, Exposed в SQL DSL режиме или чистый JDBC).
- **Последствия:** Полный контроль над запросами и транзакциями, но больше boilerplate.

### ADR-005: Koin для Dependency Injection
- **Контекст:** Clean Architecture требует инверсии зависимостей. Ktor не имеет встроенного DI.
- **Решение:** Koin — лёгковесный DI-фреймворк, идиоматичный для Kotlin, хорошо интегрируется с Ktor.
- **Последствия:** Простая конфигурация, тестируемость через подмену реализаций.

## 8. Схема развёртывания

```plantuml
@startuml
!theme plain

node "Docker Compose" {

  node "api-gateway" as api {
    [Ktor Application\nPort 8080]
  }

  node "ingestion" as ingest {
    [Go Service]
  }

  node "load-tester" as lt {
    [Bot Simulator]
  }

  node "postgres" as pg {
    database "PostgreSQL\nPort 5432"
  }

  node "clickhouse" as ch {
    database "ClickHouse\nPort 8123 (HTTP)\nPort 9000 (Native)"
  }

  node "redis" as redis {
    database "Redis\nPort 6379"
  }
}

node "Host Linux" {
  [Kernel Driver\n/dev/quotes] as driver
}

api -down-> pg
api -down-> ch
api -down-> redis

ingest -down-> ch
ingest -down-> redis
ingest -left-> driver : mount /dev/quotes

lt -up-> api

@enduml
```

## 9. Нефункциональные требования и архитектурные ответы

| Требование | Значение | Архитектурный ответ |
|-----------|----------|---------------------|
| NFR-001 Latency | < 1 сек от генерации до клиента | Redis PubSub + WebSocket push, батч < 100ms |
| NFR-002 Throughput | 10K активных сессий | Горизонтальное масштабирование API Gateway, корутины Ktor |
| NFR-003 Consistency | ACID для балансов | PostgreSQL транзакции, serializable isolation |
| FR-OBS-01 Metrics | RPS, Error Rate, Latency | OpenTelemetry SDK → Collector → Grafana |
| FR-OBS-02 Tracing | Сквозной трейсинг | OpenTelemetry trace context propagation |
