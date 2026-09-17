$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Консоль переключается на UTF-8 без BOM, иначе русское меню и ввод искажаются.
$utf8 = New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$env:MAVEN_OPTS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

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

# Порядок как в приложении: переменная окружения, затем .env, затем значение по умолчанию.
function Get-Setting([hashtable]$DotEnv, [string]$Name, [string]$Default) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ($value) { return $value }
    if ($DotEnv.ContainsKey($Name) -and $DotEnv[$Name]) { return $DotEnv[$Name] }
    return $Default
}

# .env содержит пароль БД; значения передаются только через окружение этого процесса.
$envPath = Join-Path $PSScriptRoot ".env"
$dotEnv = if (Test-Path $envPath) { Read-DotEnv $envPath } else { @{} }

$pgPort = Get-Setting $dotEnv "POSTGRES_PORT" "5432"
$pgDb = Get-Setting $dotEnv "POSTGRES_DB" "avia_booking"
$dbUrl = Get-Setting $dotEnv "DB_URL" "jdbc:postgresql://127.0.0.1:${pgPort}/${pgDb}?currentSchema=avia"

# DB_URL тоже идёт через окружение: cmd.exe в mvnw.cmd разобрал бы символ & из URL как разделитель команд.
$env:DB_URL = $dbUrl
$env:DB_USER = Get-Setting $dotEnv "DB_USER" (Get-Setting $dotEnv "POSTGRES_USER" "avia_app")
$env:DB_PASSWORD = Get-Setting $dotEnv "DB_PASSWORD" (Get-Setting $dotEnv "POSTGRES_PASSWORD" "")

if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "JDK 21 is required: install it and set JAVA_HOME or add java to PATH."
}

$mvn = Join-Path $PSScriptRoot "mvnw.cmd"
if (-not (Test-Path $mvn)) {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "Maven 3.9+ is required (or run exec:java from your IDE)."
    }
    $mvn = "mvn"
}

Write-Host "Avia Booking Console" -ForegroundColor Cyan
# URL, заданный вручную, может содержать учётные данные; такой адрес не печатается.
if ($dbUrl -match "password=") {
    Write-Host "PostgreSQL JDBC: (custom DB_URL with credentials is not printed)"
} else {
    Write-Host "PostgreSQL JDBC: $dbUrl"
}

# -q не смешивает вывод Maven с меню; exec:java сам не компилирует, поэтому нужен compile.
& $mvn -q compile exec:java
exit $LASTEXITCODE
