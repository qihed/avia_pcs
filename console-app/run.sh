#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# .env содержит пароль БД; значения экспортируются только в окружение этого процесса.
if [[ -f .env ]]; then
  set -a
  . ./.env
  set +a
fi

db_url="${DB_URL:-jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-avia_booking}?currentSchema=avia}"
export DB_USER="${DB_USER:-${POSTGRES_USER:-avia_app}}"
export DB_PASSWORD="${DB_PASSWORD:-${POSTGRES_PASSWORD:-}}"

if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null 2>&1; then
  echo "JDK 21 is required: install it and set JAVA_HOME or add java to PATH." >&2
  exit 1
fi

mvn_cmd="./mvnw"
[[ -x "$mvn_cmd" ]] || mvn_cmd="mvn"
if ! command -v "$mvn_cmd" >/dev/null 2>&1; then
  echo "Maven 3.9+ is required (or run exec:java from your IDE)." >&2
  exit 1
fi

echo "Avia Booking Console"
# URL, заданный вручную, может содержать учётные данные; такой адрес не печатается.
if [[ "$db_url" == *[Pp]assword=* ]]; then
  echo "PostgreSQL JDBC: (custom DB_URL with credentials is not printed)"
else
  echo "PostgreSQL JDBC: $db_url"
fi

# -q не смешивает вывод Maven с меню; exec:java сам не компилирует, поэтому нужен compile.
exec "$mvn_cmd" -q -DDB_URL="$db_url" compile exec:java
