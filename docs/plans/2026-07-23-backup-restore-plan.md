# AWS RDS Backup Restore·전체 Smoke 계획

- 작성일: 2026-07-23
- 대상: AWS Learning `ap-northeast-2` PostgreSQL 16 RDS
- 상태: 격리 PITR Restore·검증·Cleanup, 원본 Full Smoke Runtime ON Apply·curl 검증과 최종 Runtime OFF·RDS 정지 완료
- 원칙: 원본 DB 무변경, Private 격리, Terraform 추적, 읽기 전용 검증, 즉시 비용 종료

## 1. 범위와 완료 조건

이 계획은 RDS Automated Backup의 Point-in-Time Recovery가 실제로 새 DB Instance를 만들고 최근 데이터와 Schema를 복구할 수 있는지 측정한다. Kubernetes↔AWS DR, DNS 전환, Writer Fencing, 원본 Endpoint 교체와 운영 RTO/RPO 보장은 범위에 포함하지 않는다.

완료 조건은 다음과 같다.

- 원본 RDS Identifier, Endpoint, Security Group, Secret과 Terraform 주소를 변경하지 않는다.
- 복원 DB는 별도 Identifier, 전용 Security Group, Private Data Subnet, `PubliclyAccessible=false`를 사용한다.
- 복원 DB를 Application Service, Cloud Map, ALB, Route 53에 연결하지 않는다.
- Private App Subnet의 일회성 Fargate Task만 복원 DB에 접근한다.
- 검증 SQL은 고정된 읽기 전용 Transaction에서 실행하고 PII·행 내용·Secret을 출력하지 않는다.
- Restore 소요 시간과 최신 복원 시점 지연을 관측값으로 기록하되 보장 RTO/RPO로 표현하지 않는다.
- 검증 성공·실패와 관계없이 복원 DB를 먼저 정지하고 별도 Cleanup Saved Plan으로 제거한다.
- Cleanup 뒤 감사 Log Group을 제외한 임시 리소스가 0이고 Terraform이 `No changes`여야 한다.
- 마지막으로 원본 Runtime을 별도 승인으로 켜 전체 HTTPS·OAuth·Session·WebSocket Smoke를 수행한 뒤 다시 OFF한다.

AWS는 PITR이 원본을 덮어쓰지 않고 새 DB Instance를 생성하며, `RestoreTime`과 `UseLatestRestorableTime`을 동시에 사용할 수 없다고 정의한다. 이 훈련은 최신 복원 가능 시점을 사용하는 Terraform `restore_to_point_in_time`으로 고정한다.

- [AWS RDS PITR 안내](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html)
- [AWS CLI RestoreDBInstanceToPointInTime](https://docs.aws.amazon.com/cli/latest/reference/rds/restore-db-instance-to-point-in-time.html)
- [AWS RDS PostgreSQL 가격](https://aws.amazon.com/rds/postgresql/pricing/)

## 2. 2026-07-23 사전 점검 결과

원본 RDS를 시작하지 않고 `DescribeDBInstanceAutomatedBackups.RestoreWindow`를 기준으로 확인했다. 정지 상태의 `DescribeDBInstances` 응답에는 `EarliestRestorableTime`과 `LatestRestorableTime`이 없었으므로 이를 복원 불가로 오판하지 않는다.

| 항목 | 확인 결과 |
| --- | --- |
| 원본 상태 | `stopped` |
| Engine/Class | PostgreSQL `16.14`, `db.t4g.micro` |
| 배치 | Single-AZ, Private, 암호화 |
| Backup 보존 | 7일 |
| Automated Backup RestoreWindow | 존재 |
| 현재 복원 가능 구간 | 약 115.2시간 |
| 최신 복원 시점 지연 | 최초 점검 약 44.1분, Foundation Plan 약 102분, Restore ON Plan 직전 약 147.8분 |
| 사용 가능한 Automated Backup | 1개 |
| 사용 가능한 Snapshot | 4개 |
| 복원 대상 Identifier | 충돌 없음 |
| DB Instance Quota | 1/40 사용 |
| Engine/Class 주문 가능 | 서울 리전에서 확인 |
| Private Data Subnet | 2개 AZ, 모두 Active |

이 값들은 점검 시점의 관측값이다. 실제 Restore Saved Plan을 만들기 직전에 RestoreWindow, 대상 Identifier, Quota와 주문 가능 여부를 다시 확인한다.

Restore ON Saved Plan 직전 재점검에서도 원본은 `stopped`·Private·암호화·Single-AZ·20 GiB gp3·Backup 7일·삭제 보호 상태였다. RestoreWindow 약 115.2시간, Backup 1개·Snapshot 4개, 대상 Identifier 가용, DB Instance Quota `1/40`, 동일 Engine/Class 주문 옵션 4개, 서로 다른 AZ의 Active Data Subnet 2개를 확인했다. RDS 소유 Master Secret은 `active`, Rotation 활성, 삭제 예약 없음이며 마지막 Rotation은 최신 복원 시점보다 이전이다. ECS Service 8개의 Desired·Running·Pending, ASG Desired·Instance는 모두 0이고 NAT는 `available`, 감사 Log는 보존 7일이다.

## 3. 비용·시간 Gate

2026-07-23 AWS Price List 조회 기준 서울 리전 Single-AZ PostgreSQL `db.t4g.micro`는 USD 0.025/시간, gp3는 USD 0.131/GB-월이다. 20 GiB를 2시간 유지하면 DB Instance와 Storage 합계는 약 USD 0.0572다. Fargate, CloudWatch Logs, Backup, 데이터 전송, 세금은 별도지만 이 훈련에서는 작고 짧게 유지한다.

- Restore 실행 승인부터 검증 Task 종료까지 목표 상한은 90분이다.
- 90분 안에 검증이 끝나지 않으면 원인 분석보다 복원 DB 정지를 우선한다.
- 복원 DB는 성공·실패 후 즉시 정지하고 Cleanup 승인을 기다린다.
- 2시간을 넘겨 실행 상태로 두지 않는다.
- 원본 RDS와 Application Runtime은 Restore 훈련 동안 계속 OFF다.

## 4. Terraform 관리 구조

추적되지 않는 CLI 복원 DB를 만들지 않는다. 기존 S3 Remote State의 Main Terraform에 `modules/rds-restore-drill`을 추가하고 다음 Flag를 분리한다.

| 입력 | 의미 | 기본값 |
| --- | --- | --- |
| `enable_rds_restore_drill_foundation` | 7일 감사 Log Group 유지 | `false` |
| `rds_restore_drill_enabled` | 임시 DB·Network·검증 Task 생성 | `false` |
| `rds_restore_drill_use_latest_restorable_time` | 최신 복원 가능 시각 사용 | `true` |
| `rds_restore_drill_identifier` | 고정된 별도 대상 Identifier | `spring-react-msa-learning-postgres-restore-drill` |
| `rds_restore_drill_expires_at_utc` | 임시 리소스 만료 시각 Tag, Restore ON 때 필수 | `null` |
| `rds_restore_drill_validator_image` | Public ECR PostgreSQL Client OCI Digest | 코드에 고정된 Digest |

`rds_restore_drill_enabled=true`는 Foundation, Data Layer, ECS Cluster와 NAT가 유지될 때만 허용한다. Application Runtime은 OFF여야 하며 이를 Terraform Precondition과 Test로 고정한다.

### 영속 Foundation

- `/ecs/<name-prefix>/rds-restore-drill` CloudWatch Log Group 1개
- 보존 7일
- Restore OFF와 Cleanup 뒤에도 감사 증적으로 유지

### 임시 Restore 리소스

- `aws_db_instance` PITR Restore 1개
  - Source Identifier는 현재 원본 RDS Output을 참조
  - `use_latest_restorable_time=true`
  - PostgreSQL 16.14, `db.t4g.micro`, Single-AZ, 20 GiB gp3
  - 기존 DB Subnet Group·Parameter Group 재사용
  - 전용 Restore DB Security Group만 연결
  - `publicly_accessible=false`, 암호화, Performance Insights·Enhanced Monitoring 비활성
  - PITR API가 원본 Backup Retention 7일을 상속하므로 Terraform도 7일로 추적
  - 삭제 보호 비활성, Cleanup 시 Final Snapshot 생략, Automated Backup 삭제
  - `Lifecycle=temporary-restore-drill`, 만료 시각 Tag 기록
- Restore DB Security Group 1개
  - Ingress 5432는 Validator Security Group에서만 허용
  - 기존 ECS/Application Security Group을 Source로 허용하지 않음
- Validator Security Group 1개
  - Ingress 없음
  - Restore DB 5432, DNS와 HTTPS 송신만 허용
- Fargate Validation Task Definition 1개
  - Private App Subnet, Public IP 없음
  - PostgreSQL 16.14 Image를 OCI Digest로 고정
  - Read-only Root, 비특권 UID `70`, Linux Capability `ALL` 제거
  - Fargate가 지원하지 않는 `tmpfs`는 사용하지 않으며 검증 Script는 쓰기 가능한 임시 파일을 요구하지 않음
  - Task Role 없음
  - Execution Role은 Log 기록과 원본 RDS Managed Master Secret 읽기만 허용
- Execution Role·Inline Policy 각 1개

복원 DB는 Cloud Map, ALB Target Group, Route 53, SSM Runtime Parameter와 Application Secret에 게시하지 않는다. 원본 `db_address` Output도 변경하지 않는다.

PostgreSQL PITR의 `RestoreDBInstanceToPointInTime`은 복원 시 새 RDS Managed Master Secret 생성을 지원하지 않는다. 복원 DB에는 원본과 같은 Master Credential이 복구되므로 Validator는 이미 RDS가 관리 중인 원본 Master Secret을 읽기 전용으로 참조한다. 새 Secret, Secret Version 또는 비밀번호를 Terraform State에 만들지 않는다.

## 5. 읽기 전용 검증 계약

Validation Task는 고정 SQL을 `BEGIN TRANSACTION READ ONLY` 안에서 실행한다. 결과는 숫자·Boolean과 SHA-256 실행 Fingerprint만 Log에 남긴다.

1. 연결 TLS와 `transaction_read_only=on`
2. `user_service`, `member_bff`, `stock_service` Schema 3개
3. Bootstrap Master가 소유한 Schema 3개와 로그인 가능한 Application Role 3개
4. Application Role별 자기 Application Table 소유권과 자기 Schema `USAGE`·`CREATE`
5. 교차 Schema 권한 0개
6. `flyway_schema_history` 3개와 V1 Success 3개
7. User Service 2개, Member BFF 2개, Stock Service 1개로 Application Table 총 5개
8. 활성 `ROLE_ADMIN` 사용자 정확히 1명과 `ROLE_USER` 동시 보유
9. 실패 Flyway Migration 0개

Login ID, Email, Username, Password Hash, Token, Cookie, Session ID, 채팅 내용과 전체 행은 출력하지 않는다. SQL Text도 사용자 입력으로 받지 않고 Image에 고정한다.

## 6. 실행 순서와 승인 Gate

### Gate A — 구현·계약 테스트

완료했다.

- `modules/rds-restore-drill`, Root 변수·Output과 고정 Validation Command를 구현했다.
- `terraform fmt -recursive`, `terraform validate`, 전체 mock test를 실행해 `38 passed, 0 failed`를 확인했다. Digest 고정 PostgreSQL Image의 `sh -n`으로 Validator Script 문법도 통과했다. `failure_threshold` 관련 기존 Provider deprecation 경고 외 오류는 없다.
- 원본 RDS는 `stopped`, Private·암호화·Backup 7일·삭제 보호 상태를 유지한다. RDS 소유(`OwningService=rds`) Master Secret은 `active`, Rotation 활성, 삭제 예약 없음이다.
- Foundation OFF Saved Plan `tfplan-rds-restore-drill-foundation-off`는 223,098 bytes, SHA-256 `6aae05fc1761745e1d217a1348ce74138f3be901369cde1111832cf73e4ae188`이다.
- 구조 검증 결과 `module.rds_restore_drill.aws_cloudwatch_log_group.audit["this"]` 생성 1개뿐이며 `1 add, 0 change, 0 destroy`다. 임시 RDS·Security Group·IAM·Task Definition 변경은 0개다.
- 최초 Plan 후보는 현재 적용된 Foundation Flag와 Image Digest를 모두 보존하지 않아 `3 add, 119 destroy`를 제안했으므로 즉시 폐기했다. 유효 Plan은 적용 중인 Flag·Image 입력을 메모리에서 보존해 다시 만들었으며 Secret이나 Image Digest를 문서에 기록하지 않았다.

Gate B에 사용한 승인 문구:

`RDS Restore Drill Foundation OFF Plan 6aae05fc1761745e1d217a1348ce74138f3be901369cde1111832cf73e4ae188 적용 승인`

### Gate B — Foundation Apply

완료했다.

- 승인 SHA-256 `6aae05fc1761745e1d217a1348ce74138f3be901369cde1111832cf73e4ae188`을 Apply 직전 재검증했다.
- 적용 결과는 감사 Log Group 하나만 `1 added, 0 changed, 0 destroyed`다.
- AWS 실상태는 `/ecs/spring-react-msa-learning/rds-restore-drill`, `STANDARD`, 보존 7일, `Lifecycle=persistent-audit`다.
- Terraform State는 249개 주소이며 Restore Drill 주소는 감사 Log Group 하나뿐이다.
- 원본 RDS는 `stopped`, 임시 RDS·Security Group·Task Definition·IAM Role은 모두 0이다.
- 동일 운영 입력 재계획은 `No changes`이며 적용한 Saved Plan 파일은 삭제했다.

다음 승인 문구:

`RDS Restore Drill Restore ON 사전 점검 + Saved Plan 생성 승인`

### Gate C — Restore ON

완료했다.

- 파일: `tfplan-rds-restore-drill-on`
- 크기: 225,896 bytes
- SHA-256: `77e48d5d8a37c5d5495b8478b68a0b8c8fd6de124495525a1022afbec5a2feb7`
- 만료 Tag: `2026-07-23T06:32:31Z` (`2026-07-23 15:32:31 KST`)
- 변경: 임시 RDS 1, Security Group 2, SG Rule 5, Validator Execution Role·Policy 각 1, Fargate Task Definition 1로 정확히 `11 add, 0 change, 0 destroy`
- 기존 RDS·Runtime·Network·Secret·Image·Frontend·DNS·관측성 변경 또는 삭제 0
- Redis Password는 Plan에 없고 실제 Secret Value는 읽거나 출력하지 않았다.
- 서울 리전 Price List 기준 2시간 DB·20 GiB gp3 예상액은 약 USD 0.0572이며 Fargate·Log·Backup·전송·세금은 별도다.

Apply 직전 Hash, RestoreWindow, 대상 Identifier와 원본 OFF 상태를 재확인했다. 만료까지 약 95.9분이 남아 90분 Gate를 통과했다.

1. RestoreWindow·Quota·대상 Identifier를 다시 확인한다.
2. 검증한 Restore ON Saved Plan을 적용한다.
3. 복원 DB `available`까지의 시간을 측정한다.
4. Private Fargate Validation Task를 실행한다.
5. Exit Code 0과 읽기 전용 계약을 확인한다.
6. 성공·실패와 관계없이 복원 DB를 정지한다.

이 Gate는 유료 리소스 생성, 검증 Task 실행과 복원 DB 정지를 한 승인 범위로 묶는다. 원본 RDS는 시작하지 않는다.

적용·검증 결과:

- 승인한 SHA-256을 적용 직전 재검증했고 결과는 정확히 `11 added, 0 changed, 0 destroyed`다.
- 적용 완료 후 같은 SHA-256을 다시 확인하고 Saved Plan 파일을 삭제했다.
- 원본 `spring-react-msa-learning-postgres`는 전체 훈련 동안 `stopped`를 유지했다.
- 복원 시점은 `2026-07-23 11:02:47 KST`이며 새 Private PostgreSQL `16.14` 인스턴스를 만들었다.
- 복원 DB는 `db.t4g.micro`, 암호화, Single-AZ, 20 GiB gp3, `PubliclyAccessible=false`이고 전용 Security Group 하나만 연결됐다.
- PITR이 원본 Backup Retention 7일을 상속했고 Terraform State도 7일을 관측했다. 최초 코드의 0일 계약은 실제 동작에 맞춰 7일로 교정했다.
- Private Fargate Validator는 Public IP 없이 실행돼 Exit Code `0`으로 종료됐다.
- 검증 결과는 Schema 3개, Application Role 3개, Application Table 5개, Flyway V1 3개, 실패 Migration 0개, 활성 관리자 1명이다.
- Log에는 Boolean·개수와 SHA-256 Fingerprint `9c0f611a1bcd68ab83d7dd918e0bef4264e6d005aa471612bfe767d61cac872e`만 기록됐고 PII·행 내용·Secret은 없다.
- Validator 성공 직후 복원 DB 정지를 요청했고 `2026-07-23 14:34:21 KST`에 `stopped`가 됐다.
- ECS 8개 Service는 모두 Desired·Running·Pending `0/0/0`, Cluster 실행 Task는 0이다.
- Terraform State는 260개 주소이며 Restore Drill 주소 12개는 감사 Log 1개와 Cleanup 대기 중인 임시 리소스 11개다.

다음 승인 문구:

`RDS Restore Drill Cleanup Saved Plan 생성 승인`

### Gate D — Cleanup

완료했다.

- 파일: `tfplan-rds-restore-drill-cleanup`
- 크기: 242,611 bytes
- SHA-256: `f01c5588a23810ae038f6e12128c9bd51181179e89616aa4553f4c95c22bc875`
- 생성 시각: `2026-07-23 15:10:11.931 KST`
- 생성 기준 Remote State serial: 93
- 변경: `0 add, 0 change, 11 destroy`
- 삭제 대상: 복원 RDS 1, Security Group 2, SG Rule 5, Validator Execution Role·Policy 각 1, Fargate Task Definition 1
- 감사 Log Group: `no-op`, 보존 7일 유지
- Application·Frontend·Public Domain·관측성·Watchdog·원본 RDS·Network·Image 변경 0
- 출력 변경은 Restore 전용 Identifier·Address·Validator 실행 구성 3개가 `null`로 바뀌는 것뿐이다.
- 복원 RDS는 삭제 보호 비활성, Final Snapshot 생략, Automated Backup 삭제 계약이다.
- Plan 파일은 Git에서 제외되며 실제 Secret Value와 Image Digest를 출력하지 않았다.

Apply 직전 SHA-256, Remote State serial 93, 원본·복원 RDS `stopped`, ECS·ASG 0을 다시 확인했다.

적용·검증 결과:

- 적용 시각: `2026-07-23 15:26:10.793 KST`~`15:28:58.425 KST`
- RDS 삭제 이벤트: `2026-07-23 15:28:35.843 KST`
- 적용 결과: `0 added, 0 changed, 11 destroyed`
- 복원 RDS·Security Group·Validator IAM Role·활성 Task Definition·Automated Backup: 모두 0
- 원본 RDS: `stopped`
- ECS Service 8개: Desired·Running·Pending `0/0/0`, 실행 Task 0
- ASG: Min·Desired·Max·Instance `0/0/0/0`
- 감사 Log Group: `STANDARD`, 보존 7일 유지
- Terraform State: serial 95, 전체 249개 주소, Restore Drill 주소는 감사 Log 1개
- 동일 Cleanup 입력: `No changes`
- 적용 Plan: 승인 SHA-256 재검증 후 삭제

다음 승인 문구:

`RDS Restore Drill 후 원본 Full Smoke Runtime ON 사전 점검 + Saved Plan 생성 승인`

### Gate E — 원본 전체 Smoke

Runtime ON Saved Plan 생성과 검증까지 완료했으며 아직 원본 RDS 시작이나 Plan Apply는 하지 않았다.

- 파일: `tfplan-post-restore-full-smoke-runtime-on`
- 크기: 230,705 bytes
- SHA-256: `1dc1bf8bcc9eccb667659722e3c038f355293f7eb880c33fd14707c8f105f55e`
- 생성 시각: `2026-07-23 15:53:37.608 KST`
- 운영 Gate 만료: `2026-07-23 17:23:37.608 KST`
- 생성 기준 Remote State serial: 95
- 변경: `40 add, 10 change, 0 destroy`, 교체 0
- 생성 40개: Valkey 6, Runtime Alarm 29, Public ALB·HTTPS Listener·Host Rule 2·`origin` A Record 5
- 변경 10개: ECS Service 8개 Desired 1, ASG Min/Max, ECS Cluster Container Insights
- ASG 코드는 Runtime ON `1/1/2` 계약이다. Capacity Provider 관리형 스케일링 때문에 Plan에는 현재 Desired 0이 유지돼 보이지만 Min 1 적용 뒤 실제 Desired 1을 반드시 검증한다.
- Valkey: `7.2`, `cache.t4g.micro`, Node 1, Multi-AZ·Failover·Snapshot 없음, 저장·전송 암호화
- ALB: Internet-facing Application, Public Subnet 2개, HTTPS 443, TLS 1.3/1.2 Policy
- 원본 RDS·Restore 감사 Log·Application/Frontend Foundation·Image·Secret·IAM 삭제 또는 교체 0
- Runtime Secret 7개와 필수 JSON Key 13개, Digest 고정 Image·활성 Task Definition 8개, Stock Client ID를 값 비노출로 검증했다.
- Redis 실제 값은 Saved Plan JSON에 직렬화되지 않았고 Apply 때 다시 Ephemeral Variable로 제공해야 한다.
- Static CloudFront `curl.exe` 6/6 HTTP 200, SNS Email 1개 Confirmed, RDS·Watchdog Alarm 6개 `OK`, Runtime Alarm 0을 OFF 기준선으로 확인했다.
- EC2 1대 기준 약 USD 0.3767/시간, 2시간 약 USD 0.7534이며 EBS·Storage·LCU·Data 처리·전송·세금은 별도다.

Apply 직전 SHA-256, State serial 95, 운영 Gate 만료, RDS `stopped`, ECS·ASG 0과 Redis Ephemeral 입력을 재확인한다. State serial이 바뀌거나 만료 시각을 지났다면 이 Plan을 적용하지 않고 폐기·재생성한다.

승인된 실행에서는 원본 RDS를 먼저 시작해 `available`까지 기다리고 Saved Plan을 적용한다. ECS Service 8개 `1/1/0`, ASG `1/1/2`, Valkey `available`, ALB Target 2/2 `healthy`와 Runtime Alarm 29개를 확인한다. 공개 HTTPS 12개, Root 308, Password Login, Member/Admin OAuth, Session·CSRF, 관리자 1명, WebSocket `CONNECTED/HISTORY/PONG/CHAT_MESSAGE`, REST History, Alarm·SNS를 실제 `curl.exe`로 검증한다. 관리자 Credential은 삭제 예약된 Bootstrap Secret을 복구하지 않고 사용자가 값 비노출 방식으로 다시 제공한다.

검증 후 별도 Runtime OFF Saved Plan을 적용하고 원본 RDS를 다시 정지한다. 전체 단계의 마지막 Terraform 입력은 `No changes`여야 한다.

다음 승인 문구:

`RDS 시작 + Post-Restore Full Smoke Runtime ON Plan 1dc1bf8bcc9eccb667659722e3c038f355293f7eb880c33fd14707c8f105f55e 적용 + HTTPS/OAuth/Session/WebSocket/REST/SNS Alarm curl Smoke 승인`

### Gate E 실행 결과

- 원본 RDS는 `2026-07-23 16:46:52.957 KST`에 시작했고 `16:52:58.209 KST`에 `available`을 확인했다. Private, 암호화, 삭제 보호와 PostgreSQL `16.14`, `db.t4g.micro` 계약을 유지했다.
- 승인된 Saved Plan을 `16:54:23.977 KST`부터 적용해 `17:05:19.483 KST`에 정확히 `40 added, 10 changed, 0 destroyed`로 완료했다. Redis Password는 Secrets Manager에서 메모리 Ephemeral Variable로만 제공하고 즉시 제거했다.
- ECS Service 8개 `1/1/0`, Task·Container `HEALTHY` 8/8, ASG `1/1/2`, Container Instance 1, Valkey `available`, ALB Target 2/2 `healthy`, Cloud Map A 등록 8/8을 확인했다.
- 정적·Readiness·OIDC·BFF HTTPS `curl.exe` 12/12 HTTP 200과 Root 308 Path·Query 보존을 확인했다.
- 무작위 `ROLE_USER`로 Registration 201, Password Login, OAuth Authorization Code, `AUTHSESSIONID`·`BFFSESSIONID`·CSRF, `/bff/user/me`, Heartbeat, WebSocket `CONNECTED/HISTORY/PONG/CHAT_MESSAGE`, REST History 영속성과 양쪽 Logout을 확인했다. 로컬 WebSocket 실행기 인자 전달 오류로 첫 계정은 연결 전 중단됐고 두 번째 계정이 전체 흐름을 통과했으며, 두 합성 계정의 비밀번호는 폐기했다.
- 사용자가 DPAPI 암호화 파일로 제공한 기존 Bootstrap 관리자 자격증명으로 Password Login, OAuth Authorization Code, `AUTHSESSIONID`·`ADMINSESSIONID`, `ROLE_ADMIN+ROLE_USER`, 보호 User/Session/Presence REST, 활성 관리자 정확히 1명, 공개 가입 `404 RESOURCE_NOT_FOUND`와 양쪽 Logout을 확인했다. 자격증명·Cookie 파일은 삭제했고 원본 `sessionId`는 응답에 없었다.
- Runtime Alarm은 생성 직후 29/29 `OK`로 수렴했다. ALB 5xx Alarm을 합성 `ALARM → OK`로 전환해 SNS `Published 2`, Email `Delivered 2`, `Failed 0`을 확인했다.
- Saved Plan과 같은 Foundation·Image·Stock·Redis 입력과 `learning_runtime_enabled=true`로 재계획한 결과 `No changes`, Remote State serial 101이었다.
- 별도의 실제 운영 신호로 RDS `FreeableMemoryLow` Alarm이 `ALARM`이다. 임계값 256MiB에 대해 `2026-07-23 17:44 KST` 최신 5분 최소값은 153.2MiB였다. 기능 Smoke는 성공했지만 임계값이나 인스턴스 사양을 임의 변경하지 않았으며, 다음 ON 전에 `db.t4g.micro` 메모리 여유와 Alarm 정책을 별도 검토한다.
- 적용한 Saved Plan은 승인 SHA-256을 다시 확인한 뒤 삭제해 재사용을 차단했다.

다음 단계에서는 Runtime OFF Saved Plan을 먼저 검토해 ECS·ASG·ALB·Valkey·Runtime Alarm을 종료한 뒤 별도 승인으로 적용하고 원본 RDS를 정지한다.

다음 승인 문구:

`Post-Restore Full Smoke Runtime OFF 사전 점검 + Saved Plan 생성 승인`

### Gate F — 원본 Runtime OFF Saved Plan

사전 점검과 Saved Plan 생성·구조 검증 후 승인된 Plan 적용과 원본 RDS 정지까지 완료했다.

- 파일: `tfplan-post-restore-full-smoke-runtime-off`
- 크기: 251,367 bytes
- SHA-256: `2c6dd23cb9f1acd2977018b83b5f43b4b48c4937d48e1c462ca15ecdf7897e07`
- 생성 시각: `2026-07-23 18:13:25.538 KST`
- 운영 Gate 만료: `2026-07-23 19:43:25.538 KST`
- 생성 기준 Remote State serial: 101
- 변경: `0 add, 10 change, 40 destroy`, 교체 0
- 변경 10개: ECS Service 8개의 Desired Count `1 → 0`, ASG `1/1/2 → 0/0/0`, ECS Container Insights `enabled → disabled`
- 삭제 40개: Runtime Alarm 29개, Valkey·Parameter 6개, Public ALB·HTTPS Listener·Gateway Rule·`origin` Record 5개
- 원본 RDS, Restore Drill 감사 Log, Application Task Definition·Image, Frontend, Secret, SNS Subscription 변경·삭제·교체 0
- 활성 Task Definition 8개의 Digest 고정 Image와 Stock Client ID를 값 비노출로 현재 AWS에서 재구성했다. Redis Password는 이 OFF Plan 입력이나 Saved Plan에 포함하지 않았다.
- `terraform validate`를 통과했고 기존 Cloud Map `failure_threshold` Provider deprecation 경고 외 오류는 없다.
- 사전 점검 시 ECS Service 8개와 ASG `1/1/2`, RDS·Valkey·ALB는 계속 실행 중이다. RDS `FreeableMemoryLow` Alarm도 실제 `ALARM` 상태이며 이번 Plan에서 임계값이나 DB Class를 변경하지 않는다.

Apply 직전 파일 SHA-256, State serial 101, 운영 Gate 만료와 AWS 실상태를 다시 확인한다. State가 바뀌거나 만료 시각을 지났다면 이 Plan을 적용하지 않고 폐기·재생성한다. 승인된 적용에서는 Plan 적용 후 ECS Service·Running/Pending Task·ASG·Container Instance 0, ALB·Valkey·Runtime Alarm 0을 확인하고 원본 RDS를 별도 명령으로 정지한다. 마지막으로 같은 OFF 입력의 `No changes`, RDS `stopped`, Restore Drill 임시 리소스 0과 감사 Log 보존을 검증한다.

다음 승인 문구:

`Post-Restore Full Smoke Runtime OFF Plan 2c6dd23cb9f1acd2977018b83b5f43b4b48c4937d48e1c462ca15ecdf7897e07 적용 + ECS/ASG/ALB/Valkey/Runtime Alarm 종료 + RDS 정지 승인`

### Gate F 실행 결과

- Apply 직전 Account, Plan SHA-256, State serial 101, 운영 Gate와 Runtime ON 실상태를 재검증했다.
- 승인된 Saved Plan을 그대로 적용해 정확히 `0 added, 10 changed, 40 destroyed`로 완료했다.
- ECS Service 8개는 Desired·Running·Pending `0/0/0`, 실행 Task·활성 Container Instance 0, ASG `0/0/0`·Instance 0이며 Container Insights는 `disabled`다.
- Public ALB, HTTPS Listener, Gateway Rule 2개, `origin` Record, Valkey 6개와 Runtime Alarm 29개는 모두 0이다.
- Cloud Map Service 8개·등록 0, Digest 고정 Task Definition 8개 `ACTIVE`, Frontend Bucket 6개·CloudFront 2개, Application Secret 7개, SNS Email Subscription 1개와 Restore Drill 감사 Log 보존 7일을 유지한다.
- 원본 RDS 정지는 `2026-07-23 18:35:52.267 KST`에 요청했고 `18:44:22.196 KST`에 `stopped`를 확인했다. Private·암호화·삭제 보호·Backup 7일을 유지하며 자동 재시작 예정은 `2026-07-30 18:44:16.118 KST`다.
- 같은 OFF 입력의 재계획은 State serial 107 기준 `No changes`다. 최종 State는 249개 주소이며 Restore Drill 주소는 감사 Log Group 하나뿐이다.
- `curl.exe` 검증은 정적 6/6 HTTP 200, Root 308, Member/Admin Runtime API 각각 502로 OFF 경계를 통과했다.
- 적용 Plan은 승인 SHA-256을 마지막으로 재검증한 뒤 삭제해 재사용을 차단한다.
- 다음 Runtime ON 전에 `db.t4g.micro` Freeable Memory 여유와 256MiB Alarm 정책을 별도 검토한다.

## 7. 측정과 기록

다음 시각만 기록하고 Account ID, ARN, Endpoint, Secret과 사용자 식별자는 기록하지 않는다.

- Restore Apply 시작 시각
- 복원 DB `available` 시각
- Validation Task 시작·종료 시각과 Exit Code
- Restore DB 정지 시각
- Cleanup 완료 시각
- Restore 요청 시점과 `LatestRestorableTime`의 차이

`관측 RTO = Validation 성공 시각 - Restore Apply 시작 시각`, `관측 RPO 지연 = Restore 요청 시각 - LatestRestorableTime`으로 계산한다. 한 번의 Learning 훈련 결과이며 운영 보장값이 아니다.

2026-07-23 관측 결과:

| 항목 | 관측값 |
| --- | --- |
| Restore Apply 시작 | `2026-07-23 13:57:00.557 KST` |
| 복원 시점 | `2026-07-23 11:02:47 KST` |
| 복원 DB 생성 시각 | `2026-07-23 14:00:16.506 KST` |
| 복원 DB `available` 최초 관측 | `2026-07-23 14:22 KST` |
| Validation 시작 | `2026-07-23 14:25:11.792 KST` |
| Validation 성공 | `2026-07-23 14:25:35.801 KST`, Exit Code `0` |
| 복원 DB 정지 요청 | `2026-07-23 14:25:58.689 KST` |
| 복원 DB 정지 완료 | `2026-07-23 14:34:21.436 KST` |
| 관측 RTO | 약 28분 35초 |
| 관측 RPO 지연 | 약 2시간 54분 14초 |
| 정지 전환 시간 | 약 8분 23초 |

`available`은 주기 조회의 최초 관측 분 단위 값이고, RTO는 Fargate Task의 AWS 기록 시각으로 계산했다. 이 수치는 정지된 원본 RDS의 Automated Backup 최신 시점과 당시 AWS 제어면 소요 시간을 포함한 단일 관측값이며 SLA·보장값이 아니다.

## 8. 중단 조건

다음 중 하나라도 발생하면 Application Runtime 연결이나 DNS 변경 없이 Restore DB부터 정지한다.

- Restore 대상이 Public이거나 기존 Data/Application Security Group에 직접 연결됨
- 원본 RDS Identifier, Endpoint, Secret 또는 Terraform 주소 변경
- 최신 RestoreWindow가 없거나 7일 Backup 계약 불일치
- Restore가 90분 안에 `available`이 되지 않음
- Validation Task가 쓰기 가능한 Transaction을 사용함
- Log에 PII, Secret, Row Content가 나타남
- Cleanup Plan에 원본 RDS, Frontend, Application Task Definition, Network, IAM Foundation 변경이 섞임

원본 DB로의 Failover나 Endpoint 전환은 이 계획의 실패 대응이 아니다. 그런 작업은 별도 데이터 전환 설계와 승인을 요구한다.
