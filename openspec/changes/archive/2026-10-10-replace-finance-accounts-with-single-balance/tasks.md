## 1. Backend: миграция и срез баланса

- [x] 1.1 Добавить миграцию `V7__replace_finance_accounts_with_balance.sql` (`DROP TABLE finance_accounts`; `CREATE TABLE finance_balance (user_id BIGINT PRIMARY KEY, starting_balance NUMERIC(19,2) NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)`) и проверить, что `FinanceApplicationTests` проходит на H2 в режиме PostgreSQL
- [x] 1.2 Создать сущность `balance/Balance` (поля `userId` как id, `startingBalance`, `updatedAt`), `BalanceRepository` и DTO (`BalanceRequest` с `startingBalance`, `BalanceResponse` с `startingBalance` и `amount`), проверив компиляцию
- [x] 1.3 Реализовать `BalanceService`: `get(userId)` возвращает `startingBalance` (0 при отсутствии строки) и `amount = startingBalance + Σ income − Σ expense` по всем операциям через `OperationRepository.sumAmountByUserIdAndCategoryType`; `update(userId, request)` делает upsert строки по `user_id`; валидация значения (`scale <= 2`, ноль и отрицательные допустимы) — проверить unit-тестами из группы 2
- [x] 1.4 Создать `balance/InvalidBalanceDataException` и `web/BalanceController` (`GET /balance`, `PUT /balance`) с идентификацией пользователя через `@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE)` — проверить web-тестами из группы 2
- [x] 1.5 Зарегистрировать `InvalidBalanceDataException` (`400`) в `GlobalExceptionHandler` и удалить обработчики и импорты исключений счетов (`AccountNotFoundException`, `InvalidAccountDataException`, `MainAccountDeletionException`) — проверить, что модуль компилируется

## 2. Backend: удаление счетов и unit-тесты

- [x] 2.1 Удалить пакет счетов (`account/Account`, `AccountRepository`, `AccountService`, исключения), DTO счетов и `web/AccountController`; убедиться, что в модуле не осталось ссылок на счета (`rg -i account backend/finance/src/main`) — проверить, что модуль компилируется
- [x] 2.2 Удалить тесты счетов (`AccountServiceTest`, `AccountRepositoryTest`, `AccountControllerTest`) и проверить, что тестовый набор собирается
- [x] 2.3 Написать `BalanceServiceTest`: расчёт баланса с начальным значением и операциями, `startingBalance` по умолчанию 0, отрицательный баланс, учёт будущих операций, пересчёт при изменении операции, валидация значения (нет значения, >2 знаков) — проверить, что тесты проходят
- [x] 2.4 Написать `BalanceRepositoryTest`: одна строка на пользователя, upsert обновляет существующую строку, изоляция по `user_id` — проверить, что тесты проходят
- [x] 2.5 Написать `BalanceControllerTest`: `GET`/`PUT /balance` для аутентифицированного пользователя, отсутствие заголовка `X-User-Id` даёт ошибку аутентификации, невалидное значение даёт `400`, изоляция данных между пользователями — проверить, что тесты проходят

## 3. Frontend: API и баланс в шапке

- [x] 3.1 Обновить `src/api/financeApi.js`: удалить `list`/`create`/`remove` счетов, добавить `getBalance(token)` (`GET /finances/balance`) и `updateBalance(token, startingBalance)` (`PUT /finances/balance`), сохранив передачу access- и CSRF-токенов — проверить тестами из группы 4
- [x] 3.2 Создать компонент отображения баланса и модальное окно изменения начального баланса (валидация: значение указано, число, ≤2 знаков; ноль и отрицательные допустимы) — проверить тестами из группы 4
- [x] 3.3 Обновить `src/pages/FinancesPage.jsx`: убрать вкладку «Счета», состав вкладок «Категории», «Операции», «Статистика», по умолчанию «Операции»; хранить баланс в состоянии страницы и показывать его в шапке на всех вкладках — проверить тестами из группы 4
- [x] 3.4 Передать во вкладку «Операции» колбэк, вызываемый после успешного сохранения/удаления операции, для перечитывания баланса в шапке — проверить тестами из группы 4

## 4. Frontend: удаление счетов и тесты

- [x] 4.1 Удалить `src/finances/AccountsTab.jsx`, `src/finances/AccountFormModal.jsx` и их тесты; обновить стили под шапку баланса и модальное окно начального баланса — проверить, что сборка (`npm run build`) проходит
- [x] 4.2 Обновить тесты `test/api/financeApi.test.js` под методы баланса и удалённые методы счетов — проверить, что тесты проходят
- [x] 4.3 Написать/обновить тесты страницы финансов: показ баланса на всех вкладках, вкладка по умолчанию «Операции», отсутствие вкладки «Счета», обновление баланса после операции — проверить, что тесты проходят

## 5. Интеграционная проверка

- [x] 5.1 Запустить бэкенд-тесты `backend/finance` (Maven) и убедиться, что все проходят
- [x] 5.2 Запустить тесты и линтер веб-клиента (`npm test`, `npm run lint`) и убедиться, что все проходят
- [x] 5.3 Проверить сквозной сценарий: пользователь задаёт начальный баланс, вводит операции прихода и ухода (в том числе будущие), видит пересчитанный баланс в шапке — проверить через `docker compose` (или локальный запуск) вручную
