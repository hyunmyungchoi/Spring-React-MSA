param(
    [switch]$DeleteNamespace
)

$ErrorActionPreference = "Stop"

$env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required but was not found in PATH."
    }
}

function Run-BestEffort {
    param([Parameter(Mandatory = $true)][string[]]$Command)

    Write-Host "> $($Command -join ' ')"
    & $Command[0] $Command[1..($Command.Length - 1)]
}

Require-Command kubectl
Require-Command helm

Run-BestEffort @("helm", "uninstall", "kube-prometheus-stack", "-n", "observability")
Run-BestEffort @("helm", "uninstall", "promtail", "-n", "observability")
Run-BestEffort @("helm", "uninstall", "loki", "-n", "observability")

if ($DeleteNamespace) {
    Run-BestEffort @("kubectl", "delete", "namespace", "observability")
}
else {
    Write-Host "Namespace and PVCs were left in place. Use -DeleteNamespace to remove them too."
}
