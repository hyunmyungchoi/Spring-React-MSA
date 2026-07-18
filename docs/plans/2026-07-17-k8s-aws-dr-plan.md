# Kubernetes↔AWS 재해 복구 구현 계획

- 작성일: 2026-07-17
- 상태: 보류 — Learning 적용 범위 제외, 운영 환경 후속 학습 과제
- 기준 결정: [ADR-006](../decisions/ADR-006-reversible-k8s-aws-dr.md)
- 목표: Kubernetes 장애 시 AWS로 전환하고 안전하게 Kubernetes로 원복

> 2026-07-18 범위 결정: 현재는 AWS Learning 단일 환경, RDS Backup/PITR와 Restore 검증을 우선한다. 이 계획은 ECS/RDS/Frontend가 구현되고 외부 접근 가능한 운영 Kubernetes가 준비될 때까지 착수하지 않는다.

## 완료 정의

- Kubernetes와 AWS가 동일한 application source SHA를 실행할 수 있다.
- 한쪽만 database writer가 되도록 fencing과 active-site 확인이 강제된다.
- PostgreSQL failover와 reverse replication을 반복 가능하게 수행한다.
- public domain, TLS, OAuth redirect와 secret이 양쪽에 준비된다.
- 외부 monitor가 장애를 감지하고 운영자가 승인된 절차로 전환한다.
- 실제 훈련에서 RTO 5~15분과 합의된 RPO를 측정한다.

## 0단계: 정책과 failure scope 확정

1. Kubernetes cluster 장애, site network 장애, AWS region 장애, registry/GitHub 장애를 각각 정의한다.
2. 서비스별 RTO/RPO와 session 재로그인 허용을 승인한다.
3. preferred site를 Kubernetes, recovery site를 AWS로 확정한다.
4. 자동 감지·수동 promotion·수동 failback 원칙을 승인한다.
5. DNS를 AWS에 둘 때의 장애 범위와 AWS 외부 control plane 필요성을 결정한다.

산출물은 승인 상태의 ADR-006과 장애 판단표다.

## 1단계: 배포 artifact와 환경 분리

1. Kubernetes manifest를 공통 base와 환경 overlay로 분리한다.
2. `localtest.me` 값을 production hostname과 분리한다.
3. ECS Task Definition용 환경 변수와 secret reference를 Environment Matrix에서 생성한다.
4. GHCR와 ECR에 동일 source SHA image가 존재하는지 검증하는 CI check를 추가한다.
5. 양쪽 environment의 schema version과 application compatibility를 배포 gate로 만든다.

ECR/OIDC Apply는 [`infra/aws/terraform/README.md`](../../infra/aws/terraform/README.md)의 별도 승인 gate를 따른다.

## 2단계: AWS warm standby 구현

1. NAT Gateway와 VPC Endpoint 조합을 비용·가용성 기준으로 결정한다.
2. ECS Cluster, capacity provider, Task Definition과 Service를 구현한다.
3. Member/Admin ALB listener와 health check를 구성한다.
4. RDS와 ElastiCache를 private data subnet에 구성한다.
5. ACM/TLS와 public hostname 전환 경계를 준비한다.
6. CloudWatch logs, metrics와 alarm을 외부 incident view에 연결한다.
7. 평상시 최소 capacity와 failover 목표 capacity의 scale 시간을 측정한다.

AWS workload 생성과 비용 발생 변경은 검토된 Terraform plan과 명시적 승인을 요구한다.

## 3단계: PostgreSQL migration과 복제

1. 승인한 Flyway SQL Migration으로 versioned production migration을 도입한다.
2. Community 영속화와 Chat Outbox를 포함할 schema 순서를 결정한다.
3. backup, point-in-time recovery와 restore 시간을 검증한다.
4. Kubernetes→AWS 비동기 복제 방식을 선택하고 TLS network를 구성한다.
5. replication lag, last replay position과 database role metric을 수집한다.
6. Kubernetes writer fencing 후 AWS promotion을 test한다.
7. AWS→Kubernetes reverse replication과 새 replica rebuild를 test한다.
8. sequence, constraint, large transaction과 schema migration 중 복제를 검증한다.

data corruption 또는 split-brain test가 통과하기 전에는 public traffic failover를 연결하지 않는다.

## 4단계: Redis, Kafka와 background work

1. failover 시 모든 기존 Spring Session을 무효화하고 재로그인시키는 절차를 구현한다.
2. cache warm-up과 Toss token refresh lock의 새 site 동작을 검증한다.
3. Transactional Outbox와 relay를 구현하고 Kafka 중단 중 backlog가 보존되는지 검증한다.
4. scheduler, Kafka consumer와 relay가 active-site generation을 확인하도록 한다.
5. cross-site Kafka 복제가 필요한지 부하·복구 결과로 후속 결정한다.

## 5단계: Traffic, TLS와 인증

1. site 외부 health check에서 member/admin 핵심 경로를 검사한다.
2. public hostname의 DNS TTL과 traffic 전환 방식을 결정한다.
3. 양쪽 site에 같은 hostname의 인증서를 사전 배치한다.
4. OAuth2 issuer, redirect/logout URI, CORS와 cookie 정책을 양쪽에서 검증한다.
5. WebSocket client reconnect와 DNS cache 지연을 test한다.
6. active-site 변경을 감사 가능한 기록으로 남긴다.

## 6단계: Fencing과 자동화

1. application, scheduler, consumer와 database credential을 차단하는 fencing 절차를 구현한다.
2. 두 개 이상의 외부 신호와 일정 지속 시간을 장애 판단 조건으로 사용한다.
3. promotion 전 replication lag와 fencing evidence가 없으면 자동 중단한다.
4. CI/CD와 schema migration freeze를 incident workflow에 포함한다.
5. failover는 승인 gate 이후 실행하고 failback은 항상 별도 승인한다.

## 7단계: 훈련과 운영 전환

1. [Kubernetes→AWS failover](../runbooks/k8s-to-aws-failover.md)를 staging에서 수행한다.
2. AWS에서 write를 발생시킨 뒤 [AWS→Kubernetes failback](../runbooks/aws-to-k8s-failback.md)을 수행한다.
3. login, 사용자 CRUD, 관심 종목, 채팅 저장·이력·WebSocket을 검증한다.
4. replication lag와 실제 RTO/RPO, session 영향, event backlog를 기록한다.
5. 실패 지점과 수동 단계를 자동화 backlog에 반영한다.
6. 두 차례 연속 훈련 성공 후 ADR-006의 승인 여부를 검토한다.

## 검증 시나리오

- Kubernetes control plane과 worker 전체 접근 불가
- Kubernetes application만 장애지만 database는 정상
- 복제 지연이 허용 RPO를 초과한 상태
- AWS promotion 도중 일부 service activation 실패
- DNS 전환 뒤 이전 WebSocket 연결 잔존
- Kafka 중단 중 PostgreSQL write 지속
- AWS active 상태에서 Kubernetes 복구 및 역복제
- Failback 직전 schema version 불일치

## 현재 차단 요소

- RDS/Secrets와 ECS Compute Foundation은 적용됐지만 RDS 정지·ECS ASG `0/0/0` 상태이고 DB Bootstrap·ECS Task/Service·ALB·ElastiCache는 미구현
- production Kubernetes overlay와 외부 ingress 미구현
- versioned migration과 cross-site PostgreSQL replication 미구현
- Outbox 미구현
- 외부 traffic control과 active-site registry 미구현

이 차단 요소가 해소되기 전 런북은 설계 검토용이며 실제 운영 명령으로 사용하지 않는다.
