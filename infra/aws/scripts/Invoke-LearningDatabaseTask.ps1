[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$TaskDefinition,

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

    $arguments = @("-chdir=$TerraformDirectory", "output")
    if ($Json) {
        $arguments += "-json"
    }
    else {
        $arguments += "-raw"
    }
    $arguments += $Name

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

if ($subnets.Count -ne 2) {
    throw "Expected exactly two Private App subnets."
}

$networkConfiguration = "awsvpcConfiguration={subnets=[$($subnets -join ',')],securityGroups=[$securityGroup],assignPublicIp=DISABLED}"
$taskResultJson = Invoke-AwsCli -Arguments @(
    "ecs", "run-task",
    "--region", $Region,
    "--cluster", $cluster,
    "--task-definition", $TaskDefinition,
    "--capacity-provider-strategy", "capacityProvider=$capacityProvider,weight=1",
    "--network-configuration", $networkConfiguration,
    "--count", "1",
    "--output", "json"
)

$taskResult = $taskResultJson | ConvertFrom-Json
if ($taskResult.failures.Count -gt 0 -or $taskResult.tasks.Count -ne 1) {
    throw "ECS did not start exactly one task. Review the run-task failures in AWS Console."
}

$taskArn = [string]$taskResult.tasks[0].taskArn
Write-Host "Task started. Waiting for completion: $taskArn"

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

$container = $taskDescription.tasks[0].containers[0]
if ($container.exitCode -ne 0) {
    $reason = if ($container.PSObject.Properties.Name -contains "reason" -and $null -ne $container.reason) {
        [string]$container.reason
    }
    else {
        [string]$taskDescription.tasks[0].stoppedReason
    }
    throw "Database task failed with exit code $($container.exitCode). Reason: $reason"
}

Write-Host "Database task completed successfully with exit code 0."
