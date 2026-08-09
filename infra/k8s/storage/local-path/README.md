# local-path storage

The VMware learning cluster uses Rancher local-path-provisioner `v0.0.37` for
dynamic PVC provisioning.

```bash
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.37/deploy/local-path-storage.yaml
kubectl wait -n local-path-storage --for=condition=Available deployment/local-path-provisioner --timeout=180s
kubectl get storageclass local-path
```

Volumes are stored on the selected Kubernetes node. This is suitable for the
local learning cluster but is not highly available. A production on-premises
cluster should use replicated storage or an external CSI backend.
