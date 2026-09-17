#!/usr/bin/env bash
set -euo pipefail

# Сбрасывает демонстрационные данные: схема avia удаляется целиком вместе с историей Flyway.
# При следующем запуске приложение заново применит V1 (схема) и V2 (демонстрационные данные).

cd "$(dirname "$0")"

[[ -f .env ]] || { echo ".env not found. Run ./init-env.sh and ./start-compose.sh first." >&2; exit 1; }

# Из .env читаются только имя базы и пользователь; пароль в переменные скрипта не попадает.
env_value() {
  local line
  line="$(grep -E "^[[:space:]]*$1=" .env | tail -n 1 || true)"
  line="${line#*=}"
  line="${line%$'\r'}"
  line="${line%\"}"
  line="${line#\"}"
  printf '%s' "${line:-$2}"
}

pg_db="$(env_value POSTGRES_DB avia_booking)"
pg_user="$(env_value POSTGRES_USER avia_app)"
compose=(docker compose -f docker-compose.yml --env-file .env)

echo "Database: ${pg_db} (user ${pg_user})"
echo "Stop the running console app first: it keeps the schema locked."
read -r -p "Reset drops schema avia with ALL passengers, flights and bookings. Type RESET: " answer || answer=""
[[ "$answer" == "RESET" ]] || { echo "Cancelled"; exit 1; }

# psql берётся из контейнера db: локальный клиент PostgreSQL не нужен.
"${compose[@]}" exec -T db psql -U "$pg_user" -d "$pg_db" -c "DROP SCHEMA IF EXISTS avia CASCADE"

echo
echo "Schema avia dropped."
echo "Start the app again (./run.sh): Flyway recreates the schema and loads the demo data."
