# Kubernetes에서 AWS로 장애 전환

- 상태: 초안, 현재 실행 불가
- 방향: Kubernetes preferred site → AWS warm standby
- 관련 구조: [재해 복구 아키텍처](../architecture/disaster-recovery.md)

> AWS ECS/RDS/ALB와 교차 환경 database replication이 아직 없으므로 이 절차를 현재 환경에서 실행하지 않는다. 리소스 이름과 명령은 DR 구현 후 실제 Terraform output에 맞춰 확정한다.
>
> Kubernetes↔AWS DR은 Learning 적용 범위에서 제외했다. 이 문서는 운영 환경 후속 학습용 초안이며 Learning 장애 대응에 사용하지 않는다.

## 실행 조건

- 두 개 이상의 외부 관측 지점에서 Kubernetes 핵심 경로 장애가 합의된 시간 이상 지속된다.
- incident commander와 database operator가 지정된다.
- CI/CD, Argo CD Sync와 schema migration이 중지된다.
- AWS warm standby의 image SHA, schema version, TLS, secret과 capacity가 검증된다.
- PostgreSQL replication lag와 마지막 replay 위치를 읽을 수 있다.
- 이전 writer를 fencing할 수단과 AWS promotion 승인이 있다.

조건을 만족하지 못하면 장애 전환 대신 서비스 write 차단과 복구를 우선한다.

## 기록할 값

| 항목 | 값 |
| --- | --- |
| Incident ID | |
| 장애 선언 시각 | |
| Kubernetes 마지막 정상 시각 | |
| 배포 Git SHA | |
| Schema version | |
| Replication lag/position | |
| 예상 RPO | |
| Fencing 증거 | |
| Promotion 승인자 | |

## 절차

### 1. 장애 범위 확인

1. Member/Admin public endpoint, database, cluster API와 node 상태를 서로 다른 위치에서 확인한다.
2. AWS, DNS, GitHub/GHCR와 외부 API가 동시에 장애인지 확인한다.
3. 단일 application 오류라면 전체 site failover를 시작하지 않는다.
4. 현재 active site와 마지막 성공 배포 SHA를 기록한다.

### 2. 변경 동결과 Kubernetes fencing

1. GitHub deployment workflow와 Argo CD Sync를 중지한다.
2. Kubernetes가 도달 가능하면 gateway, BFF, domain service, scheduler, Kafka consumer와 relay를 scale down한다.
3. Kubernetes가 도달 불가능하면 network/database 계층에서 기존 write credential과 source route를 차단한다.
4. 이미 열린 connection과 background worker가 더 이상 write하지 못하는지 database에서 확인한다.
5. fencing 증거를 확보하지 못하면 AWS database를 승격하지 않는다.

### 3. 데이터 손실 범위 확인

1. AWS replica의 replay 위치와 lag를 기록한다.
2. 마지막 Kubernetes commit 위치와 비교해 예상 RPO를 계산한다.
3. schema version과 extension 호환성을 확인한다.
4. 합의한 RPO를 초과하면 incident commander가 data loss 수용 또는 복구 대기를 결정한다.

### 4. AWS database 승격

1. 승인된 방법으로 AWS replica를 writer로 승격한다.
2. database role과 read/write probe를 확인한다.
3. Kubernetes source에서 AWS writer로 연결할 수 없도록 fencing을 유지한다.
4. active-site 기록을 새 generation의 AWS로 변경한다.

### 5. AWS application 활성화

1. 승인된 Git SHA의 ECS Service를 목표 capacity로 확장한다.
2. application이 AWS database와 Redis만 참조하는지 확인한다.
3. schema migration을 자동 실행하지 않고 `ddl-auto=validate` 상태를 확인한다.
4. 내부 endpoint에서 readiness가 모두 통과할 때까지 public traffic을 열지 않는다.

### 6. Smoke test

다음 순서로 검증한다.

1. Member/Admin gateway readiness
2. 로그인, callback, CSRF와 logout
3. 사용자 조회와 허용된 write
4. 관심 종목 조회·수정
5. 채팅 방·message history와 새 message 저장
6. WebSocket 연결과 reconnect
7. Outbox backlog 또는 Kafka 비활성 상태의 허용 범위

실패하면 public DNS를 바꾸지 않는다.

### 7. Traffic 전환

1. Member/Admin hostname을 AWS endpoint로 전환한다.
2. TLS 인증서, issuer, redirect URI와 CORS를 외부에서 다시 확인한다.
3. DNS propagation, 기존 WebSocket과 error rate를 관찰한다.
4. 사용자가 재로그인해야 할 수 있음을 공지한다.

### 8. 안정화

1. 5xx, latency, database connection, replication 상태, Redis와 event backlog를 감시한다.
2. Kubernetes를 복구해도 application write를 즉시 열지 않는다.
3. 실제 장애 전환 시각과 RTO/RPO를 기록한다.
4. AWS가 새 source of truth임을 운영 기록과 후속 작업에 명시한다.

## 중단 기준

- 이전 writer fencing 실패
- AWS replica 손상 또는 schema 불일치
- 예상 data loss가 승인 범위를 초과
- AWS 핵심 smoke test 실패
- active site를 단일 값으로 확정할 수 없음

database promotion 뒤에는 단순히 Kubernetes traffic을 다시 여는 방식으로 취소하지 않는다. AWS를 writer로 유지한 채 문제를 해결하거나 [원복 런북](aws-to-k8s-failback.md)에 따라 데이터를 재동기화한다.
