$ErrorActionPreference = "Stop"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl is required but was not found in PATH."
}

Write-Host "Grafana: http://localhost:3000"
Write-Host "User: admin"
Write-Host "Read the generated password from secret kube-prometheus-stack-grafana."
kubectl -n observability port-forward svc/kube-prometheus-stack-grafana 3000:80

