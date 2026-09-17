#!/usr/bin/env bash
set -euo pipefail

# Показывает активный compose-файл и ключевые строки итоговой конфигурации.
cd "$(dirname "$0")"

compose_file="$(pwd)/docker-compose.yml"
env_file=.env
[[ -f "$env_file" ]] || env_file=.env.example
pattern='AVIA_BOOKING_COMPOSE_V1|mvn -f|exec-maven-plugin|jansi.tmpdir|restart:|5432'

echo "Compose path: $compose_file"
echo "Env file:     $(pwd)/$env_file"
echo
echo "Expected markers:"
grep -nE "$pattern" "$compose_file" || true

echo
echo "Rendered effective command:"
# Профиль app включается, чтобы увидеть команду запуска консоли; строки с паролями отфильтровываются.
rendered="$(docker compose -f "$compose_file" --env-file "$env_file" --profile app config)"
printf '%s\n' "$rendered" | grep -E "$pattern|jansi|image:" | grep -vi 'password' || true
