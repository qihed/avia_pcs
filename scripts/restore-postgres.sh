#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 ]]; then echo "Usage: $0 backups/file.dump" >&2; exit 2; fi

# Путь к дампу вычисляется до смены каталога, чтобы работали и относительные пути.
file="$1"
[[ "$file" == /* ]] || file="$(pwd)/$file"
cd "$(dirname "$0")/.."
[[ -f "$file" ]] || { echo "File not found: $1" >&2; exit 2; }

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

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d ' ' -f 1
}

# Хеш сравнивается напрямую: путь внутри .sha256 может не совпадать, если дамп перенесли.
if [[ -f "$file.sha256" ]]; then
  expected="$(cut -d ' ' -f 1 "$file.sha256")"
  [[ "$(sha256_of "$file")" == "$expected" ]] || { echo "Checksum mismatch: $1" >&2; exit 1; }
  echo "Checksum OK: $1"
fi

pg_user="${POSTGRES_USER:-$(env_value POSTGRES_USER avia_app)}"
pg_db="${POSTGRES_DB:-$(env_value POSTGRES_DB avia_booking)}"
compose=(docker compose -f console-app/docker-compose.yml --env-file "$env_file")

read -r -p "Restore replaces database objects. Type RESTORE: " answer || answer=""
[[ "$answer" == "RESTORE" ]] || { echo "Cancelled"; exit 1; }
"${compose[@]}" exec -T db pg_restore -U "$pg_user" -d "$pg_db" --clean --if-exists < "$file"
printf 'Restored: %s\n' "$1"
