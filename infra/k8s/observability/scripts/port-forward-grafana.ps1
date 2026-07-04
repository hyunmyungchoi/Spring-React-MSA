$ErrorActionPreference = "Stop"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is required but was not found in PATH."
}

Write-Host "Grafana: http://localhost:3000"
Write-Host "Login: admin / admin"
kubectl -n observability port-forward svc/kube-prometheus-stack-grafana 3000:80

