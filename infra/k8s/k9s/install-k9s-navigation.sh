#!/usr/bin/env bash
set -euo pipefail

K9S_CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/k9s"
BACKUP_SUFFIX="$(date +%Y%m%d%H%M%S)"

mkdir -p "$K9S_CONFIG_DIR"

for file in hotkeys.yaml aliases.yaml; do
  if [[ -f "$K9S_CONFIG_DIR/$file" ]]; then
    cp "$K9S_CONFIG_DIR/$file" "$K9S_CONFIG_DIR/$file.$BACKUP_SUFFIX.bak"
  fi
done

cat > "$K9S_CONFIG_DIR/hotkeys.yaml" <<'EOF'
hotKeys:
  shift-0:
    shortCut: Shift-0
    description: All namespace pods
    command: pods all
  shift-1:
    shortCut: Shift-1
    description: Application pods
    command: pods spring-msa
  shift-2:
    shortCut: Shift-2
    description: Argo CD platform pods
    command: pods argocd
  shift-3:
    shortCut: Shift-3
    description: Observability pods
    command: pods observability
  shift-4:
    shortCut: Shift-4
    description: Kafka pods
    command: pods kafka
  shift-5:
    shortCut: Shift-5
    description: Ingress pods
    command: pods ingress-nginx
  shift-6:
    shortCut: Shift-6
    description: Kubernetes system pods
    command: pods kube-system
EOF

cat > "$K9S_CONFIG_DIR/aliases.yaml" <<'EOF'
aliases:
  app: pod spring-msa
  platform: pod argocd
  obs: pod observability
  msg: pod kafka
  net: pod ingress-nginx
  system: pod kube-system
  allpods: pod all
EOF

declare -A namespace_groups=(
  [spring-msa]=application
  [argocd]=platform
  [kube-system]=platform-system
  [local-path-storage]=platform-storage
  [observability]=observability
  [kafka]=messaging
  [ingress-nginx]=networking
  [cilium-secrets]=networking
)

for namespace in "${!namespace_groups[@]}"; do
  if kubectl get namespace "$namespace" >/dev/null 2>&1; then
    kubectl label namespace "$namespace" \
      springmsa.io/group="${namespace_groups[$namespace]}" \
      --overwrite >/dev/null
  fi
done

printf 'K9s navigation installed in %s\n' "$K9S_CONFIG_DIR"
printf 'Hotkeys: Shift-0 all, Shift-1 app, Shift-2 platform, Shift-3 observability, Shift-4 kafka, Shift-5 ingress, Shift-6 system\n'
