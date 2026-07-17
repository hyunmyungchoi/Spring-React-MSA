# ADR-006: Kubernetes와 AWS는 단일 writer Active-Passive DR로 전환한다

- 상태: 제안
- 결정일: 2026-07-17
- 구현 상태: 미구현

## 배경

현재 Kubernetes 배포와 AWS migration 경로는 서로 독립적이다. Kubernetes는 GHCR와 Argo CD를 사용하고 AWS는 Foundation 및 ECR/OIDC 단계까지만 준비됐다. 사용자는 Kubernetes 장애 시 AWS로 전환하고, Kubernetes 복구 후 다시 원복할 수 있는 구조를 원한다.

애플리케이션 image를 두 환경에 실행하는 것만으로 재해 복구가 되지 않는다. PostgreSQL, Redis session, Kafka event, DNS, TLS와 secret 상태를 함께 전환해야 하며, 두 환경이 동시에 write하면 split-brain으로 데이터가 충돌한다.

## 제안 결정

Kubernetes를 선호 site, AWS를 warm standby site로 하는 reversible Active-Passive DR을 목표로 한다.

1. 어느 시점이든 하나의 site와 하나의 PostgreSQL만 writer다.
2. Failover 전에 이전 writer를 fencing하고 AWS database를 승격한다.
3. Failback은 AWS 데이터를 Kubernetes로 역복제한 뒤 짧은 write freeze와 최종 동기화 후 수행한다.
4. 장애 감지는 자동화할 수 있지만 database promotion에는 승인 gate를 둔다.
5. Failback은 flapping과 데이터 역전을 막기 위해 자동화하지 않는다.
6. Redis cache는 재생성하고 기존 login session은 무효화할 수 있다.
7. Kafka 후속 event는 Transactional Outbox를 기준으로 재발행한다.
8. 동일한 Git source SHA의 image만 양쪽 환경에서 사용한다.
9. DNS/traffic control과 장애 관측은 가능한 한 두 site 바깥에 둔다.

## 불변 조건

- active site가 불명확하면 write traffic을 열지 않는다.
- 이전 database writer가 fencing됐다는 증거 없이 standby를 승격하지 않는다.
- replication lag 또는 마지막 재생 위치를 알 수 없으면 RPO를 0으로 보고하지 않는다.
- schema version이 다르면 replication과 traffic 전환을 진행하지 않는다.
- failover 중 build, application deploy와 schema migration을 중지한다.
- 원복은 단순 DNS 되돌리기가 아니라 data authority 이전 작업으로 수행한다.

## 결과

### 장점

- Kubernetes 장애와 AWS 장애를 서로 다른 실행 환경으로 격리할 수 있다.
- Git SHA image와 Git 기반 설정으로 application version을 재현할 수 있다.
- 단일 writer 원칙이 data conflict와 이중 event 처리를 줄인다.
- warm standby는 cold rebuild보다 짧은 RTO를 제공할 수 있다.

### 비용과 복잡성

- 두 환경의 compute, network, certificate, secret과 database replica 비용이 발생한다.
- cross-site PostgreSQL 복제, promotion, fencing과 failback을 구현·훈련해야 한다.
- Redis session이 유실되므로 사용자가 재로그인할 수 있다.
- DNS cache, 기존 WebSocket과 외부 dependency 때문에 즉시 전환을 보장할 수 없다.
- AWS와 Kubernetes 양쪽 관측성을 하나의 incident timeline으로 모아야 한다.

## 고려했지만 채택하지 않은 방식

### Active-Active multi-writer

상시 양쪽 traffic을 처리할 수 있지만 PostgreSQL conflict resolution, session, Kafka ordering과 운영 복잡성이 현재 프로젝트 범위를 크게 초과한다.

### 장애 시 AWS를 처음부터 생성하는 cold standby

비용은 낮지만 ECS/RDS/ALB provisioning, image pull, schema restore와 DNS 전환 시간이 길어 5~15분 RTO 목표에 맞지 않는다.

### DNS만 전환

application traffic은 이동해도 database authority, scheduler, consumer와 기존 connection이 남아 split-brain을 막을 수 없다.

## 채택 조건

이 ADR은 다음 검증이 완료된 뒤 `승인` 상태로 변경한다.

- [ ] failure scope와 RTO/RPO가 합의됨
- [ ] AWS warm standby workload와 production Kubernetes overlay가 존재함
- [ ] PostgreSQL replication, backup/restore, promotion과 reverse replication test가 통과함
- [ ] write fencing과 active-site generation이 검증됨
- [ ] 동일 SHA image, TLS, secret과 OAuth URI가 양쪽에 준비됨
- [ ] Outbox 또는 Kafka DR 전략이 구현됨
- [ ] failover/failback 훈련에서 측정한 RTO/RPO가 기록됨

상세 구조는 [재해 복구 아키텍처](../architecture/disaster-recovery.md), 구현 순서는 [DR 계획](../plans/2026-07-17-k8s-aws-dr-plan.md)을 따른다.
