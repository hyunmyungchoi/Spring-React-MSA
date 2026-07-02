param(
    [switch]$NoCache,
    [switch]$SkipUp
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$backendRoot = Join-Path $root "BackEnd"
$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"

$backendServices = @(
    "spring-user-service",
    "spring-member-community-service",
    "spring-member-stock-service",
    "spring-security-authorization-server",
    "spring-member-bff-service",
    "spring-admin-bff-service",
    "spring-member-gateway",
    "spring-admin-gateway"
)

foreach ($service in $backendServices) {
    $servicePath = Join-Path $backendRoot $service

    Write-Host "Building jar: $service"
    Push-Location $servicePath
    try {
        & .\gradlew.bat clean bootJar
    }
    finally {
        Pop-Location
    }
}

$buildArgs = @("compose", "-f", $composeFile, "build")

if ($NoCache) {
    $buildArgs += "--no-cache"
}

Write-Host "Building Docker images"
& docker @buildArgs

if (-not $SkipUp) {
    Write-Host "Recreating Docker Compose services"
    & docker compose -f $composeFile up -d --force-recreate --no-build
}
