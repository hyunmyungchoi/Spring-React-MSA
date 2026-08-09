# Platform Node

`worker-platform` is used as the dedicated platform node.

```bash
chmod +x infra/k8s/platform/apply-platform-placement.sh
infra/k8s/platform/apply-platform-placement.sh
```

The script places Argo CD, Metrics Server, the local-path provisioner, and the
Kafka exporter on the platform node. The ingress controller remains on the
application node pool with two replicas.
