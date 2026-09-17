#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Без .env compose не разберёт ${VAR:?}, поэтому подставляется шаблон; down без -v сохраняет volume с базой.
env_file=.env
[[ -f "$env_file" ]] || env_file=.env.example
exec docker compose -f docker-compose.yml --env-file "$env_file" down
