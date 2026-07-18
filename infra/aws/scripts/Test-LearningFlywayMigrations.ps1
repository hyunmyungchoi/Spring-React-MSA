[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9]{12}$')]
    [string]$ExpectedAccountId,

    [string]$Region = "ap-northeast-2",
    [string]$TerraformDirectory = (Join-Path $PSScriptRoot "..\terraform")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-AwsCli {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $output = & aws @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI command failed."
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-TerraformOutput {
    param(
        [Parameter(Mandatory)][string]$Name,
        [switch]$Json
    )

    $arguments = @("-chdir=$TerraformDirectory", "output", $(if ($Json) { "-json" } else { "-raw" }), $Name)
    $output = & terraform @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read Terraform output '$Name'."
    }
    return ($output -join [Environment]::NewLine).Trim()
}

$identity = (Invoke-AwsCli -Arguments @("sts", "get-caller-identity", "--output", "json")) | ConvertFrom-Json
if ($identity.Account -ne $ExpectedAccountId) {
    throw "AWS account mismatch. Expected $ExpectedAccountId but authenticated to $($identity.Account)."
}

$cluster = Get-TerraformOutput -Name "ecs_cluster_name"
$capacityProvider = Get-TerraformOutput -Name "ecs_capacity_provider_name"
$securityGroup = Get-TerraformOutput -Name "ecs_security_group_id"
$subnets = (Get-TerraformOutput -Name "private_app_subnet_ids" -Json) | ConvertFrom-Json
$taskDefinition = Get-TerraformOutput -Name "database_bootstrap_task_definition_arn"
$logGroup = Get-TerraformOutput -Name "database_bootstrap_log_group_name"

if ($subnets.Count -ne 2) {
    throw "Expected exactly two Private App subnets."
}

$verificationCommand = @'
set -eu
export PGHOST="$DB_ADDRESS"
export PGPORT="$DB_PORT"
export PGDATABASE="$DB_NAME"
export PGUSER="$DB_MASTER_USERNAME"
export PGPASSWORD="$DB_MASTER_PASSWORD"
export PGSSLMODE=require
psql --no-password --tuples-only --no-align --set=ON_ERROR_STOP=1 <<'SQL'
SELECT 'SAFE_ROLES=' || count(*) FROM pg_roles WHERE rolname IN ('user_service_app','member_bff_app','stock_service_app') AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls;
SELECT 'SCHEMAS=' || count(*) FROM information_schema.schemata WHERE schema_name IN ('user_service','member_bff','stock_service');
SELECT 'OWN_PRIVILEGE_PAIRS=' || count(*) FROM (VALUES ('user_service_app','user_service'),('member_bff_app','member_bff'),('stock_service_app','stock_service')) AS expected(role_name,schema_name) WHERE has_schema_privilege(role_name,schema_name,'USAGE') AND has_schema_privilege(role_name,schema_name,'CREATE');
SELECT 'CROSS_PRIVILEGES=' || count(*) FROM (VALUES ('user_service_app'),('member_bff_app'),('stock_service_app')) AS roles(role_name) CROSS JOIN (VALUES ('user_service'),('member_bff'),('stock_service')) AS schemas(schema_name) WHERE replace(role_name,'_app','') <> schema_name AND (has_schema_privilege(role_name,schema_name,'USAGE') OR has_schema_privilege(role_name,schema_name,'CREATE'));
SELECT 'MIGRATION_HISTORIES=' || count(*) FROM (VALUES ('user_service.flyway_schema_history'),('member_bff.flyway_schema_history'),('stock_service.flyway_schema_history')) AS expected(table_name) WHERE to_regclass(table_name) IS NOT NULL;
SELECT 'EXPECTED_TABLES=' || count(*) FROM (VALUES ('user_service.users'),('user_service.user_roles'),('member_bff.chat_rooms'),('member_bff.chat_messages'),('stock_service.stock_watch_items')) AS expected(table_name) WHERE to_regclass(table_name) IS NOT NULL;
SELECT 'APPLICATION_TABLES=' || count(*) FROM information_schema.tables WHERE table_schema IN ('user_service','member_bff','stock_service') AND table_name <> 'flyway_schema_history';
SELECT 'EXPECTED_TABLE_OWNERS=' || count(*) FROM (VALUES ('user_service','users','user_service_app'),('user_service','user_roles','user_service_app'),('member_bff','chat_rooms','member_bff_app'),('member_bff','chat_messages','member_bff_app'),('stock_service','stock_watch_items','stock_service_app')) AS expected(schema_name,table_name,owner_name) JOIN pg_tables actual ON actual.schemaname=expected.schema_name AND actual.tablename=expected.table_name AND actual.tableowner=expected.owner_name;
SELECT 'MIGRATION_ROWS=' || count(*) FROM (SELECT version,success FROM user_service.flyway_schema_history UNION ALL SELECT version,success FROM member_bff.flyway_schema_history UNION ALL SELECT version,success FROM stock_service.flyway_schema_history) AS migrations;
SELECT 'SUCCESSFUL_V1_MIGRATIONS=' || count(*) FROM (SELECT version,success FROM user_service.flyway_schema_history UNION ALL SELECT version,success FROM member_bff.flyway_schema_history UNION ALL SELECT version,success FROM stock_service.flyway_schema_history) AS migrations WHERE version='1' AND success;
SELECT 'FAILED_MIGRATIONS=' || count(*) FROM (SELECT success FROM user_service.flyway_schema_history UNION ALL SELECT success FROM member_bff.flyway_schema_history UNION ALL SELECT success FROM stock_service.flyway_schema_history) AS migrations WHERE NOT success;
SELECT 'APPLICATION_ROWS=' || ((SELECT count(*) FROM user_service.users) + (SELECT count(*) FROM user_service.user_roles) + (SELECT count(*) FROM member_bff.chat_rooms) + (SELECT count(*) FROM member_bff.chat_messages) + (SELECT count(*) FROM stock_service.stock_watch_items));
SQL
'@

$expectedMessages = @(
    "SAFE_ROLES=3",
    "SCHEMAS=3",
    "OWN_PRIVILEGE_PAIRS=3",
    "CROSS_PRIVILEGES=0",
    "MIGRATION_HISTORIES=3",
    "EXPECTED_TABLES=5",
    "APPLICATION_TABLES=5",
    "EXPECTED_TABLE_OWNERS=5",
    "MIGRATION_ROWS=3",
    "SUCCESSFUL_V1_MIGRATIONS=3",
    "FAILED_MIGRATIONS=0",
    "APPLICATION_ROWS=0"
)

$request = [ordered]@{
    cluster = $cluster
    taskDefinition = $taskDefinition
    capacityProviderStrategy = @(
        [ordered]@{
            capacityProvider = $capacityProvider
            weight = 1
        }
    )
    networkConfiguration = [ordered]@{
        awsvpcConfiguration = [ordered]@{
            subnets = @($subnets)
            securityGroups = @($securityGroup)
            assignPublicIp = "DISABLED"
        }
    }
    overrides = [ordered]@{
        containerOverrides = @(
            [ordered]@{
                name = "db-bootstrap"
                command = @($verificationCommand)
            }
        )
    }
    count = 1
}

$temporaryFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText(
        $temporaryFile,
        ($request | ConvertTo-Json -Depth 10),
        [System.Text.UTF8Encoding]::new($false)
    )

    $taskResult = (Invoke-AwsCli -Arguments @(
        "ecs", "run-task",
        "--region", $Region,
        "--cli-input-json", "file://$temporaryFile",
        "--output", "json"
    )) | ConvertFrom-Json
}
finally {
    if (Test-Path -LiteralPath $temporaryFile) {
        Remove-Item -LiteralPath $temporaryFile -Force
    }
}

if ($taskResult.failures.Count -gt 0 -or $taskResult.tasks.Count -ne 1) {
    throw "ECS did not start exactly one Flyway verification task."
}

$taskArn = [string]$taskResult.tasks[0].taskArn
$taskId = $taskArn.Split("/")[-1]
Write-Host "Flyway verification task started: $taskArn"

$null = Invoke-AwsCli -Arguments @(
    "ecs", "wait", "tasks-stopped",
    "--region", $Region,
    "--cluster", $cluster,
    "--tasks", $taskArn
)

$taskDescription = (Invoke-AwsCli -Arguments @(
    "ecs", "describe-tasks",
    "--region", $Region,
    "--cluster", $cluster,
    "--tasks", $taskArn,
    "--output", "json"
)) | ConvertFrom-Json

$exitCode = $taskDescription.tasks[0].containers[0].exitCode
if ($exitCode -ne 0) {
    throw "Flyway verification task failed with exit code $exitCode."
}

$logStream = "bootstrap/db-bootstrap/$taskId"
$messages = @()
for ($attempt = 0; $attempt -lt 15 -and $messages.Count -lt $expectedMessages.Count; $attempt++) {
    Start-Sleep -Seconds 2
    $events = (Invoke-AwsCli -Arguments @(
        "logs", "get-log-events",
        "--region", $Region,
        "--log-group-name", $logGroup,
        "--log-stream-name", $logStream,
        "--start-from-head",
        "--output", "json"
    )) | ConvertFrom-Json
    $messages = @($events.events.message)
}

foreach ($expectedMessage in $expectedMessages) {
    if ($messages -notcontains $expectedMessage) {
        throw "Missing Flyway verification result: $expectedMessage"
    }
}

$messages | Where-Object { $_ -in $expectedMessages } | ForEach-Object { Write-Host $_ }
Write-Host "Flyway migration verification completed with exit code 0."
