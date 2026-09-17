$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$compose = Join-Path $root "docker-compose.yml"
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { $envFile = Join-Path $root ".env.example" }
$pattern = "AVIA_BOOKING_COMPOSE_V1|mvn -f|exec-maven-plugin|jansi.tmpdir|restart:|5432"

Write-Host "Compose path: $compose"
Write-Host "Env file:     $envFile"
Write-Host ""
Write-Host "Expected markers:" -ForegroundColor Cyan
Get-Content $compose | Select-String -Pattern $pattern

Write-Host ""
Write-Host "Rendered effective command:" -ForegroundColor Cyan
# Профиль app включается, чтобы увидеть команду запуска консоли; строки с паролями отфильтровываются.
docker compose -f $compose --env-file $envFile --profile app config |
    Select-String -Pattern "$pattern|jansi|image:" |
    Where-Object { $_.Line -notmatch "PASSWORD" }
