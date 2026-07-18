[CmdletBinding(SupportsShouldProcess = $true)]
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

$identityJson = Invoke-AwsCli -Arguments @("sts", "get-caller-identity", "--output", "json")
$identity = $identityJson | ConvertFrom-Json
if ($identity.Account -ne $ExpectedAccountId) {
    throw "AWS account mismatch. Expected $ExpectedAccountId but authenticated to $($identity.Account)."
}

$secretArnsJson = Invoke-TerraformOutput -OutputName "application_secret_arns"
$secretArns = $secretArnsJson | ConvertFrom-Json

$databaseUsers = [ordered]@{
    "/spring-react-msa/learning/user-service"  = "user_service_app"
    "/spring-react-msa/learning/member-bff"    = "member_bff_app"
    "/spring-react-msa/learning/stock-service" = "stock_service_app"
}

foreach ($secretName in $databaseUsers.Keys) {
    $secretProperty = $secretArns.PSObject.Properties[$secretName]
    if ($null -eq $secretProperty) {
        throw "Terraform output does not contain the required secret container: $secretName"
    }

    $secretArn = [string]$secretProperty.Value
    $expectedUsername = [string]$databaseUsers[$secretName]
    $currentVersionCount = Invoke-AwsCli -Arguments @(
        "secretsmanager", "list-secret-version-ids",
        "--secret-id", $secretArn,
        "--region", $Region,
        "--include-deprecated",
        "--query", "length(Versions[?contains(VersionStages, 'AWSCURRENT')])",
        "--output", "text"
    )

    if ([int]$currentVersionCount -gt 0) {
        $existingSecretString = Invoke-AwsCli -Arguments @(
            "secretsmanager", "get-secret-value",
            "--secret-id", $secretArn,
            "--region", $Region,
            "--query", "SecretString",
            "--output", "text"
        )

        try {
            $existingSecret = $existingSecretString | ConvertFrom-Json
        }
        catch {
            throw "The current value of $secretName is not valid JSON. It was not changed."
        }

        $propertyNames = @($existingSecret.PSObject.Properties.Name)
        if (-not ($propertyNames -contains "db_username") -or
            -not ($propertyNames -contains "db_password") -or
            [string]$existingSecret.db_username -ne $expectedUsername -or
            [string]::IsNullOrWhiteSpace([string]$existingSecret.db_password)) {
            throw "The current value of $secretName does not match the approved DB credential contract. It was not changed."
        }

        $existingSecretString = $null
        $existingSecret = $null
        Write-Host "Already initialized and valid; skipped: $secretName"
        continue
    }

    if (-not $PSCmdlet.ShouldProcess($secretName, "Create the initial db_username and random db_password secret version")) {
        continue
    }

    $password = Invoke-AwsCli -Arguments @(
        "secretsmanager", "get-random-password",
        "--region", $Region,
        "--password-length", "40",
        "--exclude-punctuation",
        "--query", "RandomPassword",
        "--output", "text"
    )

    $secretString = [ordered]@{
        db_username = $expectedUsername
        db_password = $password
    } | ConvertTo-Json -Compress

    $temporaryFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText(
            $temporaryFile,
            $secretString,
            [System.Text.UTF8Encoding]::new($false)
        )

        $null = Invoke-AwsCli -Arguments @(
            "secretsmanager", "put-secret-value",
            "--secret-id", $secretArn,
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
        $password = $null
        $secretString = $null
    }

    Write-Host "Initialized without printing credentials: $secretName"
}

Write-Host "Database secret initialization check completed."
