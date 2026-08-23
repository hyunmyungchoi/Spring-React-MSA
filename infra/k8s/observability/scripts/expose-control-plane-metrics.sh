#!/usr/bin/env bash
set -euo pipefail

MANIFEST_DIR=/etc/kubernetes/manifests
BACKUP_DIR=/etc/kubernetes/backup-springmsa
sudo mkdir -p "$BACKUP_DIR"

for file in kube-controller-manager.yaml kube-scheduler.yaml etcd.yaml; do
  if [[ ! -f "$BACKUP_DIR/$file.springmsa.bak" ]]; then
    sudo cp "$MANIFEST_DIR/$file" "$BACKUP_DIR/$file.springmsa.bak"
  fi
done

sudo sed -i 's/--bind-address=127.0.0.1/--bind-address=0.0.0.0/' "$MANIFEST_DIR/kube-controller-manager.yaml"
sudo sed -i 's/--bind-address=127.0.0.1/--bind-address=0.0.0.0/' "$MANIFEST_DIR/kube-scheduler.yaml"
sudo sed -i 's#--listen-metrics-urls=http://127.0.0.1:2381#--listen-metrics-urls=http://0.0.0.0:2381#' "$MANIFEST_DIR/etcd.yaml"
sudo touch "$MANIFEST_DIR/kube-controller-manager.yaml" "$MANIFEST_DIR/kube-scheduler.yaml" "$MANIFEST_DIR/etcd.yaml"

echo "Updated control-plane metrics endpoints."
echo "Backups: $BACKUP_DIR"
