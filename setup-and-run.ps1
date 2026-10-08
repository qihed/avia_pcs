# Setup and run avia_pcs. Run in PowerShell from the avia_pcs folder:
#   powershell -ExecutionPolicy Bypass -File .\setup-and-run.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot 'console-app')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'Docker not found. Installing Docker Desktop via winget...'
    winget install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements
    Write-Host 'Docker Desktop installed. Restart the PC (or sign out/in), start Docker Desktop, then run this script again.'
    exit 0
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Docker is installed but not running. Start Docker Desktop, wait until it is ready, then run this script again.'
    exit 1
}

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host '.env created from .env.example'
}

docker compose run --rm app
