[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string]$SourceSha,

    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9]{12}$')]
    [string]$ExpectedAccountId,

    [string]$TerraformDirectory = (Join-Path $PSScriptRoot '..\terraform')
)

$ErrorActionPreference = 'Stop'
$expectedRegion = 'ap-northeast-2'
$services = [ordered]@{
    'user-service'  = 'spring-user-service'
    'member-bff'    = 'spring-member-bff-service'
    'stock-service' = 'spring-member-stock-service'
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)]
        [string]$Command,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $output = & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
    return $output
}

$resolvedTerraformDirectory = (Resolve-Path -LiteralPath $TerraformDirectory).Path
$configuredRegion = (Invoke-CheckedCommand -Command 'aws' -Arguments @('configure', 'get', 'region')).Trim()
if ($configuredRegion -ne $expectedRegion) {
    throw "AWS CLI region must be $expectedRegion; received $configuredRegion."
}

$callerAccountId = (Invoke-CheckedCommand -Command 'aws' -Arguments @(
        'sts',
        'get-caller-identity',
        '--region', $expectedRegion,
        '--query', 'Account',
        '--output', 'text',
        '--no-cli-pager'
    )).Trim()
if ($callerAccountId -ne $ExpectedAccountId) {
    throw "AWS account mismatch. Expected $ExpectedAccountId but authenticated as $callerAccountId."
}

$repositoryJson = Invoke-CheckedCommand -Command 'terraform' -Arguments @(
    "-chdir=$resolvedTerraformDirectory",
    'output',
    '-json',
    'ecr_repository_urls'
)
$repositories = ($repositoryJson -join [Environment]::NewLine) | ConvertFrom-Json

$migrationImages = [ordered]@{}
foreach ($entry in $services.GetEnumerator()) {
    $repositoryProperty = $repositories.PSObject.Properties[$entry.Value]
    if ($null -eq $repositoryProperty) {
        throw "Terraform output does not contain ECR repository URL for $($entry.Value)."
    }

    $repositoryUrl = [string]$repositoryProperty.Value
    $separatorIndex = $repositoryUrl.IndexOf('/')
    if ($separatorIndex -lt 1 -or $separatorIndex -eq ($repositoryUrl.Length - 1)) {
        throw "Invalid ECR repository URL for $($entry.Value): $repositoryUrl"
    }
    $repositoryName = $repositoryUrl.Substring($separatorIndex + 1)

    $digest = (Invoke-CheckedCommand -Command 'aws' -Arguments @(
            'ecr',
            'describe-images',
            '--region', $expectedRegion,
            '--repository-name', $repositoryName,
            '--image-ids', "imageTag=$SourceSha",
            '--query', 'imageDetails[0].imageDigest',
            '--output', 'text',
            '--no-cli-pager'
        )).Trim()

    if ($digest -notmatch '^sha256:[0-9a-f]{64}$') {
        throw "ECR returned an invalid digest for $($entry.Value): $digest"
    }
    $migrationImages[$entry.Key] = "$repositoryUrl@$digest"
}

Write-Output 'database_migration_images = {'
foreach ($entry in $migrationImages.GetEnumerator()) {
    Write-Output "  $($entry.Key) = `"$($entry.Value)`""
}
Write-Output '}'
