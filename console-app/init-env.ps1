param(
    [int]$PostgresPort = 5432,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$EnvPath = Join-Path $PSScriptRoot ".env"
$Existed = Test-Path $EnvPath

if ($Existed -and -not $Force) {
    throw ".env already exists. Use -Force to replace it."
}

if ($PostgresPort -lt 1 -or $PostgresPort -gt 65535) {
    throw "PostgresPort must be between 1 and 65535."
}

function New-RandomBytes([int]$Length) {
    $bytes = New-Object byte[] $Length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return $bytes
}

function New-Base64Secret([int]$Length) {
    return [Convert]::ToBase64String((New-RandomBytes $Length))
}

# Пароль БД создаётся криптографически стойким генератором.
# 36 байт дают 48 символов Base64 без '=', поэтому значение не нужно экранировать.
$DbPassword = New-Base64Secret 36

# Текст файла только ASCII: Windows PowerShell 5.1 читает скрипт без BOM в кодировке ANSI.
$content = @"
# Local AVIA-BOOKING settings created by init-env.ps1. Do not commit or share this file.
POSTGRES_DB=avia_booking
POSTGRES_USER=avia_app
POSTGRES_PASSWORD=$DbPassword
POSTGRES_PORT=$PostgresPort
DEMO_DATA=true
EXPORT_DIR=exports
APP_TIME_ZONE=Europe/Moscow

"@

# UTF-8 без BOM: иначе docker compose и приложение прочитают BOM как часть первого ключа.
[IO.File]::WriteAllText($EnvPath, $content, (New-Object Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Created: $EnvPath" -ForegroundColor Green
Write-Host "A random PostgreSQL password was generated and saved only to .env." -ForegroundColor Cyan
Write-Host ""
Write-Host "Connection addresses:" -ForegroundColor Cyan
Write-Host "  PostgreSQL JDBC:  jdbc:postgresql://localhost:$PostgresPort/avia_booking?currentSchema=avia"
Write-Host "  PostgreSQL user:  avia_app"
Write-Host ""
if ($Existed) {
    Write-Host "An existing database volume keeps its old password: restore the previous .env or run 'docker compose down -v' (deletes all data)." -ForegroundColor Yellow
}
Write-Host "Next step: .\start-compose.ps1" -ForegroundColor Cyan
Write-Host "Do not commit or send the .env file to anyone." -ForegroundColor Red
