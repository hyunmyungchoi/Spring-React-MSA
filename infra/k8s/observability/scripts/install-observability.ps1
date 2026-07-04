$ErrorActionPreference = "Stop"

$env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")

$Root = Split-Path -Parent $PSScriptRoot
$ValuesDir = Join-Path $Root "values"
$NamespaceManifest = Join-Path $Root "00-namespace.yaml"

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required but was not found in PATH."
    }
}

function Run {
    param([Parameter(Mandatory = $true)][string[]]$Command)

    Write-Host "> $($Command -join ' ')"
    & $Command[0] $Command[1..($Command.Length - 1)]
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $($Command -join ' ')"
    }
}

Require-Command kubectl
Require-Command helm

Run @("kubectl", "cluster-info")
Run @("kubectl", "apply", "-f", $NamespaceManifest)

Run @("helm", "repo", "add", "prometheus-community", "https://prometheus-community.github.io/helm-charts", "--force-update")
Run @("helm", "repo", "add", "grafana-community", "https://grafana-community.github.io/helm-charts", "--force-update")
Run @("helm", "repo", "add", "grafana", "https://grafana.github.io/helm-charts", "--force-update")
Run @("helm", "repo", "update")

Run @(
    "helm", "upgrade", "--install", "loki", "grafana-community/loki",
    "--namespace", "observability",
    "--create-namespace",
    "--values", (Join-Path $ValuesDir "loki-values.yaml"),
    "--wait",
    "--timeout", "10m"
)

Run @(
    "helm", "upgrade", "--install", "promtail", "grafana/promtail",
    "--namespace", "observability",
    "--values", (Join-Path $ValuesDir "promtail-values.yaml"),
    "--wait",
    "--timeout", "10m"
)

Run @(
    "helm", "upgrade", "--install", "kube-prometheus-stack", "prometheus-community/kube-prometheus-stack",
    "--namespace", "observability",
    "--create-namespace",
    "--values", (Join-Path $ValuesDir "kube-prometheus-stack-values.yaml"),
    "--wait",
    "--timeout", "15m"
)

Run @("kubectl", "get", "pods,svc,ingress,pvc", "-n", "observability")

Write-Host ""
Write-Host "Grafana ingress: http://grafana.localtest.me"
Write-Host "Grafana local port-forward: .\scripts\port-forward-grafana.ps1"
Write-Host "Grafana login: admin / admin"
