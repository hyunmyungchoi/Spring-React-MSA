# Observability

This directory installs the local Kubernetes observability baseline for the
Spring MSA cluster.

## Components

```text
Grafana             UI for metrics and logs
Prometheus          Metrics storage and scrape engine
kube-state-metrics  Kubernetes object metrics
node-exporter       Node metrics
Loki                Log storage
Grafana Alloy       Kubernetes log collector
```

Loki is configured for local development with auth disabled.
Grafana Alloy currently collects logs from these namespaces only:

```text
spring-msa
observability
ingress-nginx
```

## Flow

```text
Spring/Redis/Postgres/Ingress pods
  -> stdout/stderr
  -> Kubernetes pod log API
  -> Grafana Alloy
  -> Loki
  -> Grafana
```

```text
Kubernetes nodes/pods/services
  -> exporters and ServiceMonitors
  -> Prometheus
  -> Grafana
```

## Prerequisites

```powershell
kubectl cluster-info
kubectl get storageclass local-path
helm version
```

If Helm is not installed on Windows:

```powershell
winget install Helm.Helm
```

Restart the terminal after installing Helm so the PATH is refreshed.

## Install

Run from this directory:

```powershell
.\scripts\install-observability.ps1
```

Run directly on the Linux control-plane:

```bash
chmod +x scripts/install-observability.sh
./scripts/install-observability.sh
```

The script installs:

```text
loki                    grafana-community/loki
alloy                   grafana/alloy
kube-prometheus-stack   prometheus-community/kube-prometheus-stack
```

## Access Grafana

If ingress-nginx is running:

```text
http://grafana.localtest.me
```

Or use port-forward:

```powershell
.\scripts\port-forward-grafana.ps1
```

Then open:

```text
http://localhost:3000
```

Local login:

```text
admin / <generated password>
```

Read the generated password without storing it in Git:

```powershell
[System.Text.Encoding]::UTF8.GetString(
  [System.Convert]::FromBase64String(
    (kubectl -n observability get secret kube-prometheus-stack-grafana -o jsonpath="{.data.admin-password}")
  )
)
```

## Verify

```powershell
.\scripts\check-observability.ps1
```

From the Linux control-plane:

```bash
chmod +x scripts/check-observability.sh
./scripts/check-observability.sh
```

Useful LogQL queries:

```logql
{namespace="spring-msa"}
{namespace="spring-msa", app="spring-member-bff-service"}
{namespace="spring-msa"} |= "ERROR"
{namespace="ingress-nginx"}
```

## Uninstall

Remove Helm releases only:

```powershell
.\scripts\uninstall-observability.ps1
```

Remove releases, namespace, and PVCs:

```powershell
.\scripts\uninstall-observability.ps1 -DeleteNamespace
```
