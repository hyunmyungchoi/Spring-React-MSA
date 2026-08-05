[CmdletBinding()]
param(
    [string]$VmHost = "192.168.56.101",
    [int]$PostgresPort = 5432,
    [string]$PostgresDatabase = "postgres",
    [string]$PostgresUsername = "postgres",
    [string]$PostgresPassword = $env:SPRING_DATASOURCE_PASSWORD,
    [int]$RedisPort = 6379,
    [string]$RedisPassword = $env:SPRING_DATA_REDIS_PASSWORD,
    [string]$AdminLoginId = "admin",
    [string]$AdminEmail = "admin@springmsa.local",
    [string]$AdminUsername = "Administrator",
    [string]$AdminPassword = $env:ADMIN_BOOTSTRAP_PASSWORD,
    [string]$AdminAuditActor = "local-vm-bootstrap",
    [string]$AdminRequestId = ([guid]::NewGuid().ToString("N"))
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Require-Secret([string]$Value, [string]$Prompt) {
    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        return $Value
    }

    $secure = Read-Host $Prompt -AsSecureString
    return [System.Net.NetworkCredential]::new("", $secure).Password
}

function Invoke-ServiceGradle([string]$Service, [string]$Task) {
    $servicePath = Join-Path $repoRoot "BackEnd\$Service"
    Push-Location $servicePath
    try {
        & ".\gradlew.bat" --no-daemon $Task
        if ($LASTEXITCODE -ne 0) {
            throw "$Service Gradle task failed: $Task"
        }
    }
    finally {
        Pop-Location
    }
}

function Test-Redis([string]$HostName, [int]$Port, [string]$Password) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync($HostName, $Port)
        if (-not $connect.Wait([TimeSpan]::FromSeconds(3))) {
            throw "Redis connection timed out"
        }

        $stream = $client.GetStream()
        $payload = "*2`r`n`$4`r`nAUTH`r`n`$$($Password.Length)`r`n$Password`r`n*1`r`n`$4`r`nPING`r`n"
        $bytes = [System.Text.Encoding]::ASCII.GetBytes($payload)
        $stream.Write($bytes, 0, $bytes.Length)

        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
        $authResponse = $reader.ReadLine()
        $pingResponse = $reader.ReadLine()
        if ($authResponse -ne "+OK" -or $pingResponse -ne "+PONG") {
            throw "Redis authentication or PING failed: $authResponse $pingResponse"
        }
    }
    finally {
        $client.Dispose()
    }
}

$PostgresPassword = Require-Secret $PostgresPassword "PostgreSQL password"
$RedisPassword = Require-Secret $RedisPassword "Redis password"
$AdminPassword = Require-Secret $AdminPassword "Initial admin password (20-72 UTF-8 bytes)"

$previous = @{}
$variables = @(
    "SPRING_DATASOURCE_URL",
    "SPRING_DATASOURCE_USERNAME",
    "SPRING_DATASOURCE_PASSWORD",
    "SPRING_DATA_REDIS_HOST",
    "SPRING_DATA_REDIS_PORT",
    "SPRING_DATA_REDIS_PASSWORD",
    "ADMIN_BOOTSTRAP_LOGIN_ID",
    "ADMIN_BOOTSTRAP_EMAIL",
    "ADMIN_BOOTSTRAP_USERNAME",
    "ADMIN_BOOTSTRAP_PASSWORD",
    "ADMIN_BOOTSTRAP_AUDIT_ACTOR",
    "ADMIN_BOOTSTRAP_REQUEST_ID"
)

foreach ($name in $variables) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${VmHost}:${PostgresPort}/${PostgresDatabase}"
    $env:SPRING_DATASOURCE_USERNAME = $PostgresUsername
    $env:SPRING_DATASOURCE_PASSWORD = $PostgresPassword
    $env:SPRING_DATA_REDIS_HOST = $VmHost
    $env:SPRING_DATA_REDIS_PORT = "$RedisPort"
    $env:SPRING_DATA_REDIS_PASSWORD = $RedisPassword
    $env:ADMIN_BOOTSTRAP_LOGIN_ID = $AdminLoginId
    $env:ADMIN_BOOTSTRAP_EMAIL = $AdminEmail
    $env:ADMIN_BOOTSTRAP_USERNAME = $AdminUsername
    $env:ADMIN_BOOTSTRAP_PASSWORD = $AdminPassword
    $env:ADMIN_BOOTSTRAP_AUDIT_ACTOR = $AdminAuditActor
    $env:ADMIN_BOOTSTRAP_REQUEST_ID = $AdminRequestId

    Test-Redis $VmHost $RedisPort $RedisPassword
    Invoke-ServiceGradle "spring-user-service" "migrateDatabase"
    Invoke-ServiceGradle "spring-member-community-service" "migrateDatabase"
    Invoke-ServiceGradle "spring-member-stock-service" "migrateDatabase"
    Invoke-ServiceGradle "spring-member-bff-service" "migrateDatabase"
    Invoke-ServiceGradle "spring-user-service" "bootstrapAdmin"

    Write-Host "PostgreSQL schemas, tables, Redis authentication, and the admin account are ready."
}
finally {
    foreach ($name in $variables) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], "Process")
    }
    $PostgresPassword = $null
    $RedisPassword = $null
    $AdminPassword = $null
}
