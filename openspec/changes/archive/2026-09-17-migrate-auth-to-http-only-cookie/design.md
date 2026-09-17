## Context

См. `proposal.md` — Why/What Changes. Текущее состояние, важное для подхода:

- Access-токен — симметрично подписанный JWT (`JwtService`), TTL 1 час, передаётся клиентом в заголовке `Authorization: Bearer`. Gateway проверяет его в `JwtAuthenticationFilter` и после проверки добавляет `X-User-Id` в проксируемый запрос (`ProxyRoutingFilter`).
- Spring Security настроен как stateless: `csrf` отключён, `SessionCreationPolicy.STATELESS`, публичны только `/auth/register` и `/auth/login`.
- Пользователи хранятся в Postgres (таблица `gateway_users`, Flyway `V1`), H2 используется в тестах с `ddl-auto=validate`.
- Веб-клиент (`client/web`, React + Vite) сохраняет `{ accessToken, email }` в `localStorage` (`sessionStorage.js`) и передаёт токен вручную в `request()` (`src/api/client.js`); Vite-прокси уже пробрасывает `/auth` на gateway, то есть в dev клиент и gateway same-origin.

Решения (выбраны пользователем): access-токен — в памяти, refresh-токен — в httpOnly cookie; refresh-токены хранятся в БД с ротацией и отзывом; CSRF-защита — полноценный CSRF-токен Spring Security.

## Goals / Non-Goals

**Goals:**

- Исключить хранение долгоживущего credential в доступном JavaScript хранилище.
- Дать gateway возможность отзывать сессии (выход, истечение refresh-токена) без перехода на серверные HttpSession.
- Сохранить stateless-проверку access-токена и существующую передачу `X-User-Id` вниз по цепочке.
- Определить защиту cookie-эндпоинтов от CSRF и её влияние на проксируемые изменяющие запросы.

**Non-Goals:**

- Переход на серверные HttpSession или хранение access-токена в cookie.
- Единый SSO/OIDC, MFA, восстановление пароля, rate limiting.
- Вынесение refresh-токенов в Redis или отдельный сервис.
- Продовая топология reverse-proxy — вынесена в Open Questions, не меняет спецификации.

## Decisions

### 1. Access-токен остаётся JWT в памяти, refresh-токен — единственный cookie-credential

Access-токен по-прежнему выдаётся gateway и возвращается в теле ответа `/auth/login` и `/auth/refresh`; клиент держит его только в React state и передаёт в `Authorization: Bearer`. Это сохраняет `JwtAuthenticationFilter`, `X-User-Id`-контракт и не требует CSRF для обычных защищённых запросов (заголовок не подставляется браузером автоматически).

Refresh-токен — отдельная сущность, живёт только в httpOnly cookie и используется только эндпоинтами `/auth/refresh` и `/auth/logout`. Cookie с refresh-токеном MUST NOT попадать ни в тело ответа, ни в JavaScript.

Альтернатива — класть в httpOnly cookie и access-токен — отклонена: любой запрос становится cookie-аутентифицированным, что требует CSRF для всех защищённых маршрутов и распространяет cookie на проксируемые сервисы.

### 2. Refresh-токен: непрозрачное случайное значение, в БД — только хеш

Значение refresh-токена — криптостойкая случайная строка (≥256 бит, Base64 URL-safe), не JWT. В таблице хранится SHA-256 хеш значения и метаданные; поиск идёт по хешу (уникальный индекс). Исходное значение не сохраняется и не логируется.

Таблица `gateway_refresh_tokens` (миграция `V2__create_gateway_refresh_tokens_table.sql`):

- `id BIGINT` PK
- `user_id BIGINT NOT NULL` → FK на `gateway_users(id)`
- `token_hash VARCHAR(64) NOT NULL` — уникальный индекс
- `issued_at TIMESTAMPTZ NOT NULL`
- `expires_at TIMESTAMPTZ NOT NULL`
- `revoked_at TIMESTAMPTZ NULL`

Альтернатива — refresh как подписанный JWT — отклонена: отзыв отдельной сессии всё равно требует серверного состояния, а непрозрачный токен не раскрывает никаких claims и проще хешируется.

### 3. Ротация при обновлении

`/auth/refresh`: найти запись по хешу → отклонить, если запись отсутствует, `expires_at <= now` или `revoked_at != null` → пометить текущий токен отозванным (`revoked_at = now`) → выдать новый access-токен и новый refresh-токен (новая запись, новая cookie). Это делает refresh-токен одноразовым: повторное использование старого значения после ротации отклоняется.

Повторное использование уже отозванного/ротированного токена трактуется как подозрение на кражу, но в рамках этого изменения только отклоняется (без каскадного отзыва остальных сессий) — усиление вынесено в Open Questions, чтобы не менять наблюдаемое поведение выхода.

### 4. Атрибуты cookie

- Имя: `refresh_token`; `HttpOnly`; `Path=/auth` (ограничивает отправку только auth-эндпоинтами и не даёт cookie утекать в `/tracker`); `Max-Age` = TTL refresh-токена.
- `Secure` — параметризуется (`gateway.auth.cookie.secure`, по умолчанию `true`; в dev по http — `false`).
- `SameSite` — параметризуется (`gateway.auth.cookie.same-site`, по умолчанию `Strict`), поскольку целевое размещение — same-origin (Vite-прокси в dev, reverse-proxy в проде).
- TTL: `gateway.auth.refresh-token-ttl` по умолчанию `P7D`. `gateway.jwt.access-token-ttl` уменьшается с `PT1H` до `PT15M`: access-токен теперь легко обновить, поэтому короткий срок снижает ценность утечки.

### 5. CSRF: `CookieCsrfTokenRepository` + сырой токен в заголовке

Включается `csrf` Spring Security (сейчас отключён). Используется `CookieCsrfTokenRepository.withHttpOnlyFalse()`: CSRF-токен кладётся в читаемую JavaScript cookie `XSRF-TOKEN` (не является credential сам по себе), клиент возвращает его в заголовке `X-XSRF-TOKEN`.

Так как Spring Security 6 по умолчанию применяет XOR/BREACH-обработку токена, для SPA-схемы нужен `CsrfTokenRequestAttributeHandler` с `setCsrfRequestAttributeName(null)`, чтобы валидировалось сырое значение из cookie, а не разложенное по request-атрибуту.

Публичный `GET /auth/csrf` (пермит без аутентификации) принимает `CsrfToken` аргументом, чем заставляет репозиторий записать cookie, и возвращает токен в теле — клиент получает токен до первого изменяющего запроса.

Защита применяется ко всем изменяющим методам, включая `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` и проксируемые запросы (например, `POST /tracker/...`). `SessionCreationPolicy.STATELESS` сохраняется: CSRF нужен здесь потому, что браузер автоматически отправляет cookie refresh-токена, а не из-за HttpSession.

Альтернатива — `SameSite=Strict` без CSRF-токена — отклонена пользователем: не защищает от same-site сценариев и менее явна для аудита.

### 6. Граница с проксированием

`ProxyRoutingFilter` не меняется по логике, но:

- Refresh-cookie не попадает в проксируемые запросы благодаря `Path=/auth` (не нужно полагаться на blocklist заголовков).
- `XSRF-TOKEN` cookie и заголовок `X-XSRF-TOKEN` могут уходить вниз по цепочке — они не несут полномочий; `X-XSRF-TOKEN` при необходимости добавляется в blocklist исходящих заголовков, чтобы не путать сервисы (решается в задачах).
- CSRF-фильтр Spring Security выполняется до `ProxyRoutingFilter` (security-цепочка имеет более ранний order, чем зарегистрированный фильтр), поэтому изменяющие проксируемые запросы без валидного CSRF-токена отклоняются на gateway.

### 7. Веб-клиент: in-memory доступ к токену, единая точка refresh

- Удаляется `src/auth/sessionStorage.js`; `AuthProvider` хранит `{ accessToken, email }` в state. При старте дополнительно удаляется legacy-ключ `my-life.auth` из `localStorage` (после обновления в нём может лежать старый токен).
- Восстановление сессии при запуске: `POST /auth/refresh` с cookie → доступ в память → `isAuthenticated`. Пока идёт восстановление, приложение не должно преждевременно редиректить на `/login` (нужен признак загрузки, например `isRestoring`).
- `src/api/client.js`: `credentials: 'include'` для auth-запросов; перед изменяющим запросом гарантируется наличие CSRF-токена (cookie `XSRF-TOKEN` читается и уходит в заголовке `X-XSRF-TOKEN`); при `401` на защищённом запросе выполняется однократный refresh (single-flight, чтобы параллельные 401 не запускали несколько обновлений) и повтор исходного запроса; если refresh не удался — срабатывает `setUnauthorizedHandler` и пользователь отправляется на `/login`.
- `authApi` получает методы `csrf()`, `refresh()`, `logout()`; `login` возвращает `{ accessToken, email }`.
- Выход: best-effort `POST /auth/logout`, затем очистка state — локальный выход происходит даже при недоступном gateway.

## Risks / Trade-offs

- **[Risk]** CSRF-токен лежит в читаемой cookie, а значит доступен при XSS. **Mitigation:** CSRF-токен не является credential и не даёт доступа к сессии без основного httpOnly refresh-токена; главная цель изменения — убрать долгоживущий credential из достижимого для JS места — достигается.
- **[Risk]** `SameSite=Strict` и `Secure` ломают работу при cross-site размещении API и по http. **Mitigation:** параметры cookie конфигурируемы; dev использует Vite-прокси (same-origin), прод должен отдавать API same-origin через reverse-proxy (Open Question).
- **[Risk]** Ротация refresh-токена в нескольких вкладках: устаревший токен в одной вкладке после обновления в другой ведёт к `401` и может разлогинить пользователя. **Mitigation:** single-flight refresh в рамках вкладки; окно гонки ограничено, приемлемо на текущем этапе.
- **[Risk]** Миграция `V2` должна выполняться на Postgres и H2 (тесты). **Mitigation:** избегать Postgres-специфичного синтаксиса в миграции; проверять тестами на H2.
- **[Risk]** Включение CSRF затрагивает проксируемые изменяющие запросы, а клиентский `ProxyRoutingFilter`-контракт по заголовкам остаётся прежним. **Mitigation:** обновить клиентский слой так, чтобы CSRF-заголовок добавлялся ко всем изменяющим запросам, и покрыть это тестами gateway и клиента.
- **[Risk]** Уменьшение access-token TTL до `PT15M` учащает refresh-запросы. **Mitigation:** значение конфигурируемо; при необходимости TTL можно вернуть без изменения спецификаций.

## Migration Plan

1. Gateway сначала: добавляется таблица refresh-токенов (Flyway `V2`), эндпоинты `/auth/csrf`, `/auth/refresh`, `/auth/logout`, cookie и CSRF. `/auth/login` продолжает возвращать `accessToken` в теле, поэтому старый клиент с `localStorage` продолжает работать во время раскатки.
2. Клиент вторым: переход на in-memory access-токен, восстановление через refresh-cookie, CSRF-заголовок, выход через gateway, удаление legacy-ключа `localStorage`.
3. Конфигурация перед запуском: задать `gateway.auth.refresh-token-ttl`, `gateway.auth.cookie.secure`, `gateway.auth.cookie.same-site`; в проде — HTTPS и same-origin reverse-proxy.
4. Откат: вернуть предыдущие версии gateway и клиента; таблица `gateway_refresh_tokens` может остаться неиспользуемой (удаление миграцией `V2` не требуется). Выданные refresh-cookie безвредны и истекают по TTL.

## Open Questions

- Продовая топология размещения (reverse-proxy same-origin, домены и точные `SameSite`/`domain` cookie) — уточняется при деплое и не меняет спецификации.
- Нужно ли при повторном использовании ротированного refresh-токена отзывать все сессии пользователя (reuse detection как реакция на кражу) — потенциальное отдельное изменение.
