## Why

Сейчас gateway выдаёт JWT access-токен, а веб-клиент сохраняет его в `localStorage` (`client/web/src/auth/sessionStorage.js`). Любой сторонний JavaScript на странице (например, при XSS) может прочитать токен и использовать его до истечения срока действия. Access-токен — это долгоживущий (1 час) credential, который нечем отозвать.

## What Changes

- **BREAKING** (для текущего клиента и API логина): `POST /auth/login` больше не является единственным источником access-токена — он дополнительно устанавливает httpOnly cookie с refresh-токеном.
- Gateway получает поддержку refresh-токенов: хранение в БД, ротация при обновлении, отзыв при выходе.
- Новый публичный эндпоинт `POST /auth/refresh` — принимает refresh-токен из httpOnly cookie, отзывает его, выдаёт новую пару (новый access-токен в теле, новый refresh-токен в cookie).
- Новый эндпоинт `POST /auth/logout` — отзывает текущий refresh-токен и удаляет cookie.
- Gateway включает защиту от CSRF (Spring Security) для изменяющих запросов: CSRF-токен в читаемой cookie + заголовок.
- **BREAKING** для веб-клиента: access-токен больше не сохраняется в `localStorage`, а живёт только в памяти приложения; refresh-токен недоступен JavaScript (httpOnly cookie).
- Сессия между перезагрузками страницы восстанавливается вызовом `/auth/refresh` при старте приложения.
- Выход из системы вызывает `/auth/logout` (отзыв refresh-токена на сервере), а не только очистку локального хранилища.
- Обработка истёкшего access-токена в защищённых запросах: клиент пытается обновить сессию через `/auth/refresh`, и только при неудаче разлогинивает пользователя.

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `gateway-user-auth`: выдача refresh-токена в httpOnly cookie, ротация и отзыв refresh-токенов, эндпоинты `/auth/refresh` и `/auth/logout`, защита от CSRF.
- `web-user-auth`: отказ от `localStorage`, хранение access-токена в памяти, восстановление сессии через refresh-cookie, выход через gateway, передача CSRF-заголовка и повтор запроса после обновления токена.

## Impact

- `backend/gateway`: `AuthController`, `LoginService`, `JwtService`, `SecurityConfig`, `JwtAuthenticationFilter`, `ProxyRoutingFilter` (проброс/блокировка CSRF-заголовка), `GatewayProperties` (TTL refresh-токена, атрибуты cookie), новая сущность/таблица refresh-токенов (Flyway-миграция `V2`), новые DTO и исключения.
- `client/web`: `src/api/client.js` (получение CSRF-токена, `credentials: 'include'`, заголовок `X-XSRF-TOKEN`, повтор после refresh), `src/api/authApi.js`, `src/auth/AuthProvider.jsx`, `src/auth/sessionStorage.js` (удаляется), `RequireAuth.jsx`, `HomePage.jsx`/страницы, `vite.config.js` (проксирование cookie), тесты.
- API gateway: тела ответов `/auth/login` и `/auth/refresh` включают access-токен и email пользователя; cookie `refresh_token` (httpOnly) и `XSRF-TOKEN` (читаемая).
- БД: новая таблица refresh-токенов в схеме gateway (новая Flyway-миграция).
- Не затрагивается: `backend/tracker` (получает `X-User-Id` через gateway без изменений), `tracker-habits`, `gateway-request-routing` (маршрутизация сохраняется; cookie и CSRF-заголовок распространяются на проксируемые запросы).
- Требуется HTTPS в проде для атрибута `Secure` у cookie; dev-режим через Vite-прокси работает как same-origin.
