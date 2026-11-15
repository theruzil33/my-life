#!/usr/bin/env bash
#
# Поднимает контейнеры текущей рабочей копии (worktree) с её собственным
# .env: своим портом, своим compose-проектом и своей базой. Перед запуском
# автоматически вызывает scripts/dev-env.sh.
#
# Использование:
#   scripts/dev-up.sh                # собрать образы и поднять
#   scripts/dev-up.sh --build        # то же самое, аргументы уходят в compose
#   scripts/dev-up.sh up -d --build  # можно передать команду compose целиком
#
# Остановить: scripts/dev-down.sh (или docker compose down в этом каталоге).

set -euo pipefail

root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
"$root/scripts/dev-env.sh" --skip-npm

cd "$root"

if [ "$#" -eq 0 ]; then
    docker compose up -d --build
else
    docker compose "$@"
fi

if [ ! -f "$root/.env" ]; then
    echo "Нет .env — не могу определить WEB_PORT" >&2
    exit 1
fi

web_port=$(grep -E '^WEB_PORT=' "$root/.env" | tail -1 | cut -d= -f2)
echo
echo "Приложение: http://localhost:${web_port:-80}/"
echo "Логи:       docker compose logs -f (в $root)"
