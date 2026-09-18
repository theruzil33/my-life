# My Life

Приложение для личных трекеров (привычки и т.д.), состоящее из:

- `backend/gateway` — Spring Boot API Gateway: аутентификация (JWT + refresh-cookie) и проксирование запросов к сервисам (порт 8080).
- `backend/tracker` — Spring Boot сервис трекера привычек (порт 8081, context-path `/tracker`).
- `client/web` — SPA на React + Vite, раздаётся через nginx (порт 80).
- `postgres` — общая база данных `my_life`.

## Архитектура при деплое

```
Браузер → web (nginx :80)
            ├── /              → статика SPA
            ├── /auth/*        → gateway:8080
            └── /tracker/*     → gateway:8080 → tracker:8081
postgres используется сервисами gateway и tracker
```

Наружу открывается только `web` (`WEB_PORT`). `gateway`, `tracker` и `postgres`
доступны только внутри docker-сети.

## Запуск на сервере

### Требования

- Docker Engine 24+
- Docker Compose v2 (`docker compose`)

### Шаги

1. Склонируйте репозиторий на сервер и перейдите в корень проекта.

2. Создайте файл окружения и заполните значения:

   ```bash
   cp .env.example .env
   nano .env
   ```

   Обязательно задайте:

   - `POSTGRES_PASSWORD` — пароль базы данных;
   - `GATEWAY_JWT_SECRET` — минимум 32 байта, сгенерировать:
     `openssl rand -base64 48`.

3. Соберите и запустите контейнеры:

   ```bash
   docker compose up -d --build
   ```

4. Проверьте статус:

   ```bash
   docker compose ps
   docker compose logs -f
   ```

5. Приложение доступно по адресу `http://<IP-сервера>:<WEB_PORT>/`
   (`WEB_PORT` по умолчанию `80`).

### Обновление версии

```bash
git pull
docker compose up -d --build
```

### Остановка

```bash
docker compose down          # остановить
docker compose down -v       # остановить и удалить данные БД (volume postgres-data)
```

## Публикация Docker-образов (Gitea Actions)

Workflow `.gitea/workflows/docker-publish.yml` собирает и публикует образы в
Docker Hub при пуше тега вида `v<число>.<число>.<число>`, например `v1.1.1`.
Теги, не подходящие под маску, workflow игнорирует.

Перед использованием добавьте в репозитории Gitea
(**Settings → Actions → Secrets**) два секрета:

| Секрет | Описание |
| --- | --- |
| `DOCKERHUB_USERNAME` | Логин Docker Hub |
| `DOCKERHUB_TOKEN` | [Access Token](https://hub.docker.com/settings/security) Docker Hub (не пароль) |

Публикуются три образа:

- `<DOCKERHUB_USERNAME>/my-life-gateway:<тег>`
- `<DOCKERHUB_USERNAME>/my-life-tracker:<тег>`
- `<DOCKERHUB_USERNAME>/my-life-web:<тег>`

Запуск публикации:

```bash
git tag v1.1.1
git push origin v1.1.1
```

Требования к runner: доступ к Docker (docker-режим или смонтированный
`/var/run/docker.sock`) и доступ в интернет для `docker push`.

## Переменные окружения

Все переменные задаются в файле `.env` в корне проекта (см. `.env.example`).

| Переменная | Сервис | Обязательна | По умолчанию | Описание |
| --- | --- | --- | --- | --- |
| `POSTGRES_DB` | postgres | нет | `my_life` | Имя базы данных |
| `POSTGRES_USER` | postgres | нет | `postgres` | Пользователь БД |
| `POSTGRES_PASSWORD` | postgres | **да** | — | Пароль БД |
| `GATEWAY_JWT_SECRET` | gateway | **да** | — | Секрет подписи JWT, ≥32 байт |
| `GATEWAY_JWT_ACCESS_TOKEN_TTL` | gateway | нет | `PT15M` | Время жизни access-токена |
| `GATEWAY_AUTH_REFRESH_TOKEN_TTL` | gateway | нет | `P7D` | Время жизни refresh-токена |
| `GATEWAY_AUTH_COOKIE_SECURE` | gateway | нет | `false` | Отдавать refresh-cookie только по HTTPS |
| `GATEWAY_AUTH_COOKIE_SAME_SITE` | gateway | нет | `Lax` | SameSite для refresh-cookie |
| `TRACKER_TIME_ZONE` | tracker | нет | `Europe/Moscow` | Часовой пояс трекера |
| `WEB_PORT` | web | нет | `80` | Порт сайта на сервере |
| `VITE_API_BASE_URL` | web (build) | нет | пусто | Базовый URL API (см. ниже) |

### Про HTTPS и cookie

Если сайт открывается по HTTPS, поставьте:

```
GATEWAY_AUTH_COOKIE_SECURE=true
GATEWAY_AUTH_COOKIE_SAME_SITE=Lax
```

Если сайт открывается по HTTP (например, на этапе первичной настройки),
оставьте `GATEWAY_AUTH_COOKIE_SECURE=false`, иначе браузер не сохранит
refresh-cookie и вход не будет работать.

`VITE_API_BASE_URL` нужен, только если фронтенд и API расположены на разных
origin. При штатной схеме (nginx проксирует `/auth` и `/tracker`) оставьте его
пустым. Учтите: это параметр сборки, после изменения нужен
`docker compose up -d --build web`.

## Особенности

- `gateway` и `tracker` используют одну базу `my_life`, но разные таблицы истории
  Flyway. `tracker` выполняет baseline поверх схемы, созданной `gateway`, поэтому
  в `docker-compose.yml` он дожидается healthcheck `gateway`. Менять порядок
  запуска этих сервисов не нужно.
- Наружу открыт только `web`; `gateway`, `tracker` и `postgres` доступны только
  внутри docker-сети.
- Данные PostgreSQL хранятся в volume `postgres-data` и переживают
  `docker compose down` (удаляются только с флагом `-v`).

## Локальная разработка (без Docker)

Для локального запуска удобно поднять только базу из compose (нужен файл `.env`,
см. выше). Чтобы совпасть с дефолтами бэкендов, укажите в `.env`
`POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=postgres`, `POSTGRES_DB=my_life`:

```bash
# база данных
docker compose up -d postgres

# gateway (по умолчанию :8080)
cd backend/gateway && ./mvnw spring-boot:run

# tracker (по умолчанию :8081)
cd backend/tracker && ./mvnw spring-boot:run

# frontend (Vite dev server с проксированием /auth и /tracker на gateway)
cd client/web && npm ci && npm run dev
```
