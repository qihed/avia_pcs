$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
# Без .env compose не разберёт ${VAR:?}, поэтому подставляется шаблон; down без -v сохраняет volume с базой.
$envFile = if (Test-Path ".env") { ".env" } else { ".env.example" }
& docker compose -f (Join-Path $PSScriptRoot "docker-compose.yml") --env-file $envFile down
exit $LASTEXITCODE
