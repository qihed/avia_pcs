#!/usr/bin/env bash
set -euo pipefail

# Полная локальная сборка: тесты модуля, затем структурная проверка проекта с --build.
cd "$(dirname "$0")/.."

(cd console-app && ./mvnw -B -ntp clean verify)
python3 scripts/verify.py --build
