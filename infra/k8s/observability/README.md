# Observability

- worker-platform-observability: Grafana, MinIO, Loki gateway, operator, Prometheus, kube-state-metrics
- 모든 Kubernetes node: Alloy, node-exporter
- Storage/Kafka VM: 외부 Alloy agent

~~~text
Pod logs -> Alloy -> Loki -> Grafana
External VM logs -> Alloy -> Loki NodePort -> Grafana
Metrics/exporters -> Prometheus -> Grafana
~~~

## 설치

~~~bash
chmod +x scripts/install-observability.sh scripts/expose-control-plane-metrics.sh
./scripts/install-observability.sh
./scripts/expose-control-plane-metrics.sh
~~~

control-plane 백업은 /etc/kubernetes/backup-springmsa에 둔다. /etc/kubernetes/manifests 안에 백업을 두면 안 된다.

## 검증

~~~bash
kubectl top nodes
kubectl get --raw '/api/v1/namespaces/observability/services/http:kube-prometheus-stack-prometheus:9090/proxy/-/ready'
kubectl get --raw '/api/v1/namespaces/observability/services/http:loki-gateway:80/proxy/loki/api/v1/labels'
~~~

Grafana: http://grafana.localtest.me
