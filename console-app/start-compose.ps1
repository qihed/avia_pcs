param(
    [int]$WaitTimeoutSeconds = 420
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$ComposePath = Join-Path $ProjectRoot "docker-compose.yml"
$EnvPath = Join-Path $ProjectRoot ".env"
$Marker = "AVIA_BOOKING_COMPOSE_V1"

Set-Location $ProjectRoot

if (-not (Test-Path $ComposePath)) {
    throw "docker-compose.yml not found: $ComposePath"
}

$composeText = Get-Content -LiteralPath $ComposePath -Raw
if ($composeText -notmatch $Marker) {
    throw "Wrong/old docker-compose.yml is active. Expected marker $Marker."
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker with the Compose plugin is required."
}

if (-not (Test-Path $EnvPath)) {
    Write-Host ".env not found. Creating it..." -ForegroundColor Yellow
    & (Join-Path $ProjectRoot "init-env.ps1")
}

Write-Host ""
Write-Host "Active Compose:" -ForegroundColor Cyan
Write-Host "  $ComposePath"
Write-Host "Profile marker: $Marker" -ForegroundColor Green
Write-Host ""

# Устаревшие контейнеры проекта удаляются, именованный volume с базой сохраняется.
Write-Host "Stopping stale containers (database volume is preserved)..." -ForegroundColor Cyan
& docker compose -f $ComposePath --env-file $EnvPath down --remove-orphans
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting fresh containers..." -ForegroundColor Cyan
# --wait ждёт только db: одноразовый connection-info завершается сам,
# и часть версий Compose считает такой выход ошибкой ожидания.
& docker compose -f $ComposePath --env-file $EnvPath up -d --force-recreate --wait --wait-timeout $WaitTimeoutSeconds db
if ($LASTEXITCODE -eq 0) {
    & docker compose -f $ComposePath --env-file $EnvPath up -d connection-info
}

if ($LASTEXITCODE -ne 0) {
    # Код ошибки запоминается: следующие команды диагностики перезапишут $LASTEXITCODE.
    $exitCode = $LASTEXITCODE
    Write-Host ""
    Write-Host "Startup failed. Effective database service:" -ForegroundColor Red
    # Строки с паролями отфильтровываются, чтобы секреты не попали в вывод.
    & docker compose -f $ComposePath --env-file $EnvPath config |
        Select-String -Pattern "image:|5432|restart:|pg_isready" |
        Where-Object { $_.Line -notmatch "PASSWORD" }
    Write-Host ""
    Write-Host "Database logs:" -ForegroundColor Yellow
    & docker compose -f $ComposePath --env-file $EnvPath logs --tail 160 db
    exit $exitCode
}

function Read-DotEnv([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith("#")) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($v.Length -ge 2 -and $v.StartsWith('"') -and $v.EndsWith('"')) {
            $v = $v.Substring(1, $v.Length - 2)
        }
        $values[$k] = $v
    }
    return $values
}

$e = Read-DotEnv $EnvPath
$pgPort = if ($e.ContainsKey("POSTGRES_PORT")) { $e["POSTGRES_PORT"] } else { "5432" }
$pgDb = if ($e.ContainsKey("POSTGRES_DB")) { $e["POSTGRES_DB"] } else { "avia_booking" }
$pgUser = if ($e.ContainsKey("POSTGRES_USER")) { $e["POSTGRES_USER"] } else { "avia_app" }

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " AVIA BOOKING DATABASE IS READY" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " PostgreSQL JDBC:  jdbc:postgresql://localhost:${pgPort}/${pgDb}?currentSchema=avia"
Write-Host " PostgreSQL user:  $pgUser"
Write-Host " Run the app:      .\run.ps1"
Write-Host "============================================================" -ForegroundColor Green
Write-Host "Passwords/secrets are not printed." -ForegroundColor DarkGray
Write-Host ""

& docker compose -f $ComposePath --env-file $EnvPath ps
