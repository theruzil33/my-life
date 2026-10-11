## Context

См. `proposal.md` — Why и `specs/` — требования. Текущее состояние `backend/finance`: вертикальный срез счетов (`account/Account`, таблица `finance_accounts`, признак `is_main`), категории (`category/Category` с `type` — `INCOME`/`EXPENSE`) и операции (`operation/Operation`, таблица `finance_operations` с `user_id`, `category_id`, `operation_date`, `amount`, без `account_id`). Баланс главного счёта считается на чтение в `AccountService` через агрегат `OperationRepository.sumAmountByUserIdAndCategoryType`. Учётные данные пользователя приходят из `UserIdFilter` (заголовок `X-User-Id`); ошибки маппит `GlobalExceptionHandler`. Стек: Spring Boot + JPA, прод — PostgreSQL (`ddl-auto=validate`), тесты — H2 в режиме PostgreSQL; Flyway `baseline-version=1`, последняя миграция `V6__add_is_main_to_finance_accounts.sql`; context-path сервиса `/finances`. Веб-клиент: `src/pages/FinancesPage.jsx` с вкладками, `src/finances/AccountsTab.jsx`, `src/finances/AccountFormModal.jsx`, `src/api/financeApi.js` (через `request()` из `src/api/client.js`).

## Goals / Non-Goals

**Goals:**

- Заменить сущность «счёт» единственным балансом: редактируемый начальный баланс + вычисляемый текущий баланс.
- Удалить код, API и UI счетов целиком; сохранить операции, категории и статистику без изменений.
- Отдавать в веб-клиент одно число баланса и дать его пересчитывать при изменении операций.

**Non-Goals:**

- Переводы между счетами, баланс по отдельным «кошелькам», валюта, история баланса.
- Изменение модели операций (операции остаются без привязки к счёту).
- Хранение текущего баланса: он всегда производный от операций.

## Decisions

1. **Схема: удалить `finance_accounts`, создать `finance_balance`.** Одна миграция `V7__replace_finance_accounts_with_balance.sql`:
   ```sql
   DROP TABLE finance_accounts;
   CREATE TABLE finance_balance (
       user_id BIGINT PRIMARY KEY,
       starting_balance NUMERIC(19, 2) NOT NULL,
       updated_at TIMESTAMP WITH TIME ZONE NOT NULL
   );
   ```
   `user_id` как первичный ключ гарантирует не более одной строки на пользователя. Альтернатива «оставить `finance_accounts` с одной строкой на пользователя» отклонена: остаются мёртвые колонки `name`/`is_main` и сбивающая семантика счёта. Альтернатива «общая таблица настроек пользователя» отклонена: других настроек финансов пока нет, добавили бы лишний уровень.

2. **Текущий баланс вычисляется на чтение и не хранится.** `BalanceService` переиспользует `OperationRepository.sumAmountByUserIdAndCategoryType` (агрегат сохраняется, а не удаляется): `balance = startingBalance + Σ income − Σ expense` по всем операциям пользователя, включая будущие даты (фильтра по дате нет). Начальный баланс = 0, если строки нет. Альтернатива «хранить баланс в строке» отклонена: он менялся бы при каждом upsert/удалении операции и рассинхронизировался бы.

3. **Изменение начального баланса — upsert по `user_id` в сервисе, а не DB-specific `ON CONFLICT`.** `BalanceService` находит строку пользователя и обновляет либо создаёт её (`findById`/`save`). Так поведение одинаково на PostgreSQL и H2. Валидация: значение обязательно, `BigDecimal`, `scale <= 2`; ноль и отрицательные допустимы.

4. **Контракт API — один ресурс «balance»:**
   - `GET /balance` → `{ "startingBalance": <число>, "amount": <число> }`.
   - `PUT /balance` — тело `{ "startingBalance": <число> }`; ответ — те же `startingBalance` и `amount`.
   Контекст сервиса `/finances` даёт клиенту пути `/finances/balance`. Альтернатива «отдельные `/settings` и `/balance`» отклонена: два эндпоинта на одно понятие. Эндпоинты счетов удаляются.

5. **Ошибки: одно новое исключение `InvalidBalanceDataException` (`400`).** Обработчики и исключения счетов (`AccountNotFoundException`, `InvalidAccountDataException`, `MainAccountDeletionException`) удаляются из `GlobalExceptionHandler` и из кода. `404`/`409` для баланса не нужны: ресурс одиночный и не удаляется.

6. **Веб-клиент: баланс в шапке, состояние баланса живёт в `FinancesPage`.** `FinancesPage` запрашивает баланс и хранит его; шапка показывает число на всех вкладках. Состав вкладок — «Категории», «Операции», «Статистика», по умолчанию «Операции»; `AccountsTab` и `AccountFormModal` удаляются. Новый компонент модалки изменения начального баланса переиспользует стили `auth-field`/`modal-*`. `financeApi` теряет методы счетов и получает `getBalance`/`updateBalance`.

7. **Обновление баланса после операций.** `FinancesPage` передаёт во вкладку «Операции» колбэк, который та вызывает после успешного сохранения/удаления операции, чтобы `FinancesPage` перечитал баланс. Без этого число в шапке оставалось бы устаревшим после правок на вкладке «Операции». Альтернатива «перечитывать баланс при каждом переключении вкладки» отклонена: на вкладке «Операции» шапка тоже должна обновляться сразу после ввода.

## Risks / Trade-offs

- **[Risk]** `DROP TABLE finance_accounts` необратимо удаляет данные счетов. **Mitigation:** подтверждено, что реальных пользователей нет; для отката миграция отката пересоздаёт пустую `finance_accounts` и удаляет `finance_balance`.
- **[Risk]** Баланс включает будущие операции, что может «уменьшать» число до наступления даты. **Mitigation:** осознанное решение, зафиксировано в `finance-balance`.
- **[Risk]** Баланс в шапке устаревает после правок операций. **Mitigation:** колбэк из вкладки «Операции» (решение 7) перечитывает баланс.
- **[Risk]** Обновление `GlobalExceptionHandler`/`AccountService` затрагивает компиляцию всего модуля при удалении исключений счетов. **Mitigation:** удаление кода счетов и ссылок на его исключения выполняется одним согласованным шагом (см. `tasks.md`).

## Migration Plan

1. Задеплоить `finance`: миграция `V7` (drop `finance_accounts`, create `finance_balance`), новый срез `balance`, удалённый срез счетов и связанные исключения/обработчики.
2. Задеплоить `web`: баланс в шапке, удалённые вкладка и модалка счетов, новая модалка начального баланса.

Откат: вернуть web без баланса; вернуть код счетов; откатить `V7` — пересоздать `finance_accounts` и удалить `finance_balance`. Данных для сохранения нет, потери при откате не критичны.

## Open Questions

Нет.
