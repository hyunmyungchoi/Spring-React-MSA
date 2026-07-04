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
Promtail            Log collector DaemonSet
```

Loki is configured for local development with auth disabled.
Promtail currently collects logs from these namespaces only:

```text
spring-msa
observability
ingress-nginx
```

## Flow

```text
Spring/Redis/Postgres/Ingress pods
  -> stdout/stderr
  -> node container log files
  -> Promtail DaemonSet
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

The script installs:

```text
loki                    grafana-community/loki
promtail                grafana/promtail
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

Default local login:

```text
admin / admin
```

## Verify

```powershell
.\scripts\check-observability.ps1
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
