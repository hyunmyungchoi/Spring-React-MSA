# AWS에서 Kubernetes로 원복

- 상태: 초안, 현재 실행 불가
- 방향: AWS active site → Kubernetes preferred site
- 관련 구조: [재해 복구 아키텍처](../architecture/disaster-recovery.md)

> Failback은 장애 전환의 역순 명령이 아니다. AWS에서 발생한 write를 Kubernetes로 동기화하고 database authority를 다시 이전하는 작업이다. 교차 환경 replication과 fencing 구현 전에는 실행하지 않는다.
>
> Kubernetes↔AWS DR은 Learning 적용 범위에서 제외했다. 이 문서는 운영 환경 후속 학습용 초안이며 Learning 장애 대응에 사용하지 않는다.

## 실행 조건

- Kubernetes control plane, node, ingress, storage와 관측성이 합의된 안정화 시간 동안 정상이다.
- Kubernetes application은 public traffic과 database write가 차단된 상태다.
- AWS가 유일한 writer이며 현재 Git SHA와 schema version이 기록돼 있다.
- AWS→Kubernetes replica rebuild와 catch-up이 완료됐다.
- maintenance write freeze와 promotion 승인 시간이 합의됐다.
- Kubernetes smoke test를 비공개 endpoint에서 수행할 수 있다.

## 기록할 값

| 항목 | 값 |
| --- | --- |
| Incident ID | |
| Failback 승인 시각 | |
| AWS active Git SHA | |
| Schema version | |
| 역복제 시작 위치 | |
| 최종 replication lag | |
| AWS fencing 증거 | |
| Kubernetes promotion 승인자 | |

## 절차

### 1. Kubernetes 복구 검증

1. cluster API, node, storage class, ingress와 DNS target을 확인한다.
2. PostgreSQL을 AWS writer의 replica로 새로 구성한다.
3. application image SHA와 configuration을 AWS active version에 맞춘다.
4. Redis cache/session namespace를 비우고 새 환경으로 준비한다.
5. Kafka/Outbox 처리자는 아직 시작하지 않는다.

### 2. 역복제와 정합성 확인

1. AWS→Kubernetes replication lag와 replay 위치를 관찰한다.
2. table row count, 핵심 aggregate와 sequence 값을 비교한다.
3. 사용자, 역할, 관심 종목, 채팅 방·message의 표본 checksum을 비교한다.
4. schema version과 extension을 확인한다.
5. 정합성 검사가 실패하면 failback을 중단하고 Kubernetes replica를 다시 만든다.

### 3. 비공개 application 검증

1. Kubernetes application을 public traffic 없이 시작한다.
2. database read-only 또는 검증 계정으로 readiness와 read 경로를 확인한다.
3. OAuth issuer, TLS, CORS, cookie와 redirect 설정을 확인한다.
4. AWS와 동일 Git SHA가 아닌 workload가 있으면 전환하지 않는다.

### 4. 최종 write freeze

1. 사용자에게 짧은 maintenance window를 알린다.
2. AWS public write를 차단하고 scheduler, consumer와 relay를 중지한다.
3. 열린 connection이 종료되고 새로운 write가 없는지 확인한다.
4. 마지막 WAL/event 위치까지 Kubernetes가 catch-up했는지 확인한다.
5. 최종 정합성 검사와 AWS fencing 증거를 기록한다.

### 5. Kubernetes database 승격

1. 승인된 방법으로 Kubernetes replica를 writer로 승격한다.
2. database role과 read/write probe를 확인한다.
3. active-site generation을 Kubernetes로 변경한다.
4. AWS application이 Kubernetes writer 또는 AWS의 이전 writer에 write할 수 없도록 유지한다.

### 6. Kubernetes application 활성화

1. gateway, BFF, domain service를 목표 replica로 확장한다.
2. scheduler, consumer와 Outbox relay는 active-site generation 확인 후 시작한다.
3. 내부 smoke test로 로그인, 사용자, 관심 종목, 채팅과 WebSocket을 검증한다.
4. 실패하면 public traffic을 전환하지 않는다.

### 7. Traffic 원복

1. Member/Admin hostname을 Kubernetes endpoint로 전환한다.
2. 외부에서 TLS, OAuth redirect, CORS와 핵심 read/write를 확인한다.
3. DNS cache와 WebSocket reconnect, 5xx와 latency를 관찰한다.
4. session은 새 Redis 기준이므로 재로그인을 허용한다.

### 8. AWS를 새 standby로 재구성

1. Kubernetes를 source of truth로 기록한다.
2. AWS PostgreSQL을 새 Kubernetes writer의 replica로 다시 만든다.
3. AWS application을 warm standby 최소 capacity와 write 금지 상태로 유지한다.
4. 양쪽 image SHA, schema version과 secret rotation 상태를 맞춘다.
5. 실제 failback RTO, write freeze 시간과 data validation 결과를 기록한다.

## 중단 기준

- Kubernetes 안정화 시간이 충족되지 않음
- 역복제 lag가 줄지 않거나 정합성 검사 실패
- AWS write fencing 실패
- schema 또는 image SHA 불일치
- Kubernetes 핵심 smoke test 실패

Kubernetes promotion 후 문제가 발생하면 AWS의 이전 database를 그대로 다시 writer로 켜지 않는다. Kubernetes에서 AWS로 새 replication을 구성하고 [장애 전환 런북](k8s-to-aws-failover.md)의 단일 writer 절차를 다시 수행한다.
