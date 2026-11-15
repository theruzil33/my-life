#!/usr/bin/env bash
#
# Готовит рабочую копию (обычно git worktree) к независимому запуску:
# создаёт собственный .env с уникальными портом, именем compose-проекта
# и именем базы, а также ставит зависимости веб-клиента.
#
# Скрипт идемпотентен: повторный запуск не меняет уже вычисленные значения
# (они выводятся из имени ветки) и не переустанавливает node_modules.
#
# Использование:
#   scripts/dev-env.sh              # подготовить текущую рабочую копию
#   scripts/dev-env.sh --skip-npm   # только .env, без npm ci
#
# В основном checkout'е (не worktree) скрипт ничего не меняет: там остаётся
# ваш собственный .env, чтобы не переименовать базу и не сменить порт.

set -euo pipefail

# set_env_value <файл> <ключ> <значение> — заменяет или добавляет KEY=value
set_env_value() {
    local file=$1
    local key=$2
    local value=$3
    local tmp
    tmp=$(mktemp)
    grep -v "^${key}=" "$file" > "$tmp" || true
    printf '%s=%s\n' "$key" "$value" >> "$tmp"
    mv "$tmp" "$file"
}

skip_npm=false
for arg in "$@"; do
    case "$arg" in
        --skip-npm) skip_npm=true ;;
        -h|--help)
            sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Неизвестный аргумент: $arg" >&2
            exit 1
            ;;
    esac
done

# Корень берём от расположения скрипта, а не от текущего каталога:
# скрипт всегда работает с той рабочей копией, в которой лежит.
root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
cd "$root"
primary=$(git worktree list --porcelain | awk '/^worktree /{print $2; exit}')

if [ "$root" = "$primary" ]; then
    echo "Основной checkout ($root): .env не трогаю — это ваша рабочая копия на master."
    exit 0
fi

branch=$(git rev-parse --abbrev-ref HEAD)
if [ "$branch" = "HEAD" ]; then
    branch="detached-$(git rev-parse --short HEAD)"
fi

slug=$(printf '%s' "$branch" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9' '-' | sed 's/--*/-/g; s/^-//; s/-$//' | cut -c1-24)
if [ -z "$slug" ]; then
    slug="worktree"
fi
db_suffix=$(printf '%s' "$slug" | tr '-' '_')

checksum=$(printf '%s' "$slug" | cksum | awk '{print $1}')
web_port=$((8090 + checksum % 50))
project_name="my-life-$slug"

env_file="$root/.env"

if [ ! -f "$env_file" ]; then
    if [ -f "$primary/.env" ]; then
        echo "Копирую секреты из основного checkout'а: $primary/.env"
        cp "$primary/.env" "$env_file"
    else
        echo "Беру шаблон .env.example и генерирую секреты"
        cp "$root/.env.example" "$env_file"
        postgres_password=$(openssl rand -base64 24 | tr -d '\n')
        jwt_secret=$(openssl rand -base64 48 | tr -d '\n')
        set_env_value "$env_file" POSTGRES_PASSWORD "$postgres_password"
        set_env_value "$env_file" GATEWAY_JWT_SECRET "$jwt_secret"
    fi
    chmod 600 "$env_file"
fi

set_env_value "$env_file" WEB_PORT "$web_port"
set_env_value "$env_file" POSTGRES_DB "my_life_$db_suffix"
set_env_value "$env_file" COMPOSE_PROJECT_NAME "$project_name"

if [ "$skip_npm" = false ] && [ ! -d "$root/client/web/node_modules" ]; then
    echo "Устанавливаю зависимости веб-клиента (npm ci)"
    (cd "$root/client/web" && npm ci)
fi

echo
echo "Готово: $root"
echo "  ветка:            $branch"
echo "  WEB_PORT:         $web_port"
echo "  POSTGRES_DB:      my_life_$db_suffix"
echo "  compose-проект:   $project_name"
echo "  адрес после up:   http://localhost:$web_port"
