#!/usr/bin/env bash
#
# Останавливает контейнеры текущей рабочей копии. Данные базы сохраняются:
# том принадлежит compose-проекту этой копии. Для удаления данных:
#   scripts/dev-down.sh -v
#
# Использование:
#   scripts/dev-down.sh        # остановить и удалить контейнеры
#   scripts/dev-down.sh -v     # заодно удалить том с данными БД

set -euo pipefail

root=$(cd -- "$(dirname -- "$0")/.." && pwd -P)
cd "$root"

docker compose down "$@"
