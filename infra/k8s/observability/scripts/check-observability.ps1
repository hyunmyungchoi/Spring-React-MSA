$ErrorActionPreference = "Stop"

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required but was not found in PATH."
    }
}

Require-Command kubectl

Write-Host "== Cluster =="
kubectl cluster-info

Write-Host ""
Write-Host "== Observability namespace =="
kubectl get pods,svc,ingress,pvc -n observability

Write-Host ""
Write-Host "== Log collector =="
kubectl get daemonset,pods -n observability -l app.kubernetes.io/name=promtail

Write-Host ""
Write-Host "== Spring MSA workload =="
kubectl get pods -n spring-msa

Write-Host ""
Write-Host "Grafana LogQL examples:"
Write-Host '{namespace="spring-msa"}'
Write-Host '{namespace="spring-msa", app="spring-member-bff-service"}'
Write-Host '{namespace="spring-msa"} |= "ERROR"'

