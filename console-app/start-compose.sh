#!/usr/bin/env bash
set -euo pipefail

# Поднимает PostgreSQL в Docker и печатает параметры подключения.
# Использование: ./start-compose.sh [--wait-timeout 420]

cd "$(dirname "$0")"

usage="Usage: $0 [--wait-timeout 420]"
wait_timeout=420

while [[ $# -gt 0 ]]; do
  case "$1" in
    --wait-timeout)
      [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || { echo "Option --wait-timeout requires a number of seconds." >&2; exit 2; }
      wait_timeout="$2"
      shift 2
      ;;
    -h|--help)
      echo "$usage"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "$usage" >&2
      exit 2
      ;;
  esac
done

compose_file="$(pwd)/docker-compose.yml"
marker="AVIA_BOOKING_COMPOSE_V1"

[[ -f "$compose_file" ]] || { echo "docker-compose.yml not found: $compose_file" >&2; exit 1; }
if ! grep -q "$marker" "$compose_file"; then
  echo "Wrong/old docker-compose.yml is active. Expected marker $marker." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker with the Compose plugin is required." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  echo ".env not found. Creating it..."
  bash ./init-env.sh
fi

compose=(docker compose -f "$compose_file" --env-file .env)

echo
echo "Active Compose:"
echo "  $compose_file"
echo "Profile marker: $marker"
echo

# Устаревшие контейнеры проекта удаляются, именованный volume с базой сохраняется.
echo "Stopping stale containers (database volume is preserved)..."
"${compose[@]}" down --remove-orphans

echo "Starting fresh containers..."
# --wait ждёт только db: одноразовый connection-info завершается сам,
# и часть версий Compose считает такой выход ошибкой ожидания.
status=0
"${compose[@]}" up -d --force-recreate --wait --wait-timeout "$wait_timeout" db || status=$?
if [[ $status -eq 0 ]]; then
  "${compose[@]}" up -d connection-info || status=$?
fi

if [[ $status -ne 0 ]]; then
  echo
  echo "Startup failed. Effective database service:" >&2
  # Строки с паролями отфильтровываются, чтобы секреты не попали в вывод.
  rendered="$("${compose[@]}" config || true)"
  printf '%s\n' "$rendered" | grep -E 'image:|5432|restart:|pg_isready' | grep -vi 'password' || true
  echo
  echo "Database logs:" >&2
  "${compose[@]}" logs --tail 160 db || true
  exit "$status"
fi

# Из .env читаются только нужные ключи; пароль в переменные скрипта не попадает.
env_value() {
  local line
  line="$(grep -E "^[[:space:]]*$1=" .env | tail -n 1 || true)"
  line="${line#*=}"
  line="${line%$'\r'}"
  line="${line%\"}"
  line="${line#\"}"
  printf '%s' "${line:-$2}"
}

pg_port="$(env_value POSTGRES_PORT 5432)"
pg_db="$(env_value POSTGRES_DB avia_booking)"
pg_user="$(env_value POSTGRES_USER avia_app)"

echo
echo "============================================================"
echo " AVIA BOOKING DATABASE IS READY"
echo "============================================================"
echo " PostgreSQL JDBC:  jdbc:postgresql://localhost:${pg_port}/${pg_db}?currentSchema=avia"
echo " PostgreSQL user:  ${pg_user}"
echo " Run the app:      ./run.sh"
echo "============================================================"
echo "Passwords/secrets are not printed."
echo

"${compose[@]}" ps
