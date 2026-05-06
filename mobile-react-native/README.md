# HighLoad Invest React Native

Кросс-платформенный мобильный клиент учебного брокера на React Native + Expo.

## Возможности

- регистрация учебного пользователя;
- текущие котировки и WebSocket-обновления;
- свечной график по выбранному тикеру;
- учебное пополнение счёта;
- покупка и продажа лотов;
- портфель и баланс.

## Запуск

1. Поднимите backend:
   ```bash
   cd ../backend
   docker compose up -d --build
   ```

2. Установите зависимости:
   ```bash
   npm install
   ```

3. Запустите Android-клиент:
   ```bash
   npm run android
   ```

По умолчанию клиент использует `http://10.0.2.2:8080`. Для физического устройства задайте:

```bash
EXPO_PUBLIC_API_BASE_URL=http://<host-ip>:8080 npm run android
```

## Проверки

```bash
npm run typecheck
npm audit --omit=dev
```
