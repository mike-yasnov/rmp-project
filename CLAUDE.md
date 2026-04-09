# HighLoad Invest Ecosystem — Backend

## Project Overview
Учебная экосистема имитации биржевых торгов. Курс РМП, ИТМО, весна 2026.
Мы отвечаем за **бэкенд** — микросервисы, базы данных, API для мобильных клиентов.

## Architecture
Микросервисная архитектура из 5 бэкенд-компонентов:

| Сервис | Язык | Назначение |
|--------|------|-----------|
| API Gateway | Kotlin/Ktor | REST + WebSocket для мобильных клиентов |
| Core Banking | Kotlin | Балансы, сделки, портфели (PostgreSQL) |
| Ingestion | Go | Батчинг котировок из драйвера в ClickHouse |
| Load Tester | Kotlin/Go | Имитация 10K клиентов |
| Driver | C | Linux kernel module, генерация котировок |

## Tech Stack (обязательный по ТЗ)
- **Kotlin + Ktor** — API и бизнес-логика, корутины, kotlinx.serialization
- **Go** — Ingestion-сервис
- **C** — Linux kernel driver
- **ClickHouse** — котировки (eventual consistency)
- **PostgreSQL** — пользователи, балансы (ACID, голый SQL)
- **Redis/KeyDB** — кэш + брокер сообщений
- **OpenTelemetry** — метрики и трейсинг
- **WebSocket** — push котировок
- **Docker / Docker Compose** — контейнеризация

## Key Requirements
- Latency: обновление цены на клиенте < 1 сек
- Throughput: 10,000 активных сессий
- ACID для финансов (PostgreSQL), eventual consistency для котировок (ClickHouse)

## Project Structure
```
docs/           — ТЗ (srs.md) и лекционные материалы
.ai-factory/    — описание проекта для AI
.claude/        — скиллы и настройки Claude Code
```

## Commands
```bash
# TODO: заполнить по мере создания проекта
# docker-compose up     — запуск всех сервисов
# ./gradlew build       — сборка Kotlin-сервисов
# go build ./...        — сборка Go-сервиса
```

## Conventions
- Язык коммуникации: русский
- Код и комментарии: английский
- Документация API: английский
- Отчёт: русский
