# Заключение

В ходе работы команда HighLoad Invest спроектировала и реализовала программно-аппаратный
комплекс имитации биржевых торгов в полном соответствии с ТЗ:

## Достигнутые результаты

**Backend-инфраструктура.** Развёрнута микросервисная архитектура из пяти компонентов:
api-gateway и core-banking на Kotlin/Ktor, go-ingestion на Go, kernel-driver на C и
load-tester на Kotlin. Стек поднят docker-compose-ом на dedicated-сервере
(8 vCPU, 16 GB RAM); инфраструктура (PostgreSQL, ClickHouse, Redis) и observability
(otel-collector, Jaeger, Prometheus, Grafana) — в том же compose. Перед сервисами nginx
проксирует все REST/WS/UI-маршруты.

**Полная интеграция OpenTelemetry (FR-OBS-01/02).** В каждый Kotlin-сервис подключён
SDK 1.46 с OTLP gRPC-экспортёром на 4317. В go-ingestion — OTLP HTTP на 4318. Custom
span-ы обернули `trade.execute`, `clickhouse.<op>`, `redis.publish`, метрики
домен-уровня (`trades.success`, `ws.active_connections`, `quotes.ingested` и т.д.).
Trace propagation через W3C Trace Context связывает api-gateway → core-banking
в единую трассу. В Jaeger видны все три сервиса, в Prometheus — 12 пользовательских
метрик, в Grafana — auto-provisioned дашборд «HighLoad Invest — Backend Overview».

**Подтверждённый NFR-002 — 10 000 сессий.** Лестница нагрузочных прогонов
1 K → 5 K → 10 K показала:

| Bots | RPS | Avg latency | Errors |
|------|-----|-------------|--------|
| 1 000 | 335 | 24 ms | 0 |
| 5 000 | 594 | 225 ms | 0.04 % |
| 10 000 | 590 | 399 ms | 0.03 % |

Система держит 10 000 параллельных ботов с error-rate ниже 0.05 % и latency, не
превышающим 400 ms. Узкое место — ClickHouse `LIMIT 1 BY ticker` на каждый
`/api/quotes`-запрос; в production-конфигурации это снимается Redis-кэшем или
materialized view. PostgreSQL с `READ COMMITTED + SELECT FOR UPDATE` обработал
~30 K финансовых транзакций без блокировок и ACID-нарушений.

**Реальный driver runtime.** Kernel-module `quotes_driver.ko` собирается локально
через `make all` (требует `linux-headers-$(uname -r)`), загружается через `insmod`
и создаёт `/dev/quotes`. `dmesg` подтверждает корректную регистрацию символьного
устройства. go-ingestion переключён на `QUOTES_DEVICE=/dev/quotes`, читает живые
котировки и публикует их в ClickHouse + Redis. API возвращает 20 тикеров от
драйвера (MSFT, INTC, AMD и т.д.); fallback-генератор остаётся в коде на случай
отсутствия драйвера в локальной разработке.

**Мобильные клиенты.** Реализованы две версии: Native (Kotlin + Jetpack Compose,
~360 LOC, MVVM, OkHttp + kotlinx.serialization) и Cross-platform (React Native + Expo,
~580 LOC, hooks, fetch + WebSocket). Backend-команда подтвердила полную готовность
API: все эндпоинты возвращают корректные ответы, CORS-preflight открыт,
`/ws/quotes` пушит данные в realtime. Сравнительный анализ по DOC-002 показал, что
для production-брокера предпочтительна Native-реализация, для MVP с быстрой
итерацией — Cross-platform.

## Выявленные ограничения и направления развития

- **ClickHouse под `/api/quotes`** становится «узким горлышком» уже на 5 K ботов;
  следующим шагом — добавить Redis-кэш горячих котировок с TTL ~500 ms либо
  materialized view с агрегацией.
- **Redis Pub/Sub** не персистентен: если api-gateway переподключается в момент
  PUBLISH, сообщение теряется. Для production-сценариев лучше Redis Streams.
- **Trace propagation через Redis Pub/Sub отсутствует** — span go-ingestion-а и
  span api-gateway-WS-broadcast-а попадают в разные trace-ы. Решается переносом
  trace-id в JSON-payload сообщения.
- **Authentication не реализован** — все эндпоинты открыты. Для учебной задачи
  это допустимо, для реальной эксплуатации добавить JWT в api-gateway и
  передавать `Authorization: Bearer ...` дальше.
- **Driver-генератор однопоточный** и выдаёт фиксированные 20 тикеров; в реальном
  биржевом интерфейсе котировок было бы тысячи и kernel-thread-ов несколько.

## Итог

Проект полностью покрывает учебные цели курса: студенты прошли через kernel space,
high-throughput data ingestion, ACID-транзакции в реляционной БД, реактивный
WebSocket fan-out, distributed tracing, нагрузочное тестирование и, наконец,
два разных подхода к мобильной разработке. Все исходники и инструкции по
запуску — в монорепо https://github.com/mike-yasnov/rmp-project.
