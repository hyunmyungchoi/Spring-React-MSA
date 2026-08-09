#!/usr/bin/env bash
set -euo pipefail

echo "== Argo CD =="
kubectl get application spring-msa -n argocd \
  -o custom-columns=SYNC:.status.sync.status,HEALTH:.status.health.status --no-headers

probe() {
  local name="$1"
  local url="$2"
  local output
  local status
  output="$(kubectl run "probe-$name" --rm -i --restart=Never \
    --image=curlimages/curl:8.17.0 -n observability -- \
    curl -sS -o /dev/null -w '%{http_code}' "$url")"
  status="${output:0:3}"
  echo "${name^^}=$status"
  [[ "$status" == "200" ]]
}

echo "== Observability endpoints =="
probe grafana http://kube-prometheus-stack-grafana/api/health
probe prometheus http://kube-prometheus-stack-prometheus:9090/-/ready
probe loki http://loki-gateway/loki/api/v1/labels

echo "== Spring actuator Prometheus endpoints =="
services=(
  spring-user-service:8081
  spring-member-community-service:8083
  spring-member-stock-service:8084
  spring-member-bff-service:8082
  spring-admin-bff-service:8087
  spring-member-gateway:8080
  spring-admin-gateway:8090
  spring-security-authorization-server:9000
)

for item in "${services[@]}"; do
  name="${item%:*}"
  port="${item#*:}"
  status="$(kubectl exec -n spring-msa "deployment/$name" -- \
    curl -sS -o /dev/null -w '%{http_code}' \
      "http://127.0.0.1:$port/actuator/prometheus" 2>/dev/null || true)"
  echo "$name=${status:-UNREACHABLE}"
done
