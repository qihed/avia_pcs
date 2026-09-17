$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$ComposePath = Join-Path $PSScriptRoot "docker-compose.yml"
$EnvPath = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $EnvPath)) {
    throw ".env not found. Run .\init-env.ps1 and .\start-compose.ps1 first."
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
$pgDb = if ($e.ContainsKey("POSTGRES_DB")) { $e["POSTGRES_DB"] } else { "avia_booking" }
$pgUser = if ($e.ContainsKey("POSTGRES_USER")) { $e["POSTGRES_USER"] } else { "avia_app" }

Write-Host "Database: $pgDb (user $pgUser)" -ForegroundColor Cyan
Write-Host "Stop the running console app first: it keeps the schema locked." -ForegroundColor Yellow
$answer = Read-Host "Reset drops schema avia with ALL passengers, flights and bookings. Type RESET"
if ($answer -cne "RESET") {
    Write-Host "Cancelled" -ForegroundColor Yellow
    exit 1
}

# Схема удаляется целиком вместе с историей Flyway: при следующем запуске приложение
# заново применит V1 (схема) и V2 (демонстрационные данные). psql берётся из контейнера db.
& docker compose -f $ComposePath --env-file $EnvPath exec -T db psql -U $pgUser -d $pgDb -c "DROP SCHEMA IF EXISTS avia CASCADE"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Schema avia dropped." -ForegroundColor Green
Write-Host "Start the app again (.\run.ps1): Flyway recreates the schema and loads the demo data." -ForegroundColor Cyan
