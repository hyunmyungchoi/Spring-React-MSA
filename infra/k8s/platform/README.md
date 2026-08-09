# Platform Node

The existing `worker-app-3` node is used as the dedicated platform node. Its
hostname remains unchanged to avoid rejoining the Kubernetes cluster.

```bash
chmod +x infra/k8s/platform/apply-platform-placement.sh
infra/k8s/platform/apply-platform-placement.sh
```

The script places Argo CD, Metrics Server, the local-path provisioner, and the
Kafka exporter on the platform node. The ingress controller remains on the
application node pool with two replicas.
