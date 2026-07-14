# AWS ECS Resource Baseline

This is an initial ECS on EC2 sizing baseline for Spring services only. It excludes Kafka, Prometheus, Grafana, Loki, PostgreSQL/RDS, Redis/ElastiCache, and frontend nginx containers.

## Service Task Size

| Service | CPU units | Memory |
|---|---:|---:|
| `spring-member-gateway` | 256 | 512 MB |
| `spring-admin-gateway` | 256 | 512 MB |
| `spring-member-bff-service` | 512 | 1024 MB |
| `spring-admin-bff-service` | 512 | 768 MB |
| `spring-security-authorization-server` | 512 | 1024 MB |
| `spring-user-service` | 512 | 768 MB |
| `spring-member-community-service` | 512 | 768 MB |
| `spring-member-stock-service` | 512 | 1024 MB |

## JVM Baseline

All Spring runtime images provide this default and allow runtime override:

```text
JAVA_TOOL_OPTIONS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=30
```

`JAVA_OPTS` remains available for service-specific JVM/system properties.

## Capacity Notes

- Steady-state Spring task memory total: 6400 MB.
- Rolling deployment peak if every service rolls at the same time: 12800 MB.
- If Admin Gateway and Admin BFF desired count are set to `0`, steady-state drops by 1280 MB to 5120 MB.
- Service Connect or sidecar proxies require additional memory per task and are not included in the 6400 MB total.
- ECS Agent, Docker/containerd, OS, CloudWatch agent, and node-level daemons require reserved host memory.
- An 8 GB EC2 instance is too tight for all Spring tasks plus OS/agent/proxy overhead and leaves little room for rolling deployments.
- A 16 GB EC2 instance is the practical minimum for one-node validation with all Spring services. Production should use multiple instances or reduce desired counts per capacity plan.

## Local Kubernetes Mapping

The local Kubernetes manifests use the same memory numbers as `resources.requests` and `resources.limits` so local scheduling failures surface early. CPU units are represented as millicores (`256` → `256m`, `512` → `512m`).
