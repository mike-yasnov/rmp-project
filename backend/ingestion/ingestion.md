# Go Ingestion Service

Сервис на языке **Go**, предназначенный для чтения потока биржевых котировок из драйвера ядра Linux (`/dev/quotes`) и пакетной загрузки их в аналитическую базу данных **ClickHouse**.

Является частью системы **HighLoad Invest Ecosystem**.

## Архитектура

1.  **Источник данных:** Модуль ядра Linux (`quotes_driver.ko`), генерирующий структуры `struct quote` в кольцевой буфер и предоставляющий доступ через character device `/dev/quotes`.
2.  **Ingestion Service (Go):**
    *   Открывает устройство `/dev/quotes`.
    *   Читает бинарные данные порциями по 28 байт.
    *   Парсит структуру (Symbol, Price, Change, Timestamp).
    *   Накопляет данные в батчи (по 100 записей или каждые 500 мс).
    *   Отправляет батчи в ClickHouse через Native Protocol.
3.  **Хранилище:** ClickHouse таблица `quotes`.

## Требования

*   **Go** 1.21+
*   **ClickHouse** (запущенный)
*   **Linux Kernel Module** (для продакшена)

## Сборка и запуск(исполняемого файла)

```bash
cd backend/ingestion
go build -o ingestion .

./ingestion
```
