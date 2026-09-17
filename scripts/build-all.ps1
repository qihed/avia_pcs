$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    Push-Location console-app
    try {
        .\mvnw.cmd -B -ntp clean verify
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally { Pop-Location }
    python scripts\verify.py --build
    exit $LASTEXITCODE
} finally { Pop-Location }
