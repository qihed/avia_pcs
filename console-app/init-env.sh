#!/usr/bin/env bash
set -euo pipefail

# Создаёт console-app/.env со случайным паролем PostgreSQL.
# Использование: ./init-env.sh [--port 5432] [--force]

cd "$(dirname "$0")"

usage="Usage: $0 [--port 5432] [--force]"
port=5432
force=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      [[ $# -ge 2 ]] || { echo "Option --port requires a value." >&2; exit 2; }
      port="$2"
      shift 2
      ;;
    --force)
      force=true
      shift
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

if ! [[ "$port" =~ ^[0-9]{1,5}$ ]] || (( 10#$port < 1 || 10#$port > 65535 )); then
  echo "Port must be a number between 1 and 65535." >&2
  exit 2
fi
port=$((10#$port))

existed=false
if [[ -f .env ]]; then
  existed=true
  if [[ "$force" != true ]]; then
    echo ".env already exists. Use --force to replace it." >&2
    exit 1
  fi
fi

# Пароль БД создаётся криптографически стойким генератором.
# 36 байт дают 48 символов Base64 без '=', поэтому значение не нужно экранировать.
if command -v openssl >/dev/null 2>&1; then
  db_password="$(openssl rand -base64 36 | tr -d '\n')"
else
  db_password="$(head -c 36 /dev/urandom | base64 | tr -d '\n')"
fi
if (( ${#db_password} < 48 )); then
  echo "Failed to generate a random password." >&2
  exit 1
fi

# Файл сразу создаётся с правами 600: пароль доступен только владельцу.
umask 077
cat > .env <<EOF
# Local AVIA-BOOKING settings created by init-env.sh. Do not commit or share this file.
POSTGRES_DB=avia_booking
POSTGRES_USER=avia_app
POSTGRES_PASSWORD=${db_password}
POSTGRES_PORT=${port}
DEMO_DATA=true
EXPORT_DIR=exports
APP_TIME_ZONE=Europe/Moscow
EOF
chmod 600 .env
unset db_password

echo
echo "Created: $(pwd)/.env"
echo "A random PostgreSQL password was generated and saved only to .env."
echo
echo "Connection addresses:"
echo "  PostgreSQL JDBC:  jdbc:postgresql://localhost:${port}/avia_booking?currentSchema=avia"
echo "  PostgreSQL user:  avia_app"
echo
if [[ "$existed" == true ]]; then
  echo "An existing database volume keeps its old password: restore the previous .env or run 'docker compose down -v' (deletes all data)."
fi
echo "Next step: ./start-compose.sh"
echo "Do not commit or send the .env file to anyone."
