# Platform and Observability Node

`worker-platform-observability` is the shared platform and observability node.

```bash
chmod +x infra/k8s/platform/apply-platform-placement.sh
infra/k8s/platform/apply-platform-placement.sh
```

The script places Argo CD, Metrics Server, the local-path provisioner, and the
observability stateful workloads on the shared node. The ingress controller
remains on the application node pool with two replicas.
