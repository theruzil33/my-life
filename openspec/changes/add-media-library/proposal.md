## Why

Сейчас в хабе есть только «Трекер» и «Финансы», поэтому фильмы и сериалы, книги и игры приходится отслеживать где-то вне приложения. Нужен единый раздел, где видно, что ещё не начато, что уже в процессе и что завершено.

## What Changes

- Добавить новое приложение «Медиатека» с отдельной страницей `/media` и карточкой в хабе.
- Страница содержит три подвкладки: «Фильмы и сериалы», «Книги», «Игры»; открытая по умолчанию — «Фильмы и сериалы».
- Каждая запись медиатеки хранит название (обязательно), описание (необязательно) и статус: «Не начал» (`NOT_STARTED`), «В процессе» (`IN_PROGRESS`), «Завершил» (`COMPLETED`).
- Записи можно создавать, редактировать (название, описание, статус) и удалять; список каждой подвкладки показывает записи только своего вида и изолирован по пользователю.
- Добавить новый backend-сервис `backend/media` (Spring Boot, порт 8083, context-path `/media`, собственная таблица истории Flyway), хранящий записи в общей БД `my_life` в таблице `media_items`.
- Добавить в gateway маршрут префикса `/media` до сервиса `media` для аутентифицированных запросов с передачей идентификатора пользователя.
- Подключить новый сервис к инфраструктуре: nginx (SPA-маршрут `/media` и проксирование `/media/`), `docker-compose.yml`, `docker-compose.no-postgres.yml`, `.env.example`, readme, Gitea Actions (тесты и публикация образа).

## Capabilities

### New Capabilities

- `media-library`: поведение сервиса медиатеки — CRUD записей видов «фильм/сериал», «книга», «игра» с названием, описанием и статусом, изоляция по пользователю.
- `web-media`: страница «Медиатека» в веб-клиенте — три подвкладки, список записей своего вида, создание/редактирование/удаление, состояния загрузки, ошибки и пустого списка.

### Modified Capabilities

- `gateway-request-routing`: к конфигурации маршрутов добавляется префикс `/media`, сопоставленный с сервисом медиатеки; требование о составе маршрутов расширяется.
- `web-app-hub`: в сетку карточек добавляется активная карточка «Медиатека», открывающая `/media`.

## Impact

- Новый модуль: `backend/media` (pom.xml, Dockerfile, `application.properties`, миграции Flyway `media_*`, entity/repository/service/controller, фильтр идентификатора пользователя, обработчик ошибок, тесты, тестовые `application.properties` и H2).
- `backend/gateway`: `src/main/resources/application.properties` (маршрут `gateway.routes[2]`), тесты маршрутизации.
- `client/web`: `src/apps/apps.js`, `src/App.jsx`, новая `src/pages/MediaPage.jsx`, компоненты в `src/media/`, `src/api/mediaApi.js`, стили `src/App.css`, тесты в `test/pages` и `test/media`, `test/api`.
- Инфраструктура: `client/web/nginx.conf`, `docker-compose.yml`, `docker-compose.no-postgres.yml`, `.env.example`, `readme.md`, `.gitea/workflows/backend-tests.yml`, `.gitea/workflows/docker-publish.yml`.
- OpenSpec: новые capability `media-library` и `web-media`, дельты для `gateway-request-routing` и `web-app-hub`.
