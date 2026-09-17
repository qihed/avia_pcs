#!/usr/bin/env bash
set -euo pipefail

# Резервная копия базы из контейнера db: backups/avia_booking_<UTC>.dump и файл .sha256 рядом.
cd "$(dirname "$0")/.."

env_file="console-app/.env"
[[ -f "$env_file" ]] || { echo "File not found: $env_file (run console-app/init-env.sh first)" >&2; exit 2; }

# Из .env читаются только имя базы и пользователь; пароль в переменные скрипта не попадает.
env_value() {
  local line
  line="$(grep -E "^[[:space:]]*$1=" "$env_file" | tail -n 1 || true)"
  line="${line#*=}"
  line="${line%$'\r'}"
  line="${line%\"}"
  line="${line#\"}"
  printf '%s' "${line:-$2}"
}

pg_user="${POSTGRES_USER:-$(env_value POSTGRES_USER avia_app)}"
pg_db="${POSTGRES_DB:-$(env_value POSTGRES_DB avia_booking)}"
compose=(docker compose -f console-app/docker-compose.yml --env-file "$env_file")

mkdir -p backups
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="backups/avia_booking_${stamp}.dump"
# Custom format supports selective restore and compression.
if ! "${compose[@]}" exec -T db pg_dump -U "$pg_user" -d "$pg_db" -Fc > "$file"; then
  rm -f "$file"
  echo "Backup failed. Is the database running (console-app/start-compose.sh)?" >&2
  exit 1
fi
if command -v sha256sum >/dev/null 2>&1; then sha256sum "$file" > "$file.sha256"; else shasum -a 256 "$file" > "$file.sha256"; fi
printf 'Backup: %s\n' "$file"
