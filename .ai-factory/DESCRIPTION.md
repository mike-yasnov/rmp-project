# Project: HighLoad Invest Ecosystem — Backend

## Overview
Educational trading simulation platform that mimics a stock exchange broker backend. The system generates synthetic stock quotes via a Linux kernel driver, ingests them into an analytical database (ClickHouse), and exposes real-time and historical data to mobile clients through a Kotlin/Ktor API layer. Financial operations (buy/sell) are validated against user balances in PostgreSQL.

## Our Scope: Backend Services

We are responsible for the server-side components of the ecosystem:

### Microservices
1. **API Gateway** (Kotlin/Ktor) — Single entry point for both mobile clients (Android Native + Cross-platform). REST + WebSocket for real-time quotes push.
2. **Core Banking** (Kotlin) — Business logic: user accounts, balances, trade execution. ACID transactions in PostgreSQL.
3. **Ingestion Service** (Go) — Reads quotes from the kernel driver, batches them, and bulk-inserts into ClickHouse.
4. **Load Tester** (Kotlin/Go/Python) — Simulates 10,000 concurrent client sessions hitting the API.
5. **Linux Driver** (C) — Kernel-space module generating synthetic stock quotes.

### Data Stores
- **ClickHouse** — Quotes storage and history (columnar, eventual consistency)
- **PostgreSQL** — User data, balances, portfolio (ACID, strong consistency)
- **Redis/KeyDB** — Cache layer and message broker between services

### Cross-cutting
- **OpenTelemetry** — Metrics (RPS, Error Rate, Latency) and distributed tracing
- **WebSocket** — Real-time quote delivery to clients
- **Docker** — Containerization of all services

## Tech Stack (Mandatory per SRS)
- **Language (API):** Kotlin
- **Framework:** Ktor
- **Language (Ingestion):** Go
- **Language (Driver):** C
- **Database (quotes):** ClickHouse
- **Database (users):** PostgreSQL (raw SQL, no ORM)
- **Cache/Broker:** Redis Server / KeyDB
- **Observability:** OpenTelemetry
- **Transport:** REST + WebSocket
- **Containerization:** Docker / Docker Compose

## Core Features (Backend)
- FR-API-01: Unified API for both mobile clients
- FR-API-02: Periodic ClickHouse polling + WebSocket push of fresh quotes
- FR-API-03: Trade execution (buy/sell lots) with balance validation in PostgreSQL
- FR-SYS-02: Batched ingestion from driver to ClickHouse
- FR-SYS-03: All price/history queries served from ClickHouse
- FR-OBS-01: Metrics collection from all services
- FR-OBS-02: End-to-end request tracing (API Gateway → DB)

## Non-Functional Requirements
- **Latency:** Quote update on client < 1 second from generation
- **Throughput:** 10,000 concurrent sessions (bots)
- **Consistency:** ACID for financial data (PostgreSQL), eventual for quotes (ClickHouse)
- **Logging:** Configurable via LOG_LEVEL
- **Error handling:** Structured error responses

## Architecture Notes
- Microservice architecture with clear service boundaries
- ClickHouse is the "source of truth" for market data, not an in-memory cache
- API Gateway handles authentication, routing, WebSocket management
- Core Banking handles all financial operations with strict ACID guarantees
- Ingestion Service acts as a bridge between kernel-space driver and ClickHouse
- Redis serves dual purpose: caching hot data and inter-service messaging

## Deliverables
- PDF report (SRS, Architecture, Implementation, Testing)
- Obsidian knowledge base
- Source code in git repository
- Project presentation
