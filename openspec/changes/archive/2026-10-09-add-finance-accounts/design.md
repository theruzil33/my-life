## Context

См. `proposal.md` — Why. Новый сервис `backend/finance` — это заготовка Spring Boot (Web MVC, Java 17, Spring Boot 4.1.1) без персистентности: только `FinanceApplication`, `application.properties` с `spring.application.name=finance` и тест контекста. Базы данных, Flyway, gateway-маршрута и Dockerfile у него нет.

Сервис `backend/tracker` уже задаёт рабочий образец: `pom.xml` с `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-flyway`, `flyway-database-postgresql` и тестовым `h2`; настройки БД и Flyway с отдельной таблицей истории и `baseline-version=1`; context-path `/tracker`, порт 8081; пакеты `habit`/`web`; `UserIdFilter` с заголовком `X-User-Id`; `GlobalExceptionHandler` и `ApiError`. Gateway проксирует по статической конфигурации `gateway.routes[i]` и добавляет `X-User-Id` из JWT. Веб-клиент имеет `src/api/client.js` (access/CSRF-токены) и страницы-приложения (например, `TrackerPage`), регистрируемые в `src/App.jsx`; главная показывает карточки из `src/apps/apps.js`.

## Goals / Non-Goals

**Goals:**

- Повторить проверенную архитектуру `tracker` для сервиса `finance`: те же зависимости, схема БД/Flyway, изоляция по `X-User-Id`, тот же формат `ApiError`.
- Дать минимальный, но полный вертикальный срез: сервис с БД → gateway-маршрут → страница веб-клиента со списком, добавлением и удалением.
- Сохранить единый origin через nginx и общий образец токенов на клиенте.

**Non-Goals:**

- Не реализовывать редактирование счёта, операции/транзакции, категории, валюту и пересчёт баланса.
- Не моделировать валюту: сумма — безвалютное число с двумя знаками после запятой.
- Не менять существующие контракты tracker, аутентификацию и безопасность gateway.
- Не вводить общие библиотеки/DTO между сервисами.

## Decisions

1. **Сервис `finance` копирует структуру `tracker`, а не переиспользует его код.** Отдельный сервис с собственными `account`/`web`-пакетами, своим `UserIdFilter` и `GlobalExceptionHandler`. Альтернатива — общая библиотека для `ApiError`/фильтра — отклонена: репозиторий пока не использует общие модули, а дублирование двух небольших классов дешевле лишней связанности.

2. **Сумма — `BigDecimal` со scale 2 без валюты.** В сущности `Account` поле `initialAmount` (`precision = 19, scale = 2`), в БД `NUMERIC(19,2)`. Начальная сумма MUST быть указана; значение с более чем двумя знаками после запятой отклоняется ошибкой валидации (нормализация через `setScale` не применяется, чтобы не скрывать ошибку ввода). Знак не ограничивается: нулевые и отрицательные значения допустимы (отрицательный «начальный баланс» трактуется как долг). Альтернатива — целые минорные единицы — отклонена пользователем.

3. **Только три операции: create, list, delete.** Отдельные эндпоинты `POST /accounts`, `GET /accounts`, `DELETE /accounts/{id}`. Эндпоинт изменения отсутствует, поэтому контракт не имеет `PUT`. Удаление выполняется через `findByIdAndUserId`, чтобы чужой/несуществующий счёт давал `404`.

4. **Изоляция по `X-User-Id`, как в tracker.** `UserIdFilter` читает заголовок, ставит атрибут `finance.userId`; при отсутствии — `401`. Сервис принимает `userId` параметром и ищет счета по `userId`. Контроллер читает `@RequestAttribute`. Gateway уже проставляет заголовок для защищённых маршрутов, поэтому сервис не проверяет JWT.

5. **Инфраструктура.** `application.properties`: URL/логин/пароль БД из переменных `FINANCE_DB_*` с дефолтами на `localhost:5432/my_life`, `ddl-auto=validate`, Flyway с таблицей `finance_flyway_schema_history`, `baseline-on-migrate=true`, `baseline-version=1`, `server.port=8082`, `server.servlet.context-path=/finances`. Первая миграция — `V2__create_finance_accounts_table.sql` (нумерация с 2, так как gateway создаёт схему и baseline ставится на версию 1). В `docker-compose.yml` добавляется сервис `finance` (build `./backend/finance`, `depends_on` postgres healthy и gateway healthy, `expose 8082`); в gateway — переменная `GATEWAY_FINANCE_BASE_URL` и маршрут `gateway.routes[1].prefix=/finances`; в `.env.example` и `readme.md` — новые переменные и сервис. `Dockerfile` копируется по образцу tracker (порт 8082).

6. **Маршрутизация nginx.** SPA-маршрут `/finances` должен отдаваться клиентом, а API-запросы `/finances/*` — проксироваться в gateway. Добавляются точные `location = /finances` и `location = /finances/` (`try_files /index.html`) и префиксный `location /finances/` (`proxy_pass http://gateway:8080`) по образцу `/tracker`.

7. **Веб-клиент следует образцу трекера.** Новый `src/api/financeApi.js` использует общий `request` из `src/api/client.js` (`requiresAuth: true`, CSRF для POST/DELETE). Новая `src/pages/FinancesPage.jsx` с состоянием списка, загрузки, ошибок и модальным окном добавления; удаление — с подтверждением, как в `ActivityListTab`. Карточка «Финансы» в `src/apps/apps.js` становится `enabled: true` с `path: '/finances'`; в `src/App.jsx` добавляется защищённый маршрут. Стили — в `src/App.css`.

8. **Формат обмена.** Тело создания — `{ "name": string, "initialAmount": number }`, ответ счёта — `{ "id": number, "name": string, "initialAmount": number }`. Список — массив таких объектов. Ошибки — `{ "message": string }` (совместимо с `ApiError`).

## Risks / Trade-offs

- **[Risk]** Числа с плавающей точкой на клиенте и `BigDecimal` на сервере могут расходиться при вводе. **Mitigation:** поля суммы — `input type="number" step="0.01"`; сервер — источник истины и отклоняет суммы с более чем двумя знаками после запятой; клиент показывает ответ сервера после создания.
- **[Risk]** Дублирование кода (`UserIdFilter`, `GlobalExceptionHandler`, `ApiError`) между `tracker` и `finance`. **Mitigation:** осознанный компромисс ради простоты; при появлении третьего сервиса можно вынести общий модуль.
- **[Risk]** Flyway baseline и порядок запуска сервисов: `finance` пишет в общую БД и должен стартовать после gateway. **Mitigation:** `baseline-on-migrate=true` и `depends_on` gateway healthy в `docker-compose.yml`, как у tracker.
- **[Risk]** SPA-маршрут `/finances` без точных `location` в nginx уйдёт в API и вернёт 404. **Mitigation:** точные `location =` для `/finances` и `/finances/` и префиксный проксирующий `location /finances/`, по образцу `/tracker`.
- **[Risk]** Маршрут `/finances` в gateway мог быть перехвачен как неизвестный префикс. **Mitigation:** маршрут добавляется в `application.properties` gateway; наличие маршрута проверяется тестом маршрутизации.

## Migration Plan

1. Задеплоить `gateway` с маршрутом `/finances` и `GATEWAY_FINANCE_BASE_URL` (без него запросы к `/finances` дают `404`, существующее поведение не меняется).
2. Задеплоить `finance` (миграция `V2__create_finance_accounts_table.sql` создаёт `finance_accounts`); убедиться, что `finance` стартует после `gateway`.
3. Задеплоить `web` с активной карточкой «Финансы», страницей и nginx-маршрутом.

Откат: убрать сервис `finance` и маршрут `/finances` из gateway/nginx и вернуть карточку «Финансы» в неактивное состояние; таблица `finance_accounts` остаётся в БД и может быть удалена вручную.

## Open Questions

Нет.
