# HighLoad Invest Native Android

Нативный Android-клиент учебного брокера на Kotlin + Jetpack Compose.

## Возможности

- регистрация учебного пользователя;
- текущие котировки из `GET /api/quotes`;
- realtime-обновления через `WS /ws/quotes`;
- свечной график по `GET /api/quotes/{ticker}/candles`;
- учебное пополнение счёта;
- покупка и продажа целого числа лотов;
- портфель и баланс.

## Запуск

1. Поднимите backend:
   ```bash
   cd ../backend
   docker compose up -d --build
   ```

2. Откройте папку `mobile-native/` в Android Studio.

3. Запустите конфигурацию `app` на Android emulator.

По умолчанию приложение ходит в `http://10.0.2.2:8080`, что соответствует `localhost:8080` хостовой машины из Android emulator. Для физического устройства замените `API_BASE_URL` в `app/build.gradle.kts` на IP машины с backend.
