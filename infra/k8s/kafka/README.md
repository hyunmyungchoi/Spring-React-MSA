# External Kafka

Kafka runs on the external VM at `192.168.147.131`. This directory creates a
Kubernetes Service and EndpointSlice for that VM and runs only Kafka Exporter
inside Kubernetes.

```bash
kubectl apply -f infra/k8s/kafka/00-namespace.yaml
kubectl apply -f infra/k8s/kafka/05-kafka.yaml
kubectl apply -f infra/k8s/kafka/10-kafka-exporter.yaml
kubectl apply -f infra/k8s/kafka/20-servicemonitors.yaml
```
