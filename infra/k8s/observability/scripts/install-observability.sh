#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VALUES_DIR="$ROOT/values"
HELM_VERSION="v4.1.3"
LOKI_CHART_VERSION="18.5.0"
ALLOY_CHART_VERSION="1.11.0"
KUBE_PROMETHEUS_STACK_CHART_VERSION="87.16.1"

install_helm() {
  if command -v helm >/dev/null 2>&1; then
    return
  fi

  local temp_dir
  temp_dir="$(mktemp -d)"
  trap 'rm -rf "$temp_dir"' RETURN

  curl -fsSLo "$temp_dir/helm.tar.gz" \
    "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz"
  curl -fsSLo "$temp_dir/helm.tar.gz.sha256sum" \
    "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz.sha256sum"
  (
    cd "$temp_dir"
    sed "s/helm-${HELM_VERSION}-linux-amd64.tar.gz/helm.tar.gz/" \
      helm.tar.gz.sha256sum | sha256sum -c -
    tar -xzf helm.tar.gz
  )
  mkdir -p "$HOME/.local/bin"
  install -m 0755 "$temp_dir/linux-amd64/helm" "$HOME/.local/bin/helm"
}

install_helm
export PATH="$HOME/.local/bin:$PATH"

kubectl cluster-info
kubectl get storageclass local-path
kubectl apply -f "$ROOT/00-namespace.yaml"

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
helm repo add grafana-community https://grafana-community.github.io/helm-charts --force-update
helm repo add grafana https://grafana.github.io/helm-charts --force-update
helm repo update

helm upgrade --install loki grafana-community/loki \
  --version "$LOKI_CHART_VERSION" \
  --namespace observability \
  --create-namespace \
  --values "$VALUES_DIR/loki-values.yaml" \
  --wait \
  --timeout 15m

helm upgrade --install alloy grafana/alloy \
  --version "$ALLOY_CHART_VERSION" \
  --namespace observability \
  --values "$VALUES_DIR/alloy-values.yaml" \
  --wait \
  --timeout 10m

helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --version "$KUBE_PROMETHEUS_STACK_CHART_VERSION" \
  --namespace observability \
  --create-namespace \
  --values "$VALUES_DIR/kube-prometheus-stack-values.yaml" \
  --wait \
  --timeout 20m

kubectl get pods,svc,ingress,pvc -n observability
