[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9]{12}$')]
    [string]$ExpectedAccountId,

    [string]$Region = "ap-northeast-2",
    [string]$TerraformDirectory = (Join-Path $PSScriptRoot "..\terraform"),
    [string]$LocalEnvFile = (Join-Path $PSScriptRoot "..\..\docker\.env.local")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-AwsCli {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $output = & aws @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "AWS CLI command failed. No secret value was printed."
    }

    return ($output -join [Environment]::NewLine).Trim()
}

function Invoke-TerraformOutput {
    param([Parameter(Mandatory)][string]$OutputName)

    $output = & terraform "-chdir=$TerraformDirectory" output -json $OutputName
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read Terraform output '$OutputName'."
    }

    return ($output -join [Environment]::NewLine)
}

function Read-DotEnv {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Local environment file was not found: $Path"
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -notmatch '^\s*([^#=\s]+)\s*=\s*(.*)\s*$') {
            continue
        }

        $key = $Matches[1]
        $value = $Matches[2]
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $values[$key] = $value
    }

    return $values
}

function Get-RequiredLocalValue {
    param(
        [Parameter(Mandatory)][hashtable]$Values,
        [Parameter(Mandatory)][string]$Name
    )

    $value = [string]$Values[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required local value is missing: $Name"
    }

    return $value
}

function Get-SecretObject {
    param([Parameter(Mandatory)][string]$SecretArn)

    $currentVersionCount = Invoke-AwsCli -Arguments @(
        "secretsmanager", "list-secret-version-ids",
        "--secret-id", $SecretArn,
        "--region", $Region,
        "--include-deprecated",
        "--query", "length(Versions[?contains(VersionStages, 'AWSCURRENT')])",
        "--output", "text"
    )

    if ([int]$currentVersionCount -eq 0) {
        return $null
    }

    $secretString = Invoke-AwsCli -Arguments @(
        "secretsmanager", "get-secret-value",
        "--secret-id", $SecretArn,
        "--region", $Region,
        "--query", "SecretString",
        "--output", "text"
    )

    try {
        return ($secretString | ConvertFrom-Json)
    }
    catch {
        throw "An existing runtime secret is not valid JSON. It was not changed."
    }
    finally {
        $secretString = $null
    }
}

function Set-SecretObject {
    param(
        [Parameter(Mandatory)][string]$SecretArn,
        [Parameter(Mandatory)][object]$SecretObject
    )

    $secretString = $SecretObject | ConvertTo-Json -Compress
    $temporaryFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText(
            $temporaryFile,
            $secretString,
            [System.Text.UTF8Encoding]::new($false)
        )

        $null = Invoke-AwsCli -Arguments @(
            "secretsmanager", "put-secret-value",
            "--secret-id", $SecretArn,
            "--region", $Region,
            "--secret-string", "file://$temporaryFile",
            "--query", "VersionId",
            "--output", "text"
        )
    }
    finally {
        if (Test-Path -LiteralPath $temporaryFile) {
            Remove-Item -LiteralPath $temporaryFile -Force
        }
        $secretString = $null
    }
}

function Assert-ExistingValueMatches {
    param(
        [Parameter(Mandatory)][object]$SecretObject,
        [Parameter(Mandatory)][string]$Key,
        [Parameter(Mandatory)][string]$ExpectedValue,
        [Parameter(Mandatory)][string]$SecretName
    )

    $property = $SecretObject.PSObject.Properties[$Key]
    if ($null -ne $property -and [string]$property.Value -ne $ExpectedValue) {
        throw "The existing $SecretName key '$Key' differs from the approved local value. It was not changed."
    }
}

$identityJson = Invoke-AwsCli -Arguments @("sts", "get-caller-identity", "--output", "json")
$identity = $identityJson | ConvertFrom-Json
if ($identity.Account -ne $ExpectedAccountId) {
    throw "AWS account mismatch. Expected $ExpectedAccountId but authenticated to $($identity.Account)."
}

$localValues = Read-DotEnv -Path $LocalEnvFile
$memberClientSecret = Get-RequiredLocalValue -Values $localValues -Name "BFF_CLIENT_SECRET"
$memberClientSecretHash = Get-RequiredLocalValue -Values $localValues -Name "BFF_CLIENT_SECRET_HASH"
$adminClientSecret = Get-RequiredLocalValue -Values $localValues -Name "ADMIN_BFF_CLIENT_SECRET"
$adminClientSecretHash = Get-RequiredLocalValue -Values $localValues -Name "ADMIN_BFF_CLIENT_SECRET_HASH"
$tossClientSecret = Get-RequiredLocalValue -Values $localValues -Name "TOSS_API_CLIENT_SECRET"

if ($memberClientSecretHash -notmatch '^\$2[aby]\$' -or $adminClientSecretHash -notmatch '^\$2[aby]\$') {
    throw "Both OAuth client secret hashes must use the existing BCrypt contract."
}

$secretArnsJson = Invoke-TerraformOutput -OutputName "application_secret_arns"
$secretArns = $secretArnsJson | ConvertFrom-Json

$requiredSecretNames = @(
    "/spring-react-msa/learning/admin-bff",
    "/spring-react-msa/learning/auth-server",
    "/spring-react-msa/learning/member-bff",
    "/spring-react-msa/learning/shared/internal-api",
    "/spring-react-msa/learning/shared/redis",
    "/spring-react-msa/learning/stock-service"
)

$secretArnByName = @{}
foreach ($secretName in $requiredSecretNames) {
    $secretProperty = $secretArns.PSObject.Properties[$secretName]
    if ($null -eq $secretProperty) {
        throw "Terraform output does not contain the required secret container: $secretName"
    }
    $secretArnByName[$secretName] = [string]$secretProperty.Value
}

$memberSecretName = "/spring-react-msa/learning/member-bff"
$memberSecret = Get-SecretObject -SecretArn $secretArnByName[$memberSecretName]
if ($null -eq $memberSecret) {
    throw "The Member BFF database secret must be initialized before runtime secrets."
}
Assert-ExistingValueMatches -SecretObject $memberSecret -Key "bff_client_secret" -ExpectedValue $memberClientSecret -SecretName $memberSecretName
if ($null -eq $memberSecret.PSObject.Properties["bff_client_secret"] -and
    $PSCmdlet.ShouldProcess($memberSecretName, "Add the member OAuth client secret without printing it")) {
    $memberSecret | Add-Member -NotePropertyName "bff_client_secret" -NotePropertyValue $memberClientSecret
    Set-SecretObject -SecretArn $secretArnByName[$memberSecretName] -SecretObject $memberSecret
    Write-Host "Runtime key initialized without printing credentials: $memberSecretName"
}

$stockSecretName = "/spring-react-msa/learning/stock-service"
$stockSecret = Get-SecretObject -SecretArn $secretArnByName[$stockSecretName]
if ($null -eq $stockSecret) {
    throw "The Stock Service database secret must be initialized before runtime secrets."
}
Assert-ExistingValueMatches -SecretObject $stockSecret -Key "toss_api_client_secret" -ExpectedValue $tossClientSecret -SecretName $stockSecretName
if ($null -eq $stockSecret.PSObject.Properties["toss_api_client_secret"] -and
    $PSCmdlet.ShouldProcess($stockSecretName, "Add the Toss API client secret without printing it")) {
    $stockSecret | Add-Member -NotePropertyName "toss_api_client_secret" -NotePropertyValue $tossClientSecret
    Set-SecretObject -SecretArn $secretArnByName[$stockSecretName] -SecretObject $stockSecret
    Write-Host "Runtime key initialized without printing credentials: $stockSecretName"
}

$fixedSecrets = [ordered]@{
    "/spring-react-msa/learning/admin-bff" = [ordered]@{
        admin_bff_client_secret = $adminClientSecret
    }
    "/spring-react-msa/learning/auth-server" = [ordered]@{
        bff_client_secret_hash       = $memberClientSecretHash
        admin_bff_client_secret_hash = $adminClientSecretHash
    }
}

foreach ($secretName in $fixedSecrets.Keys) {
    $expected = $fixedSecrets[$secretName]
    $existing = Get-SecretObject -SecretArn $secretArnByName[$secretName]
    if ($null -ne $existing) {
        $requiresUpdate = $false
        foreach ($property in $expected.GetEnumerator()) {
            Assert-ExistingValueMatches -SecretObject $existing -Key $property.Key -ExpectedValue $property.Value -SecretName $secretName
            if ($null -eq $existing.PSObject.Properties[$property.Key]) {
                $existing | Add-Member -NotePropertyName $property.Key -NotePropertyValue $property.Value
                $requiresUpdate = $true
            }
        }
        if ($requiresUpdate -and $PSCmdlet.ShouldProcess($secretName, "Add missing runtime secret keys without printing them")) {
            Set-SecretObject -SecretArn $secretArnByName[$secretName] -SecretObject $existing
            Write-Host "Missing runtime keys initialized without printing credentials: $secretName"
        }
        else {
            Write-Host "Already initialized and valid; skipped: $secretName"
        }
        continue
    }

    if ($PSCmdlet.ShouldProcess($secretName, "Create the initial runtime secret version without printing it")) {
        Set-SecretObject -SecretArn $secretArnByName[$secretName] -SecretObject $expected
        Write-Host "Initialized without printing credentials: $secretName"
    }
}

$generatedSecrets = [ordered]@{
    "/spring-react-msa/learning/shared/redis" = [ordered]@{
        Key       = "redis_password"
        Length    = 40
        MinLength = 32
    }
    "/spring-react-msa/learning/shared/internal-api" = [ordered]@{
        Key       = "internal_api_token"
        Length    = 64
        MinLength = 32
    }
}

foreach ($secretName in $generatedSecrets.Keys) {
    $contract = $generatedSecrets[$secretName]
    $existing = Get-SecretObject -SecretArn $secretArnByName[$secretName]
    if ($null -ne $existing) {
        $property = $existing.PSObject.Properties[$contract.Key]
        $existingValue = if ($null -eq $property) { "" } else { [string]$property.Value }
        if ($existingValue -notmatch '^[A-Za-z0-9]+$' -or $existingValue.Length -lt $contract.MinLength) {
            throw "The existing $secretName value does not meet the runtime secret contract. It was not changed."
        }
        Write-Host "Already initialized and valid; skipped: $secretName"
        continue
    }

    if (-not $PSCmdlet.ShouldProcess($secretName, "Create an AWS-generated runtime secret version without printing it")) {
        continue
    }

    $randomValue = Invoke-AwsCli -Arguments @(
        "secretsmanager", "get-random-password",
        "--region", $Region,
        "--password-length", [string]$contract.Length,
        "--exclude-punctuation",
        "--query", "RandomPassword",
        "--output", "text"
    )
    $secretObject = [ordered]@{ $contract.Key = $randomValue }
    Set-SecretObject -SecretArn $secretArnByName[$secretName] -SecretObject $secretObject
    $randomValue = $null
    $secretObject = $null
    Write-Host "Initialized without printing credentials: $secretName"
}

$memberClientSecret = $null
$memberClientSecretHash = $null
$adminClientSecret = $null
$adminClientSecretHash = $null
$tossClientSecret = $null
$localValues = $null

Write-Host "Runtime secret initialization check completed. No secret values were printed."
