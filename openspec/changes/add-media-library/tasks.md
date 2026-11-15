## 1. Каркас сервиса media

- [x] 1.1 Создать модуль `backend/media` по образцу `backend/finance`: `pom.xml` (parent `spring-boot-starter-parent:4.1.1`, java 17, зависимости `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `spring-boot-starter-webmvc-test`, `h2`), `MediaApplication.java`, `Dockerfile` с `EXPOSE 8083`, `.dockerignore`, Maven wrapper; проверить `./mvnw -q -DskipTests package` в `backend/media`.
- [x] 1.2 Заполнить `backend/media/src/main/resources/application.properties` и `src/test/resources/application.properties`: порт 8083, context-path `/media`, `MEDIA_DB_*`, `media_flyway_schema_history`, `baseline-on-migrate=true`, `baseline-version=1`, `ddl-auto=validate`, H2 `MODE=PostgreSQL` в тестах; проверить, что `MediaApplicationTests` поднимает контекст.
- [x] 1.3 Добавить миграцию `backend/media/src/main/resources/db/migration/V2__create_media_items_table.sql`: таблица `media_items` (`id`, `user_id`, `type`, `title`, `description`, `status`, `created_at`, `updated_at`) с именованными ограничениями и индексом `idx_media_items_user_id_type`; проверить, что миграция применяется при старте тестового контекста.

## 2. Домен медиатеки

- [x] 2.1 Реализовать перечисления `MediaType` (`FILM`, `BOOK`, `GAME`) и `MediaStatus` (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`) и entity `MediaItem` (`@Table(name = "media_items")`, `userId` без JPA-связи, `@Enumerated(STRING)`, `Instant createdAt/updatedAt` из параметра `now`, protected no-arg конструктор, метод `update(...)`); проверить компиляцию модуля.
- [x] 2.2 Реализовать `MediaItemRepository` с `findByIdAndUserId` и `findByUserIdOrderByIdAsc` / `findByUserIdAndTypeOrderByIdAsc`; проверить `MediaItemRepositoryTest` (`@DataJpaTest` на H2): сохранение, фильтрация по виду, порядок по `id`, поиск по владельцу.
- [x] 2.3 Реализовать DTO-records `MediaItemRequest` (`type`, `title`, `description`, `status`), `MediaItemUpdateRequest` (`title`, `description`, `status`) и `MediaItemResponse` (`id`, `type`, `title`, `description`, `status`); проверить компиляцию и соответствие имён полей design.md.
- [x] 2.4 Реализовать `MediaItemService` с `create`, `list`, `update`, `delete`: `@Transactional` на записи и `readOnly` на чтение, `trim()` названия и описания, пустое описание → `null`, статус по умолчанию `NOT_STARTED`, лимиты 200/2000 символов, неизменяемость вида; проверить `MediaItemServiceTest` на создание, значения по умолчанию, нормализацию, отказы валидации, изменение и удаление.

## 3. HTTP-слой сервиса media

- [x] 3.1 Реализовать `UserIdFilter` (заголовок `X-User-Id`, атрибут `media.userId`, `401 {"message":"authentication required"}`), `ApiError`, `GlobalExceptionHandler` (`InvalidMediaDataException` → 400, `MediaItemNotFoundException` → 404, `HttpMessageNotReadableException` и `MethodArgumentTypeMismatchException` → 400) и исключения домена; проверить `UserIdFilterTest` и `GlobalExceptionHandlerTest`.
- [x] 3.2 Реализовать `MediaItemController`: `GET /items` (`type` необязателен, `200`), `POST /items` (`201`), `PUT /items/{id}` (`200`), `DELETE /items/{id}` (`204`), первый параметр — `@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE)`; проверить `MediaItemControllerTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`) на коды ответов, JSON-поля и изоляцию пользователей.

## 4. Тесты сервиса media

- [x] 4.1 Добавить unit-тесты бизнес-логики: `MediaItemServiceTest` покрывает границы длины названия (200/201) и описания (2000/2001), нормализацию пробелов, статус по умолчанию и отказы валидации; проверить `./mvnw test` в `backend/media`.
- [x] 4.2 Добавить проверки обработки ошибок и изоляции: `MediaItemControllerTest` и `MediaItemRepositoryTest` покрывают сценарии `404` на чужую и удалённую запись, `401` без заголовка пользователя и отсутствие чужих записей в списке; проверить `./mvnw test` и полное прохождение набора модуля.

## 5. Маршрутизация gateway

- [x] 5.1 Добавить в `backend/gateway/src/main/resources/application.properties` маршрут `gateway.routes[2].prefix=/media` и `target-base-url=${GATEWAY_MEDIA_BASE_URL:http://localhost:8083}`; проверить `GatewayPropertiesTest` и `ProxyRoutingTest` с новым префиксом (`/media/items` проксируется, идентификатор пользователя передаётся, `401` без токена).

## 6. Инфраструктура и конфигурация

- [x] 6.1 Добавить в `client/web/nginx.conf` точные `location = /media` и `location = /media/` с `try_files /index.html =404` и префиксный `location /media/` с `proxy_pass http://gateway:8080` в порядке, аналогичном `/tracker`; проверить `docker compose config` и ручной запрос `/media` к собранному web-образу.
- [x] 6.2 Добавить сервис `media` в `docker-compose.yml` и `docker-compose.no-postgres.yml` (build context `./backend/media`, `depends_on` с `postgres: service_healthy` и `gateway: service_healthy`, `MEDIA_DB_*`, `expose: 8083`, `extra_hosts` в варианте без postgres) и передать `GATEWAY_MEDIA_BASE_URL: http://media:8083` в gateway; проверить `docker compose config` для обоих файлов.
- [x] 6.3 Обновить `.env.example` и `readme.md`: описание сервиса `media`, маршрута `/media`, порта 8083, переменной `GATEWAY_MEDIA_BASE_URL`, порядка запуска и команды локального запуска; проверить, что перечисленные в readme шаги соответствуют compose-файлам.

## 7. CI

- [x] 7.1 Добавить `media` в матрицу сервисов `.gitea/workflows/backend-tests.yml` и образ `my-life-media` в список сборки `.gitea/workflows/docker-publish.yml`; проверить корректность YAML и запуск `./mvnw test` для всех четырёх сервисов локально.

## 8. Клиент: API и общие модули

- [x] 8.1 Реализовать `client/web/src/api/mediaApi.js` (`list(token, type)`, `create(token, payload)`, `update(token, id, payload)`, `remove(token, id)`) на общем `request` с путями `/media/items`; проверить `test/api/mediaApi.test.js` на метод, тело, query-параметр и заголовок `Authorization`.
- [x] 8.2 Реализовать `client/web/src/media/mediaOptions.js` с перечислением видов, порядком подвкладок, подписями статусов («Не начал», «В процессе», «Завершил») и текстами подвкладок; проверить unit-тестом соответствия подписей значениям API.

## 9. Клиент: страница медиатеки

- [x] 9.1 Реализовать `client/web/src/media/MediaTab.jsx`: загрузка списка вида через `mediaApi.list` с обработкой отмены, состояния загрузки, ошибки с повтором и пустого списка, отображение названия, описания и статуса, кнопки добавления, редактирования и удаления с подтверждением; проверить рендером на моке API в `test/media/MediaTab.test.jsx`.
- [x] 9.2 Реализовать смену статуса через `<select>` в списке: оптимистичное отображение нового значения с откатом и сообщением об ошибке при неуспехе; проверить тестом успешной и неуспешной смены статуса.
- [x] 9.3 Реализовать `client/web/src/media/MediaFormModal.jsx` для создания и редактирования (название, описание, статус; статус по умолчанию «Не начал»; вид не редактируется), с валидацией пустого названия и показом ошибки сервера без потери введённых значений; проверить тестами создания и редактирования.
- [x] 9.4 Реализовать `client/web/src/pages/MediaPage.jsx` с заголовком, ссылкой возврата и тремя подвкладками в порядке «Фильмы и сериалы», «Книги», «Игры», подвкладкой «Фильмы и сериалы» по умолчанию и сохранением данных при переключении; проверить тестом `test/pages/MediaPage.test.jsx`.
- [x] 9.5 Добавить стили страницы, подвкладок, списка и форм в `client/web/src/App.css`, переиспользуя существующие классы (`tracker-page`, `tracker-tabs`, `modal`, `auth-form`); проверить `npm run build` и визуальную согласованность с трекером.

## 10. Клиент: хаб и маршрутизация

- [x] 10.1 Добавить запись `{ id: 'media', title: 'Медиатека', description: 'Фильмы, книги и игры', path: '/media', enabled: true }` в `client/web/src/apps/apps.js` и маршрут `path="/media"` с `RequireAuth` в `client/web/src/App.jsx`; проверить `test/pages/HomePage.test.jsx` и `test/App.test.jsx` на карточку и переход.

## 11. Финальные проверки

- [x] 11.1 Прогнать полный набор проверок клиента: `npm run test:run`, `npm run lint`, `npm run build` в `client/web`; убедиться, что все тесты проходят, линтер не выдаёт ошибок, сборка успешна.
- [x] 11.2 Прогнать `./mvnw test` во всех четырёх модулях `backend/*` и `openspec validate add-media-library --strict`; убедиться, что наборы зелёные и дельта специй валидна.
