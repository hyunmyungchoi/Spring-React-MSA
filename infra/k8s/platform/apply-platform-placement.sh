#!/usr/bin/env bash
set -euo pipefail

PLATFORM_NODE="${PLATFORM_NODE:-worker-app-3}"

kubectl label node worker-1 node-pool=application workload=application --overwrite
kubectl label node worker-2 node-pool=application workload=application --overwrite
kubectl label node "${PLATFORM_NODE}" node-pool=platform workload=platform --overwrite
kubectl taint node "${PLATFORM_NODE}" node-pool=platform:NoSchedule --overwrite

platform_patch='{"spec":{"template":{"spec":{"nodeSelector":{"node-pool":"platform"},"tolerations":[{"key":"node-pool","operator":"Equal","value":"platform","effect":"NoSchedule"}]}}}}'
application_patch='{"spec":{"replicas":2,"template":{"spec":{"nodeSelector":{"node-pool":"application"},"affinity":{"podAntiAffinity":{"requiredDuringSchedulingIgnoredDuringExecution":[{"labelSelector":{"matchLabels":{"app.kubernetes.io/component":"controller","app.kubernetes.io/instance":"ingress-nginx","app.kubernetes.io/name":"ingress-nginx"}},"topologyKey":"kubernetes.io/hostname"}]}}}}}}'

while IFS= read -r resource; do
  kubectl -n argocd patch "${resource}" --type merge -p "${platform_patch}"
done < <(kubectl -n argocd get deployment,statefulset -o name)

kubectl -n kube-system patch deployment metrics-server --type merge -p "${platform_patch}"
kubectl -n local-path-storage patch deployment local-path-provisioner --type merge -p "${platform_patch}"
kubectl -n ingress-nginx patch deployment ingress-nginx-controller --type merge -p "${application_patch}"

if kubectl -n kafka get deployment kafka-exporter >/dev/null 2>&1; then
  kubectl -n kafka patch deployment kafka-exporter --type merge -p "${platform_patch}"
fi
