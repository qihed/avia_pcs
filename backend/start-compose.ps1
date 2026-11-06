# Запуск PostgreSQL и backend через Docker Compose. Запускать из корня репозитория:
#   powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path ".env")) {
    Write-Error "Не найден .env в корне проекта. Скопируйте .env.example в .env и задайте значения."
}

docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait

Write-Host "Web / API:  http://localhost:8080/"
Write-Host "Swagger:    http://localhost:8080/swagger-ui.html"
Write-Host "Health:     http://localhost:8080/actuator/health"
