# AWS ECS Resource Baseline

> 문서 상태: 목표 환경의 초기 용량 가정, ECS Compute Foundation 적용·검증 완료
>
> 기준일: 2026-07-18
>
> AWS 적용 상태: ECS Cluster/ASG/Capacity Provider 적용, ASG `0/0/0`, Task/Service 미구현

상위 배치와 ON/OFF 결정은 [AWS Learning Runtime 결정](07-learning-runtime-design.md)을 따른다.

This is an initial ECS on EC2 sizing baseline for Spring services only. It excludes Kafka, Prometheus, Grafana, Loki, PostgreSQL/RDS, Redis/ElastiCache, and frontend nginx containers.

이 값은 부하 측정 결과가 아니라 시작점이다. 운영 용량 결정은 [부하 테스트](../testing/load-test.md) 결과와 JVM/컨테이너 metric을 사용하며, 현재 서비스·replica 구성은 [MSA 구성](../architecture/msa-structure.md)을 기준으로 한다.

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

## Learning Capacity Decision

- ECS 최적화 Amazon Linux 2023 AMI를 사용한다.
- 초기 Instance Type은 두 App AZ 모두에서 제공되는 4 vCPU, 16 GiB `m6i.xlarge` On-Demand다.
- Runtime ON에서 ASG는 `min=1`, `desired=1`, `max=2`다.
- Runtime OFF에서 ASG와 8개 ECS Service의 Desired Capacity를 `0`으로 내린다.
- Service별 Desired Count는 ON에서 `1`, OFF에서 `0`이다.
- Capacity Provider Managed Scaling은 Rolling Deployment의 추가 Capacity를 위해 최대 두 번째 Instance까지 허용한다.
- Learning에서는 Request/CPU 기반 ECS Service Application Auto Scaling을 사용하지 않는다.

위 값은 부하 측정 결과가 아니라 Learning 시작값이다. 8개 Service를 동시에 Rolling Deployment하면 두 번째 Instance가 필요할 수 있으며, Capacity Provider 동작과 배포 시간은 실제 Task 배치 테스트로 검증해야 한다.

Compute Foundation Terraform은 구현했고 `fmt`, `validate`와 11개 계약 테스트를 통과했다. 첫 실제 Remote State Plan은 `m5a.xlarge`가 현재 두 번째 App AZ인 `ap-northeast-2b`에 제공되지 않아 폐기했다. 기존 Network/Data Layer AZ를 교체하지 않고 두 App AZ에 모두 제공되는 `m6i.xlarge`로 보완했다. 새 Plan은 9개 리소스 추가, 기존 리소스 변경·삭제 없음과 ASG `0/0/0`을 확인한 뒤 Apply했다. Cluster/Capacity Provider `ACTIVE`, EC2 0대와 재계획 `No changes`를 검증했다. Task Definition과 Service는 이 단계 범위가 아니다.

서울 리전 2026-07-18 AWS Price List 기준 `m6i.xlarge` Linux On-Demand는 USD 0.236/시간이고 30 GiB `gp3`는 월 USD 2.736이다. Runtime OFF의 Compute 추가 시간당 비용은 없고, 1대를 730시간 유지하면 약 USD 175.02/월, Managed Scaling으로 2대를 유지하면 약 USD 350.03/월이 추가된다. 이는 기존 NAT/EIP, RDS, Secrets Manager와 Data Transfer 비용을 포함하지 않는다. 가격은 [Amazon ECS 요금](https://aws.amazon.com/ecs/pricing/), [EC2 On-Demand 요금](https://aws.amazon.com/ec2/pricing/on-demand/)과 [EBS 요금](https://aws.amazon.com/ebs/pricing/)에서 Apply 전 다시 확인한다.

## Local Kubernetes Mapping

The local Kubernetes manifests use the same memory numbers as `resources.requests` and `resources.limits` so local scheduling failures surface early. CPU units are represented as millicores (`256` → `256m`, `512` → `512m`).

Kubernetes↔AWS DR은 Learning 적용 범위에서 제외한다. 운영형 Warm Standby 용량은 이 Learning Baseline을 운영 보장값으로 재사용하지 않고 별도 부하·복구 시험으로 결정한다.
