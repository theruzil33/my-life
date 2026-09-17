## 1. Gateway: хранение refresh-токенов

- [x] 1.1 Добавить Flyway-миграцию `V2__create_gateway_refresh_tokens_table.sql` с таблицей `gateway_refresh_tokens` (id, user_id с FK на `gateway_users`, token_hash с уникальным индексом, issued_at, expires_at, revoked_at) и проверить, что миграция применяется на Postgres и на H2 в тестах
- [x] 1.2 Добавить JPA-сущность refresh-токена и Spring Data репозиторий с поиском по хешу (проверка: приложение стартует с `ddl-auto=validate` без ошибок схемы)
- [x] 1.3 Реализовать сервис refresh-токенов: генерация криптостойкого случайного значения (≥256 бит), хеширование SHA-256, выдача с привязкой к пользователю и TTL, поиск и проверка действительности (не истёк, не отозван), отзыв токена (проверка: unit-тесты из группы 5)
- [x] 1.4 Добавить свойства конфигурации `gateway.auth.refresh-token-ttl`, `gateway.auth.cookie.secure`, `gateway.auth.cookie.same-site` и изменить `gateway.jwt.access-token-ttl` по умолчанию на `PT15M` в `application.properties` и тестовом `application.properties` (проверка: `GatewayPropertiesTest` покрывает значения по умолчанию и переопределение)

## 2. Gateway: эндпоинты аутентификации и cookie

- [x] 2.1 Изменить `/auth/login`: вернуть access-токен и email в теле, установить httpOnly cookie `refresh_token` через сервис refresh-токенов, не включать refresh-токен в тело (проверка: `AuthControllerTest` проверяет тело и заголовок `Set-Cookie` с `HttpOnly`)
- [x] 2.2 Реализовать публичный `POST /auth/refresh`: прочитать refresh-токен из cookie, отклонить отсутствующий/неизвестный/истёкший/отозванный токен ошибкой аутентификации, иначе отозвать старый и выдать новую пару access + refresh cookie (проверка: тесты из группы 6 на ротацию и отклонения)
- [x] 2.3 Реализовать `POST /auth/logout`: отозвать refresh-токен из cookie, удалить cookie (`Max-Age=0`), при отсутствии cookie завершать без ошибки сервера; выход не затрагивает другие сессии (проверка: тесты из группы 6)
- [x] 2.4 Реализовать публичный `GET /auth/csrf`, возвращающий CSRF-токен и инициирующий запись читаемой cookie `XSRF-TOKEN` (проверка: `AuthControllerTest` получает непустой токен без аутентификации и заголовок `Set-Cookie`)
- [x] 2.5 Добавить DTO ответа логина/refresh (access-токен, tokenType, expiresIn, email) и проверить, что email и access-токен присутствуют, а refresh-токен и пароль/хеш отсутствуют в теле (проверка: `AuthControllerTest`)

## 3. Gateway: CSRF-защита и фильтры безопасности

- [x] 3.1 Включить CSRF в `SecurityConfig` с `CookieCsrfTokenRepository.withHttpOnlyFalse()` и `CsrfTokenRequestAttributeHandler` с `setCsrfRequestAttributeName(null)` для приёма сырого токена из заголовка `X-XSRF-TOKEN` (проверка: тест изменяющего запроса с валидным токеном проходит, без токена — отклоняется)
- [x] 3.2 Обновить список публичных эндпоинтов (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/csrf`) и сохранить `SessionCreationPolicy.STATELESS` (проверка: тесты доступа без access-токена и проверки защищённых маршрутов)
- [x] 3.3 Проверить, что `JwtAuthenticationFilter` и существующая проверка Bearer-токена не регрессируют при включённом CSRF (проверка: существующие и обновлённые `JwtAuthenticationFilterTest`, `JwtSecurityTest`)

## 4. Gateway: проксирование и конфигурация cookie

- [x] 4.1 Убедиться, что cookie refresh-токена не уходит в проксируемые запросы за счёт `Path=/auth`, и при необходимости добавить `x-xsrf-token` в blocklist исходящих заголовков `ProxyRoutingFilter` (проверка: `ProxyRoutingTest` подтверждает, что защищённый запрос проксируется, а refresh cookie вниз не передаётся)
- [x] 4.2 Проверить маршрутизацию защищённых изменяющих запросов при включённом CSRF: запрос с валидным CSRF-токеном доходит до backend-сервиса, без токена — отклоняется на gateway (проверка: тест `ProxyRoutingTest` с `csrf()`)

## 5. Gateway: unit-тесты нового бизнес-логики

- [x] 5.1 Unit-тесты сервиса refresh-токенов: выдача, истечение, отзыв, ротация и отклонение повторного использования (проверка: `RefreshTokenServiceTest` проходит)
- [x] 5.2 Unit-тесты отсутствия исходного значения токена в хранилище: сохранённый `token_hash` не равен выданному значению и однозначно соответствует ему (проверка: тест репозитория/сервиса)
- [x] 5.3 Unit-тесты обработки cookie: корректные атрибуты (`HttpOnly`, `Secure` по конфигурации, `SameSite`, `Path=/auth`, `Max-Age`) при выдаче, обновлении и удалении (проверка: тест построения cookie/`AuthControllerTest`)

## 6. Gateway: интеграционные тесты сценариев

- [x] 6.1 Интеграционные тесты обновления сессии: успешная ротация, отсутствие cookie, неизвестный, истёкший и повторно использованный токен (проверка: тесты через `MockMvc` с `csrf()` проходят)
- [x] 6.2 Интеграционные тесты выхода: успешный отзыв, отклонение обновления после выхода, выход без cookie без ошибки, независимость второй сессии (проверка: тесты через `MockMvc` проходят)
- [x] 6.3 Тесты CSRF: bootstrap `/auth/csrf`, отклонение изменяющего запроса без токена и с неверным токеном, успех с валидным токеном (проверка: тесты через `MockMvc` проходят)
- [x] 6.4 Прогнать полный набор тестов gateway (`./mvnw -q test` в `backend/gateway`) и убедиться, что все тесты проходят

## 7. Веб-клиент: API-слой

- [x] 7.1 Добавить в `client.js` поддержку `credentials: 'include'` для auth-запросов и получение CSRF-токена из cookie `XSRF-TOKEN` (при необходимости через `GET /auth/csrf`), добавлять заголовок `X-XSRF-TOKEN` ко всем изменяющим запросам (проверка: unit-тесты `client.test.js`)
- [x] 7.2 Реализовать single-flight обновление сессии и повтор исходного защищённого запроса один раз при `401`, с вызовом `setUnauthorizedHandler` только при неудачном обновлении (проверка: unit-тесты `client.test.js` на повтор, на отсутствие бесконечного цикла и на один refresh для параллельных запросов)
- [x] 7.3 Расширить `authApi` методами `csrf()`, `refresh()` и `logout()`; `login` возвращает access-токен и email (проверка: unit-тесты `authApi.test.js`)

## 8. Веб-клиент: состояние аутентификации

- [x] 8.1 Перевести `AuthProvider` на хранение access-токена только в памяти и удалить `src/auth/sessionStorage.js`; при старте удалять legacy-ключ `my-life.auth` из `localStorage` (проверка: unit-тесты `AuthProvider.test.jsx` подтверждают отсутствие токена в `localStorage`)
- [x] 8.2 Реализовать восстановление сессии при запуске через `POST /auth/refresh`, включая признак загрузки, чтобы `RequireAuth` не редиректил на `/login` до завершения попытки (проверка: unit-тесты `AuthProvider.test.jsx` и `RequireAuth.test.jsx`)
- [x] 8.3 Реализовать выход через `POST /auth/logout` (best-effort) с локальной очисткой состояния даже при недоступном gateway (проверка: unit-тесты `AuthProvider.test.jsx`)
- [x] 8.4 Обновить страницы, где отображается email/выход (`HomePage`), под новое состояние без сохранённого токена (проверка: тесты страниц и `App.test.jsx`)

## 9. Веб-клиент: тесты и проверка

- [x] 9.1 Обновить тесты `client.test.js`, `authApi.test.js`, `AuthProvider.test.jsx`, `RequireAuth.test.jsx`, `LoginPage.test.jsx`, `RegisterPage.test.jsx`, `App.test.jsx` под новую схему (проверка: `npm run test:run` в `client/web` проходит)
- [x] 9.2 Прогнать `npm run lint` и `npm run test:run` в `client/web` и убедиться, что ошибок нет
- [x] 9.3 Проверить сквозной сценарий в dev: вход, перезагрузка страницы (сессия восстанавливается через refresh-cookie), выход, повторная перезагрузка остаётся неаутентифицированной; убедиться, что access-токен и refresh-токен отсутствуют в `localStorage`/`sessionStorage`, а cookie `refresh_token` помечена `HttpOnly` (проверка: ручной прогон через Vite-прокси)
