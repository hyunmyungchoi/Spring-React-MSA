# AWS Terraform 운영 Runbook

이 디렉터리는 `spring-react-msa` 학습 환경의 현재 AWS 인프라를 관리한다. 기본 리전은 `ap-northeast-2`다. Hikari `5/1` 재측정 Runtime ON과 30분 HTTPS/OAuth/Session/WebSocket/REST/SNS Smoke를 완료한 뒤 승인된 Runtime OFF Saved Plan SHA-256 `5e3f9b9a03dceab9eb57491b57b05a8c090693c2c41c10f047ee2c9b86cd779d`을 정확히 `0 added, 10 changed, 40 destroyed`로 적용했다. 현재 ECS Service 8개 `0/0/0`, ASG `0/0/0`, EC2·Task·Container Instance·ALB·Valkey·Runtime Alarm·`origin` Record는 0이고 RDS는 `stopped`, State serial 119·주소 249개이며 동일 OFF 입력은 `No changes`다. 정적 curl 6/6 HTTP 200, Root 308, Member/Admin API 502도 확인했다. 후속 Runtime의 승인된 목표와 미구현 경계는 [AWS Learning Runtime 결정](../../../docs/aws-migration/07-learning-runtime-design.md)을 따른다.

## 현재 상태와 범위

### 적용된 Foundation 기준선

현재 S3 Remote State에는 다음 네트워크와 Budget 리소스가 적용되어 있다.

- VPC 1개 (`10.20.0.0/16`)
- Public Subnet 2개
- Private App Subnet 2개
- Private Data Subnet 2개
- Internet Gateway 1개
- Public/App/Data Route Table 각 1개
- ALB/ECS Application/Data Security Group 각 1개와 명시적 규칙
- 월 USD 50 AWS Budget 1개와 USD 10, 30, 40, 50 알림 기준

### 적용된 ECR/OIDC 확장

승인된 `infra/aws/terraform/tfplan.ecr`을 Apply해 다음 18개 리소스를 추가했다.

- Private ECR Repository 8개와 각각의 Lifecycle Policy 8개
  - `spring-react-msa-learning/spring-member-gateway`
  - `spring-react-msa-learning/spring-admin-gateway`
  - `spring-react-msa-learning/spring-security-authorization-server`
  - `spring-react-msa-learning/spring-user-service`
  - `spring-react-msa-learning/spring-member-community-service`
  - `spring-react-msa-learning/spring-member-stock-service`
  - `spring-react-msa-learning/spring-member-bff-service`
  - `spring-react-msa-learning/spring-admin-bff-service`
- GitHub Actions가 ECR에 이미지를 게시할 때 사용할 IAM Role 1개와 Inline Policy 1개
  - Role: `spring-react-msa-learning-github-ecr-push`
  - 기존 `https://token.actions.githubusercontent.com` OIDC Provider를 재사용한다.
  - 신뢰 범위는 `hyunmyungchoi/Spring-React-MSA` 저장소의 `master` Branch로 제한한다.

각 ECR Repository는 Tag 변경 불가, AES-256 암호화, Push 시 Basic Scan, `force_delete = false`를 사용한다. Tag가 없는 이미지는 1일 뒤 만료하고, Tag가 있는 최신 이미지 5개를 유지한다.

별도 수동 Workflow `.github/workflows/ecr-build-push.yml`도 현재 범위에 포함된다. 기존 GHCR/Kubernetes 배포 경로와 독립적이며, ECR에는 Full Commit SHA Tag만 게시한다.

현재 운영 상태는 다음과 같다.

- `tfplan.ecr` 생성 및 검토: 완료
- 검토된 Plan Apply: 완료 (`18 added, 0 changed, 0 destroyed`)
- GitHub Repository Variable `AWS_ECR_PUSH_ROLE_ARN` 등록: 완료
- ECR 수동 Workflow 실행: 완료 (단일 게시, 동일 SHA Skip, Backend 8개 전체 게시)

### 적용된 ECS Compute Foundation

`modules/ecs-compute`는 ECS on EC2의 실행 기반만 관리한다. `enable_ecs_compute_foundation = true`로 Cluster, EC2 Instance Role/Profile, Launch Template, Auto Scaling Group과 Capacity Provider를 만들고, `learning_runtime_enabled = false`로 ASG의 `min`, `desired`, `max`를 모두 `0`으로 유지한다.

첫 저장 Plan은 `9 added, 0 changed, 0 destroyed`와 ASG `0/0/0`을 확인했지만 Apply 대상으로 사용하지 않는다. 현재 네트워크의 Private App Subnet은 `ap-northeast-2a`와 `ap-northeast-2b`인데 승인했던 `m5a.xlarge`는 서울 리전에서 `2a`와 `2c`에만 제공되어 폐기했다. 기존 Network/Data Layer를 교체하지 않고 두 현재 AZ에 모두 제공되는 `m6i.xlarge`로 변경했고, 전체 테스트와 AZ 검사 후 새 저장 Plan을 생성했다.

새 `tfplan-ecs-compute-foundation`을 승인된 SHA-256으로 Apply했고 결과는 `9 added, 0 changed, 0 destroyed`다. 아래 9개 Terraform 리소스만 추가했으며 Task/Service, ALB와 기존 Foundation/Data Layer 변경은 없었다.

- ECS Cluster 1개와 Cluster-Capacity Provider 연결 1개
- EC2 Auto Scaling Group 1개와 Launch Template 1개
- ECS EC2 Capacity Provider 1개
- ECS Container Instance IAM Role 1개, Instance Profile 1개, AWS 관리형 Policy 연결 2개
- ASG 용량 `0/0/0`, EC2 Instance 0대, Public IP 없음
- ECS 최적화 Amazon Linux 2023 AMI, 두 App AZ 모두에서 제공되는 `m6i.xlarge`, 암호화한 30 GiB `gp3`, IMDSv2 필수
- SSH Ingress 없이 SSM Session Manager 사용

AWS 사후 검증에서 Cluster와 Capacity Provider `ACTIVE`, ASG `0/0/0`, EC2/Container Instance/Task/Service 모두 0개, Launch Template `lt-0f7e0ae4d3537b33f`와 IAM Policy 2개를 확인했다. ECS와 Auto Scaling의 계정 공용 Service-Linked Role 2개는 AWS가 자동 생성했다. 필수 `AmazonECSManaged` ASG Tag를 Terraform 코드에도 명시한 뒤 재계획 `No changes`를 확인했다.

두 Flag의 역할은 분리한다. `enable_ecs_compute_foundation = false`로 되돌리면 기반 리소스 삭제 Plan이 생길 수 있으므로 일반적인 OFF 수단으로 사용하지 않는다. 일상적인 Learning OFF는 Foundation을 유지하고 `learning_runtime_enabled = false`를 사용한다.

### 적용된 Database Bootstrap Task Foundation

승인한 `tfplan-database-tasks-foundation`을 Apply해 다음 4개 Terraform 리소스를 추가했다.

- ECS EC2/`awsvpc` Database Bootstrap Task Definition 1개
- ECS Task Execution IAM Role 1개와 최소 권한 Inline Policy 1개
- 보존 기간 7일의 CloudWatch Log Group 1개

Task는 PostgreSQL `16.14` Public ECR Image를 OCI Digest로 고정하고 읽기 전용 Root Filesystem, UID 70, `/tmp` tmpfs와 TLS 연결을 사용한다. RDS Managed Master Secret은 Bootstrap에만 제공하며 Application Secret 세 곳은 `db_username`, `db_password` JSON Key만 읽는다. IAM은 해당 네 Secret의 `GetSecretValue`와 지정 Log Group 쓰기만 허용하며 Secret 변경 권한은 없다.

Foundation Apply 결과는 `4 added, 0 changed, 0 destroyed`다. 최초 Task Definition Revision 1은 RDS 제한 계정에서 `NOSUPERUSER`와 Schema 소유자 전환을 요구해 Bootstrap 실행에 실패했다. RDS 호환 방식으로 Role의 LOGIN·비밀번호만 동기화하고 Schema는 Bootstrap 관리 계정이 소유하도록 수정한 뒤, 불변 Task Definition을 Revision 2로 교체했다.

별도 SHA-256 승인으로 User Service, Member BFF, Stock Service Secret에 최초 `AWSCURRENT` Version을 생성했다. 각 값은 승인된 사용자명과 AWS가 생성한 40자 무작위 비밀번호만 포함하며 화면, Git, Terraform State에 저장하지 않았다. 재실행 시 기존 정상 Version 세 개를 모두 Skip했다.

짧은 Runtime ON에서 Revision 2 Bootstrap Task가 Exit Code `0`으로 완료됐다. Bootstrap 직후 읽기 전용 검증 Task도 Exit Code `0`이었으며 안전한 Role 3개, Schema 3개, 자기 Schema 권한 조합 3개, 교차 Schema 권한 0개, Application Table 0개를 실제 RDS에서 확인했다. 이후 Digest 고정 Migration Task 3개를 적용·실행하고 Flyway V1 사후 검증까지 완료했다.

DB Migration 검증 후에는 승인된 OFF Plan으로 ASG `0/0/0`과 RDS 정지를 확인했고 당시 Remote State는 107개 주소였다. 이후 Application Foundation과 Runtime ON을 적용해 ASG `1/1/2`, EC2·Container Instance 1대, ECS Service 8개 `1/1/0`, RDS·Valkey `available`, Public ALB Target 2/2 `healthy`를 검증했다. 당시 최종 Runtime OFF 뒤 ASG·EC2·Container Instance·Task는 0이고 RDS는 `stopped`였다. Bootstrap Revision 2와 Migration Revision 1 세 개는 계속 `ACTIVE`다. 현재 상태는 문서 상단의 2026-07-22 관측성 수명주기 완료 후 Runtime OFF 기준이며 동일 입력 재계획은 `No changes`다.

### 현재 AWS에 아직 생성하지 않은 대상

- MSK/Kafka

Route 53/ACM/TLS는 Bootstrap State Role 권한과 별도 Global DNS State를 적용해 기존 Hosted Zone Import, ACM 인증서 2개 `ISSUED`, DNS 검증 CNAME 4개를 완료했다. Runtime OFF Custom Domain/A·AAAA/HTTPS API Origin도 적용해 정적 curl 6/6, Root 308, TLS와 재계획 `No changes`를 검증했다.

### 관측성 Foundation과 Runtime 수명주기

기존 CloudWatch Log Group 12개는 7일 보존이고 월 USD 50 Budget 1개에는 실제 비용 USD 10/30/40/50 알림이 있다.

`modules/observability`는 Runtime OFF에도 유지되는 SNS Operations Topic·Policy·Email Subscription, RDS CPU/Freeable Memory/Free Storage Alarm 3개와 RDS Event Subscription을 정의한다. RDS 정지 중 지표 누락은 `notBreaching`으로 처리한다. 루트 플래그는 기본 `false`이고 Data Layer와 유효한 추적 제외 알림 Email이 있어야 활성화할 수 있다.

첫 Runtime OFF Plan Apply는 SNS Operations Topic 1개 생성 뒤 Topic Policy의 `SNS:*`가 AWS `InvalidParameter`로 거부돼 중단됐다. wildcard 관리자 문장을 제거하고 RDS Event·CloudWatch Alarm에 `sns:Publish`만 허용했으며, 정책 Action과 운영 tfvars 격리 테스트를 보강했다. 부분 state 기준 복구 Plan `tfplan-observability-foundation-off-policy-recovery`는 190,225 bytes, SHA-256 `fb8beee57f39b463268d11b0341953dc3340216c7c182076067ab21ae5546de8`이고 승인된 그대로 `6 added, 0 changed, 0 destroyed`로 적용했다. 실상태는 관측성 state 7개, `sns:Publish` Policy 2개 문장, Email Subscription 1개 `Confirmed`, RDS Alarm 3개 `OK`, RDS Event Subscription `active`다. SNS 직접 발행은 CloudWatch 전달 3건·실패 0건과 Gmail 실제 수신을 확인했다.

`enable_runtime_observability=true`는 Runtime ON에서만 일반 Container Insights와 ECS·ALB Alarm을 활성화한다. Backend 8개 서비스별 CPU·Memory·Running Task Alarm 24개와 ALB 자체 5xx, 두 Gateway Target Group의 Target 5xx·Unhealthy Host Alarm 5개를 합쳐 29개다. `learning_runtime_enabled=false`이면 Container Insights는 `disabled`이고 Runtime Alarm은 0개이므로 Runtime OFF 비용 기준을 유지한다. Enhanced Container Insights는 사용하지 않는다. 2B `enable_runtime_watchdog=true`는 Runtime을 켜지 않고 15분마다 RDS·ASG·ECS를 읽기 전용 점검하며, 6시간 Runtime·RDS 자동 재시작 24시간 전·OFF 중 RDS 실행을 상태 전환 한 번씩만 기존 SNS에 알린다. Watchdog 자체는 Heartbeat·Lambda Error·EventBridge Failed Invocation Alarm 3개로 감시한다. Python 단위 테스트 `7 passed`, 모듈 계약과 전체 mock 테스트는 `30 passed, 0 failed`다. 운영 절차는 [`docs/runbooks/aws-observability.md`](../../../docs/runbooks/aws-observability.md)를 따른다.

2026-07-23 Watchdog 최초 Plan 적용은 Lambda 예약 동시성 1이 계정 한도 10·최소 미예약 동시성 10 조건을 위반해 부분 중단됐다. 예약 동시성을 제거한 Source SHA `2f9566bfee08504153a2775b11ac554ceffe53cc`의 Recovery Plan SHA-256 `1ae5c62eced0fe89b718a8a005f555d3c2287c5fd792959abb5b6394d8dfcf35`는 EventBridge Target과 Lambda Permission만 `2 create, 0 update, 0 destroy`로 포함했고 적용 결과도 `2 added, 0 changed, 0 destroyed`였다. Lambda는 계정 미예약 풀, Python 3.12 ARM64, 128MB, timeout 30초를 사용한다. Baseline Invoke·DynamoDB inactive 3개·Heartbeat `1.0`·Alarm→SNS `Delivered 1`을 검증했고 최종 Watchdog Alarm 3개는 `OK`, 동일 OFF 입력은 `No changes`다.

Runtime OFF 관측 Plan을 준비할 때 `terraform.tfvars` 기본값만 사용하면 적용된 Application·Frontend·Public Domain Foundation이 삭제 후보가 될 수 있다. 운영 Plan은 세 Foundation Flag와 현재 적용된 Application Image digest 8개, Stock Client ID를 항상 보존한다. 2026-07-21 최종 Saved Plan은 Source SHA `ed33b8323f8706c6dbd322404c55cbe9bfbd8582`, SHA-256 `90a5be1ca3f71e07e689be1b77045c6e2a8bd66995945748053c43c6a13fbba5`이며 AWS 리소스 변경 없이 출력 2개만 추가했다. 적용 결과는 `0 added, 0 changed, 0 destroyed`이고 실상태와 동일 입력 재계획은 Runtime OFF·`No changes`다.

2026-07-22 Runtime ON Saved Plan은 Source SHA `d2dc46b062be1deef7c0c4a55ff8a87a4c914579`, 202,653 bytes, SHA-256 `46c96d2b993afc8afe8c354ad93cf2c949cd0cee5d4973e5659629af3384ba45`이며 `40 added, 10 changed, 0 destroyed`로 적용했다. Container Insights와 Alarm 29개, ECS·ASG·ALB·Valkey·RDS Runtime, HTTPS curl 12/12, OAuth·Session·CSRF·Logout, WebSocket `CONNECTED`·`HISTORY`·`PONG`·`CHAT_MESSAGE`·REST 영속성을 검증했다. SNS Email Subscription이 `Deleted`인 drift는 단일 교체 Plan SHA-256 `383b514ea0cba9eeac4572c5a909b493489e434cb722b5203203aaeff7eb930d`를 `1 added, 0 changed, 1 destroyed`로 적용해 복구했다. 최종 구독 `Confirmed`, Alarm `ALARM → OK`, SNS `Published 2`·`Delivered 2`·`Failed 0`, ON 재계획 `No changes`다. 적용된 Saved Plan 두 개는 Hash 검증 후 삭제했다.

후속 Runtime OFF Saved Plan은 Source SHA `d4f923bedbf8375ae6ff9badbf7ab24c05c591d4`, 218,898 bytes, SHA-256 `f44f5f7a22be792fdf17a1d0b5e7761ae57bd8ec12168c54a4b1c773698d89fd`이며 승인된 파일을 `0 added, 10 changed, 40 destroyed`로 적용했다. ECS Service 8개와 ASG를 0으로 내리고 Container Insights를 `disabled`로 바꿨으며 Runtime Alarm 29개, ALB·HTTPS·Rule·`origin` 5개와 Valkey 6개만 삭제했다. 실제 ECS·Task·Container Instance·ASG·ALB·Valkey·`origin`·Runtime Alarm은 모두 0이고 RDS는 별도로 정지해 `stopped`다. SNS Email Subscription 1개 `Confirmed`, RDS Alarm 3개, Task Definition·Image 8개와 Frontend 6/2는 유지했다. 정적 curl 6/6, Root 308, OFF API 502, 동일 입력 `No changes`를 확인하고 적용 Plan을 삭제했다. RDS는 2026-07-29 02:33:31 KST 자동 재시작 예정이므로 그 전에 필요 여부를 다시 판단한다.

### 적용된 Data Layer

검토한 `tfplan-data-layer`로 다음 Terraform 리소스 10개를 추가했다.

- PostgreSQL `16.14` RDS `db.t4g.micro` 1개
  - Single-AZ, Private 접근, 암호화된 고정 20 GiB `gp3`
  - Automated Backup 7일, 삭제 보호, Final Snapshot, RDS Managed Master Password
  - Performance Insights와 Enhanced Monitoring 비활성
- Private Data Subnet 2개를 사용하는 DB Subnet Group 1개
- `postgres16` DB Parameter Group 1개
- 값이 없는 Secrets Manager Container 7개

Terraform에는 `aws_secretsmanager_secret_version`이 없으므로 실제 비밀번호와 Token은 State에 넣지 않는다. RDS가 만드는 Master Secret을 포함하면 Apply 뒤 Secrets Manager 과금 대상은 총 8개다. RDS와 Secret에는 `prevent_destroy`를 적용했고, `enable_data_layer=false`로 되돌리는 것은 단순 OFF가 아니라 삭제 시도이므로 허용하지 않는다. Runtime OFF는 RDS 정지 절차를 사용한다.

### RDS Restore Drill

`modules/rds-restore-drill`은 원본 RDS를 변경하지 않는 격리 PITR 훈련을 Terraform으로 추적한다.

- `enable_rds_restore_drill_foundation=true`, `rds_restore_drill_enabled=false`는 보존 7일의 `/ecs/spring-react-msa-learning/rds-restore-drill` 감사 Log Group만 유지한다.
- Restore ON은 Data Layer·ECS Compute Foundation·NAT가 유지되고 Application Runtime이 OFF일 때만 허용한다. RFC3339 UTC 만료 시각도 필수다.
- 임시 복원 DB는 별도 Identifier, 기존 Private DB Subnet Group, 전용 Security Group, `PubliclyAccessible=false`, Single-AZ `db.t4g.micro`, 암호화된 20 GiB `gp3`로 제한한다.
- PITR API가 원본 Backup Retention 7일을 상속하므로 복원 DB도 7일로 추적한다. Cleanup은 Automated Backup을 삭제하고 Final Snapshot을 생략한다.
- Fargate Validator는 Private App Subnet·Public IP 없음, OCI Digest 고정 PostgreSQL 16 Client, Read-only Root, UID `70`, Linux Capability `ALL` 제거, Task Role 없음으로 실행한다. Fargate가 지원하지 않는 `tmpfs`는 사용하지 않는다.
- Validator는 원본 RDS Managed Master Secret을 Execution Role로 읽는다. PostgreSQL PITR은 복원 시 새 RDS Managed Master Secret을 만들 수 없고 복원 DB가 원본 Master Credential을 유지하기 때문이다. Terraform은 새 Secret이나 Secret Version을 만들지 않는다.
- 고정 SQL은 `BEGIN TRANSACTION READ ONLY`와 TLS를 강제하고 Schema 3개, Application Role 3개, Table `2/2/1` 총 5개, Flyway V1 3개, 교차 권한 0개, 활성 관리자 1명을 검증한다. Log에는 개수와 SHA-256 Fingerprint만 남긴다.
- 복원 DB를 Cloud Map·ALB·Route 53·SSM Runtime Parameter·Application Secret에 게시하지 않는다.

`terraform fmt -recursive`, `terraform validate`, 전체 mock test 결과는 `38 passed, 0 failed`다. Digest 고정 PostgreSQL Image에서 Validator Script의 `sh -n` 문법 검사도 통과했다. AWS Provider의 기존 Cloud Map `failure_threshold` deprecation 경고 외 오류는 없다.

2026-07-23 Foundation OFF Saved Plan `tfplan-rds-restore-drill-foundation-off`는 223,098 bytes, SHA-256 `6aae05fc1761745e1d217a1348ce74138f3be901369cde1111832cf73e4ae188`다. Apply 직전 Hash와 감사 Log Group 단일 `create`를 재검증했고 결과는 `1 added, 0 changed, 0 destroyed`다. AWS Log Group은 `STANDARD`, 보존 7일, `Lifecycle=persistent-audit`; State는 249개 주소다. 원본 RDS는 `stopped`, 임시 Restore 리소스는 0, 동일 운영 입력은 `No changes`이며 적용한 Plan 파일은 삭제했다.

후속 Restore ON 사전 점검은 RestoreWindow 약 115.2시간, 최신 시점 지연 약 147.8분, 대상 Identifier 가용, DB Instance Quota `1/40`, 동일 Engine/Class 주문 가능, 서로 다른 AZ의 Active Data Subnet 2개, Master Secret `active`·Rotation 활성·삭제 예약 없음, NAT `available`, ECS/ASG 0을 확인했다. 서울 리전 Price List의 `db.t4g.micro` USD 0.025/시간과 gp3 USD 0.131/GB-월 기준 2시간 DB·20 GiB Storage 예상액은 약 USD 0.0572다.

Saved Plan `tfplan-rds-restore-drill-on`은 225,896 bytes, SHA-256 `77e48d5d8a37c5d5495b8478b68a0b8c8fd6de124495525a1022afbec5a2feb7`, 만료 `2026-07-23T06:32:31Z`다. 정확히 임시 RDS 1, Security Group 2, SG Rule 5, Validator Execution Role·Policy 각 1, Fargate Task Definition 1만 `11 add, 0 change, 0 destroy`로 포함했고, Apply 결과도 `11 added, 0 changed, 0 destroyed`였다. 적용 완료 후 같은 SHA-256을 재검증하고 Plan 파일을 삭제했다.

복원 시점은 `2026-07-23 11:02:47 KST`다. 복원 DB는 PostgreSQL `16.14`, `db.t4g.micro`, 암호화, Private, Single-AZ, 20 GiB gp3, 원본에서 상속된 Backup Retention 7일과 전용 Security Group 하나를 사용했다. 최초 코드의 0일 계약은 Provider State와 AWS 실상태 7일에 맞춰 교정했다. Private Fargate Validator는 Exit Code `0`으로 Schema 3개, Application Role 3개, Application Table 5개, Flyway V1 3개, 실패 Migration 0개와 활성 관리자 1명을 확인했다. Apply부터 검증 성공까지 관측 RTO는 약 28분 35초, 복원 시점 지연은 약 2시간 54분 14초였다. 검증 직후 정지를 요청해 복원 DB는 `2026-07-23 14:34:21 KST`에 `stopped`가 됐고 원본 RDS도 계속 `stopped`다. ECS 8개 Service는 `0/0/0`, 실행 Task는 0이며 Restore Drill State 주소 12개 중 감사 Log 1개와 임시 리소스 11개가 Cleanup 승인을 기다린다.

Cleanup Saved Plan `tfplan-rds-restore-drill-cleanup`은 242,611 bytes, SHA-256 `f01c5588a23810ae038f6e12128c9bd51181179e89616aa4553f4c95c22bc875`, 생성 기준 State serial 93이다. 정확히 복원 RDS 1, Security Group 2, SG Rule 5, Validator Execution Role·Policy 각 1, Fargate Task Definition 1만 `0 add, 0 change, 11 destroy`로 포함했다. 승인 적용 결과도 `0 added, 0 changed, 11 destroyed`다. 복원 RDS·SG·IAM·활성 Validator Task Definition·Automated Backup은 모두 0이고 감사 Log는 `STANDARD`·보존 7일로 유지한다. State는 serial 95·249개 주소이며 Restore Drill 주소는 감사 Log 하나뿐이다. 동일 입력은 `No changes`이고 적용 Plan은 Hash 재검증 후 삭제했다.

Restore Plan을 `terraform.tfvars`만으로 생성하면 현재 AWS에 적용된 Foundation Flag와 Application Image 입력이 누락될 수 있다. 실제로 첫 후보가 `3 add, 119 destroy`를 제안해 즉시 삭제했다. 운영 Plan은 현재 적용된 모든 Foundation Flag, Image Digest 8개와 Stock Client ID를 메모리 입력으로 보존해야 하며, 감사 Foundation 단계에서 기대 주소 외 변경이나 하나라도 `delete`가 있으면 폐기한다. Secret과 Image Digest는 문서나 Plan 검토 출력에 노출하지 않는다.

### 적용된 Application Foundation과 Runtime ON 코드

- `modules/application-runtime`: Backend 8개 Digest 고정 Task Definition, ECS Service, 서비스별 최소 권한 Execution Role, 7일 CloudWatch Log Group, Cloud Map Private DNS와 Gateway Target Group을 관리한다.
- `modules/cache`: Runtime ON에만 Valkey 7.2 `cache.t4g.micro` 단일 Node와 SSM Redis Host Parameter를 만든다. 저장·전송 암호화와 RBAC를 사용하고 User Group에는 Password 인증 Application User만 포함하며, OFF에서는 세션/Cache와 함께 삭제한다.
- `modules/ecs-compute`: 한 `m6i.xlarge`에 8개 `awsvpc` Task ENI를 수용하도록 Account `awsvpcTrunking=enabled`를 관리한다.
- `learning_runtime_enabled=false`: ASG와 Service를 0으로 유지하고 ALB·Valkey를 만들지 않는다.
- `learning_runtime_enabled=true`: ASG `1/1/2`, Service별 Task 1개, Public ALB 1개와 Valkey Node 1개를 만든다.

Valkey Password는 Secrets Manager에서 읽어 `TF_VAR_redis_password` Ephemeral Variable로만 전달한다. Provider `passwords_wo` Write-only Argument를 사용하므로 Plan과 State에 저장하지 않는다. Application Task는 실제 Password를 `/spring-react-msa/learning/shared/redis`에서 직접 주입받고 Endpoint만 SSM `String`으로 읽는다.

Runtime Secret은 Terraform Apply와 분리한다. `Initialize-LearningRuntimeSecrets.ps1`이 기존 DB Secret JSON을 보존하면서 OAuth/Toss Key를 추가하고, Redis Password와 내부 API Token은 AWS가 생성한다. 스크립트는 Secret Value를 화면에 출력하지 않으며 실행 전 별도 SHA-256 승인을 받는다.

2026-07-19 실행 기록은 다음과 같다.

- Runtime Secret 스크립트 SHA-256 `a9d14e959d4f3b289634d6f70a7ace261d429feb15db429542388f6f29f0ad79`를 승인·재확인하고 Secret 6개를 초기화했다. 실제 Value를 출력하지 않고 `AWSCURRENT`와 JSON Key 계약 6/6을 확인했다.
- Source SHA `a7b3e0387c6817fd5a781ccf3ac532e04f38c9e1`의 ECR Digest 8개를 메모리 입력으로 고정한 Application Foundation OFF Plan SHA-256은 `a3189e4cb3503fa0cd5c7a75b4a94f799809b67adb13cc6bb47c71a1a71a7e19`였다. 검토한 56개 생성만 Apply했고 실행 용량과 RDS 상태는 바꾸지 않았다.
- 최초 Apply 뒤 빈 `health_check_custom_config`를 AWS가 상태에 남기지 않아 Cloud Map Service 8개 교체가 반복 계획되는 문제를 발견했다. `failure_threshold=1`을 명시하고 계약 테스트를 추가했다.
- 교정 Plan SHA-256 `92e2fdc2c0dffbbf1bc7d1634ee3c8c04bfb24d6edb49793a0131dcb3fc12a85`는 Cloud Map Service 8개 교체와 ECS Service 8개 참조 갱신만 포함했다. Apply 결과는 `8 added, 0 changed, 8 destroyed`였고 실제 AWS custom health 8/8, ECS Service 8개 `0/0/0`, ASG `0/0/0`, RDS `stopped`, State 165개 주소와 재계획 `No changes`를 확인했다.

현재 AWS Provider는 AWS가 항상 1로 처리하는 `failure_threshold` 명시를 deprecated 경고로 표시한다. 그러나 현재 6.x에서는 빈 Block이 누락되어 상태가 수렴하지 않았으므로 값을 명시한다. 다음 Provider Major에서 Attribute를 제거할 때는 실제 AWS custom health와 교체 없는 재계획을 먼저 검증한다.

2026-07-19 Runtime ON 첫 Saved Plan과 복구 상태는 다음과 같다.

- 첫 Plan SHA-256: `5f288ebe5ccfb6a053e6245713fd03b9857105cb0101df297442c00117e31417`
- 첫 Apply는 ASG `1/1/2`, Valkey Application User·Subnet Group·Parameter Group까지 반영한 뒤 AWS가 Valkey 엔진의 `default` 사용자에 `no-password-required`를 허용하지 않아 중단됐다. 이 Plan은 State 변경으로 무효화했으며 재사용하지 않는다.
- 실패 직후 실제 상태: RDS `available`, ASG `1/1/2`, EC2 Instance 1대 `InService/Healthy`, ECS Service 8개 `0/0/0`, Public ALB·Valkey Node 0개
- 교정: Valkey User Group에서 별도 `default` User를 제거하고 Password 인증 Application User 한 개만 포함했다. Terraform Validate와 Test `18 passed, 0 failed`를 확인했다.
- 적용한 복구 Plan: `tfplan-application-runtime-on-recovery`
- 복구 Plan SHA-256: `3cd3eeae10b6cdb3182d0c1e727056f1a87570eea279e73ea98fd84db8b1980e`
- 변경: `7 create, 8 update, 0 delete/replace`
- 생성: Public ALB 1, HTTP Listener 1, Host Rule 2, Valkey User Group 1, 단일 Valkey Replication Group 1, Redis Host SSM String 1
- 갱신: ECS Service 8개 Desired `1`
- 무변경: RDS, ASG, NAT/EIP, VPC/Subnet/Route, Task Definition/Image, IAM과 이미 생성된 Valkey Application User·Subnet/Parameter Group
- Redis Password는 Secrets Manager에서 Ephemeral Variable로만 읽었으며 복구 Plan JSON과 저장 Plan 파일에 포함되지 않음을 실제 값 비교로 확인했다.

- 복구 Apply 결과: `7 added, 8 changed, 0 destroyed`. Valkey `available`, RDS `available`, ASG `1/1/2`, ALB Target 2/2 `healthy`를 확인했다.
- Smoke 진단: Member BFF는 Valkey Application User 이름을 주입하지 않아 `WRONGPASS`, Stock Service는 `TOSS_API_CLIENT_ID`가 비어 Binding 실패로 반복 종료됐다. 두 서비스만 수동 Desired 0으로 일시 정지했다.
- 교정: Redis를 사용하는 Authorization Server·Stock Service·Member BFF·Admin BFF에 `SPRING_DATA_REDIS_USERNAME=spring-msa`를 명시하고, Runtime ON에서 Stock Toss Client ID가 비어 있으면 Plan이 실패하도록 Precondition을 추가했다. Terraform Validate와 Test `19 passed, 0 failed`를 확인했다.
- 서비스 계약 교정 Plan: `tfplan-runtime-on-service-contract-fix`
- 서비스 계약 교정 Plan SHA-256: `cf805d691a67cc0f40087f56a561427ab6790894bc5c6a8ff9180b4fc7b5f847`
- 변경: Task Definition 4개 새 Revision 교체, ECS Service 4개 갱신. Member BFF·Stock Service Desired는 0에서 1로 복구한다.
- 무변경: Data, ALB, Valkey, RDS, NAT/VPC/Network, IAM, Image와 OCI Digest
- Redis/Toss Secret 원본 값은 교정 Plan JSON과 파일에 포함되지 않았고, Toss Client ID는 Git 제외 로컬 환경에서 읽는 비밀이 아닌 학습용 값이다.

서비스 계약 교정 Plan은 승인된 SHA-256 그대로 Apply해 `4 added, 4 changed, 4 destroyed`로 완료했다. Destroy는 이전 Task Definition Revision 등록 해제이며 Data 삭제는 없었다. 최종 Service 8개 `1/1/0`, Task/Container Health와 Digest 8/8, Cloud Map A 등록 8/8, ALB Target 2/2, curl 6/6, 재계획 `No changes`를 확인했다. 배포 중 ASG가 `1/2/2`로 확장됐지만 Managed Scaling이 안정화 뒤 `1/1/2`로 자동 축소했다. [AWS CLI 동작](https://docs.aws.amazon.com/cli/latest/reference/autoscaling/update-auto-scaling-group.html)과 [ECS Managed Scaling](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-auto-scaling.html)을 따른다.

Runtime OFF Saved Plan `tfplan-runtime-off-after-application-smoke`의 승인된 SHA-256 `f9f827bccc98316d1ccbbbf1169588f11232bba88c45f2a875db9a8de501940c`를 그대로 Apply해 `0 added, 9 changed, 10 destroyed`로 완료했다. ECS Service 8개와 ASG를 0으로 내리고 Public ALB 계열 4개, 폐기 가능한 Valkey 계열·Redis Host Parameter 6개만 삭제했다. RDS, Task Definition, Service/Cloud Map/Target Group Foundation, IAM, Log와 Secret 변경은 0개였다. 사후 실제 상태는 Task·Container Instance·EC2 0, ASG `0/0/0`, Cloud Map Service 8개와 등록 0, Task Definition 8개 `ACTIVE`, Target Group 2개 유지, RDS `stopped`다. RDS 자동 재시작 예정은 2026-07-26 05:15:28 KST이며 재계획은 `No changes`다.

Public Domain Runtime ON Full Smoke용 Saved Plan `tfplan-runtime-on-public-domain-smoke`, 189,515 bytes, SHA-256 `93ae37575bac8f8cacd6b29661df26d483d1a3c4852c4eab51e08621c6e55f2c`를 승인된 그대로 Apply해 `11 added, 9 changed, 0 destroyed`로 완료했다. Public ALB·HTTPS 443 Listener·Host Rule 2개·`origin` A Alias·Valkey 계열 6개를 만들고 ECS Service 8개와 ASG `1/1/2`를 기동했다. HTTP Listener와 RDS·CloudFront·Network·Task Definition/Image 변경은 없었고 Redis Password는 Ephemeral 입력 뒤 제거했다. 실제 AWS에서 ECS·Rollout 8/8, Container Instance 1, Valkey·RDS `available`, ALB `active`, Target 2/2 `healthy`와 재계획 `No changes`를 확인했다. 적용 Plan은 재사용하지 않고 삭제했다.

HTTPS 정적·Health·OIDC·BFF 12개는 모두 HTTP 200이고 Root 308·Path/Query 보존·TLS를 확인했다. 무작위 `ROLE_USER`로 Member Password Login·OAuth Authorization Code·BFF Session·CSRF Heartbeat·Logout을 검증했고, 같은 사용자의 Admin OAuth는 `admin_role_required`로 차단되며 Admin Session이 제거됐다. 당시 실제 `ROLE_ADMIN` 성공 Login은 최초 관리자 Bootstrap 전이라 미검증이었고, 후속 Admin Bootstrap Smoke에서 성공을 확인했다. WebSocket은 인증 Cookie와 Origin을 포함해 Upgrade됐지만 `CONNECTED` 전 1002로 종료됐다. Member Gateway 전용 `ws://` Route와 ECS·Docker·Kubernetes 환경 변수 계약을 교정한 Source SHA `5fc26bdc355d0417d29bbc1941a0d9c0996e4200`은 Gateway 단독 GHCR Build Once·ECR 무재빌드 Promote와 OCI Digest 일치를 검증했다. 새 Task Definition 적용 뒤 공개 경로 재검증도 후속 단계에서 완료했다. 진단용 Smoke 계정 4개는 비밀번호가 폐기된 감사 데이터로 남아 있다.

Gateway 교정 Runtime OFF Saved Plan `tfplan-member-gateway-websocket-off`, 183,948 bytes, SHA-256 `b62a335d71e69ce82c55f0cb169873948303f5a2f3bc095fcf313cc77ef6683c`를 승인된 그대로 Apply해 `1 added, 1 changed, 1 destroyed`로 완료했다. 검증된 ECR Digest와 전용 WebSocket URI를 사용하는 Member Gateway Task Definition만 교체하고 Desired Count 0인 ECS Service 참조를 갱신했다. 새 Task Definition `ACTIVE`, Image·환경 변수와 Service 연결, ECS 전체 `0/0/0`, ASG `0/0/0`, ALB·Valkey·`origin` 0, RDS `stopped`와 OFF 입력 재계획 `No changes`를 확인했다. 다른 7개 서비스, 실행 용량, Data·Network·IAM·Secret 변경은 없었으며 적용한 Saved Plan은 삭제했다.

교정 Gateway 공개 경로 재검증용 Runtime ON Saved Plan `tfplan-runtime-on-websocket-smoke`, 185,315 bytes, SHA-256 `23b137e9b740b26d69c22a6f686c4ccc0db78e52a0e4e7497a8eff0d28383e88`을 승인된 그대로 Apply해 `11 added, 9 changed, 0 destroyed`로 완료했다. Public ALB HTTPS·Host Rule·`origin` 5개와 Valkey 6개를 만들고 ECS 8개와 ASG를 기동했다. ECS·Rollout 8/8, ASG `1/1/2`, Target 2/2, Valkey·RDS `available`, curl 12/12와 Root 308, Password Login·OAuth·BFF Session·CSRF Heartbeat가 통과했다. WebSocket은 Gateway에서 BFF까지 도달했지만 BFF Downstream Handshake 403으로 1002 종료돼 Member Public Origin 누락을 추가 원인으로 확정했다. 적용 Plan은 삭제했다.

Member BFF WebSocket Origin 계약을 AWS·Docker·Kubernetes에 추가하고 Terraform 23/23·Compose·Kubernetes 검증을 통과했다. Runtime ON 교정 Plan `tfplan-member-bff-websocket-origin-fix`, 194,907 bytes, SHA-256 `8bb7f98cf022b9513c73afc7d1ea82567103af9bee5eaef95eb7c51eec7ad758`을 승인된 그대로 Apply했다. 기존 Image Digest를 유지한 Member BFF Task Definition 교체와 Desired 1 ECS Service 갱신만 `1 added, 1 changed, 1 destroyed`로 수행했고 Revision 3이 `HEALTHY`·`COMPLETED`로 수렴했다. curl Registration·Password Login·OAuth·BFF Session·CSRF·Logout이 통과했고 공개 WebSocket `CONNECTED`·`HISTORY`·`PONG`·자체 `CHAT_MESSAGE`와 REST History 영속성도 확인했다. ECS·Rollout·Container Health 8/8, RDS·Valkey `available`, 동일 ON 입력 재계획은 `No changes`다. 진단 계정 2개가 추가돼 비밀번호를 폐기한 전체 정리 대상은 7개이며 적용 Plan은 삭제한다.

후속 Runtime OFF Saved Plan `tfplan-runtime-off-after-websocket-origin-fix`, 193,775 bytes, SHA-256 `e2c7f108b3197696a7e9e4b2cde1fc3d717fc10cc81028bbaee7573107835831`을 승인된 그대로 Apply했다. ECS Service 8개와 ASG 1개 축소, Public ALB·HTTPS·Gateway Rule 2개·`origin`·Valkey 6개 삭제만 `0 added, 9 changed, 11 destroyed`로 완료했고 RDS·Task Definition·Image·Frontend·Network·IAM·Secret은 변경하지 않았다. 실제 ECS·Running Task·Active Container Instance·ASG Instance·ALB·Valkey·`origin`은 모두 0, RDS는 `stopped`다. 정적 curl 6/6·Root 308·Runtime OFF API 502와 동일 OFF 입력 재계획 `No changes`를 확인했으며 적용 Plan은 삭제한다.

Runtime OFF Plan `tfplan-runtime-off-after-public-domain-smoke`, 195,052 bytes, SHA-256 `94aae90a5306d6b1c9aa8f597ca938213cbd39e9c872b0cc92212277608e53f3`를 승인된 그대로 Apply해 `0 added, 9 changed, 11 destroyed`로 완료했다. ECS Service·ASG 실행 용량과 ALB/HTTPS/`origin`/Valkey만 내렸고 RDS·CloudFront·Network·Task Definition/Image·IAM·Secret 변경은 0개였다. 사후 ECS/Container Instance/ASG Instance 0, ASG `0/0/0`, Public ALB·Valkey·`origin` 0과 RDS `stopped`를 확인했다. RDS Backup 7일·삭제 보호를 유지하며 자동 재시작 예정은 2026-07-26 19:44:07 KST다. OFF 재계획은 `No changes`, 정적 curl 6/6 HTTP 200, Root 308, Runtime OFF API 502와 TLS를 확인했고 적용 Plan은 삭제했다.

### 적용된 Frontend Hosting Foundation

- `modules/frontend-hosting`: 여섯 독립 Private S3 Bucket, OAC 한 개, Member/Admin CloudFront 두 개, SPA 경로 함수 두 개와 GitHub Frontend 배포 Role을 관리한다.
- `enable_frontend_hosting=false`: 적용된 Frontend 리소스 삭제 Plan이 되므로 사용하지 않는다.
- `enable_frontend_hosting=true`: 기존 Runtime과 분리해 Frontend Hosting Foundation만 Plan에 포함한다.
- `.github/workflows/aws-frontend-deploy.yml`: 선택한 Frontend Workspace·Build Script·Bucket·Invalidation 경로만 사용한다.
- `spring-stock-web` 선택 시 Stock 전용 Bucket과 `/stock`, `/stock/*`만 바뀌며 Member·Community 배포는 실행하지 않는다.

pnpm `10.0.0` Frozen Lockfile로 여섯 Lint·Build와 Entry 문서를 확인했고 Python 선택/Workflow 계약 14개, Terraform `validate`와 `test` 20/20을 통과했다. Saved Plan `tfplan-frontend-hosting-foundation`은 143,862 bytes, SHA-256 `f49031685f65ff8ed8274316e34e1c195431a3d1912ac279114b14b23f0aa5e8`이었으며 승인한 파일을 Apply해 `49 added, 0 changed, 0 destroyed`로 완료했다. S3 보안 계약 6/6, CloudFront `Deployed` 2/2·Origin 3+3·경로 네 개·Function 연결 6개, Function `DEPLOYED` 2/2, OAC `always/sigv4`, OIDC `master` Trust와 재계획 `No changes`를 확인했다. 적용된 Saved Plan은 재사용하지 않고 삭제했다. GitHub Variable 3개를 값 비노출 방식으로 연결하고 Source SHA `f29249373feae470e2c30758e3245d43d22fef25`의 [Run 29677216377](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29677216377)에서 Frontend 6개 Job과 필수 단계 24/24를 성공시켰다. S3 Entry `no-cache`·Asset immutable metadata 6/6과 CloudFront 정적 curl 6/6 HTTP 200도 확인했다. ACM·Route 53·Custom Domain과 ALB API Origin은 이번 Apply에 넣지 않았다.

### 적용된 Private App 송신 확장

검토한 저장 Plan으로 다음 5개를 추가했으며 기존 리소스 변경과 삭제는 없었다.

- 다음 ON에도 같은 주소를 재사용하도록 유지하는 Elastic IP 1개
- `enable_nat_gateway = true`일 때 생성하는 Zonal NAT Gateway 1개
- 두 Private App Subnet이 공유하는 Route Table의 `0.0.0.0/0` NAT 경로 1개
- Private App Route Table에만 연결하는 S3 Gateway Endpoint 1개
- ECS Security Group의 외부 HTTPS TCP 443 송신 규칙 1개

`enable_nat_gateway = false`로 Apply하면 NAT Gateway와 기본 경로를 제거하지만 Elastic IP와 S3 Gateway Endpoint는 유지한다. 서울 리전 현재 가격 기준 NAT Gateway는 시간당 USD 0.059와 처리량 GB당 USD 0.059, Public IPv4는 시간당 USD 0.005다. 월 730시간 상시 ON 고정비는 약 USD 46.72이며 Data 처리비와 전송비는 별도다. S3 Gateway Endpoint에는 추가 요금이 없다. 운영 중에도 [Amazon VPC 가격](https://aws.amazon.com/vpc/pricing/)과 [S3 Gateway Endpoint 문서](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html)를 확인한다.

Apply 후 NAT `available`, EIP 연결, Private App 기본 경로 `active`, S3 Endpoint의 App Route Table 단독 연결, Private Data 외부 경로 0개와 Terraform 재계획 `No changes`를 검증했다. ECS Compute Foundation Apply 뒤 Remote State에는 관리 리소스 88개와 Data Source 3개, 총 91개 주소가 있다.

## 사전 요구사항

- Terraform `1.15.x`
- HashiCorp AWS Provider `6.x`
- AWS CLI v2
- 기본 리전 `ap-northeast-2`
- S3 Remote Backend와 전용 State Role에 접근할 수 있는 AWS CLI 인증
- Git에서 제외한 `backend.s3.hcl`과 `terraform.tfvars`
- Account에 등록된 GitHub OIDC Provider `https://token.actions.githubusercontent.com`

Terraform 실행 주체에는 다음 권한이 필요하다.

- 적용된 Foundation을 Refresh할 VPC/EC2 Network와 Budgets 조회 권한
- ECR Repository, Tag, Scan 설정, Lifecycle Policy를 생성하고 조회할 ECR 권한
- 기존 GitHub OIDC Provider를 조회할 `iam:GetOpenIDConnectProvider` 권한
- 전용 IAM Role을 생성·조회·Tag하고 Inline Policy를 등록·조회할 IAM 권한
- RDS Instance, Subnet Group, Parameter Group을 생성·조회·Tag할 RDS 권한
- Secret Container를 생성·조회·Tag할 Secrets Manager 권한과 RDS Managed Master Secret 생성에 필요한 권한
- ECS Cluster/Capacity Provider, EC2 Launch Template, Auto Scaling Group을 생성·조회·Tag할 권한
- ECS 최적화 AMI를 조회할 `ssm:GetParameter`, EC2가 사용할 IAM Instance Profile과 Policy Attachment를 관리할 IAM 권한
- EC2에 Instance Role을 전달할 `iam:PassRole` 권한
- Private S3 Bucket·Policy·Versioning·Encryption을 관리할 S3 권한
- CloudFront Distribution·Function·OAC를 관리하고 배포 완료를 조회할 CloudFront 권한

예를 들어 ECR에는 `ecr:CreateRepository`, `ecr:DescribeRepositories`, `ecr:ListTagsForResource`, `ecr:PutLifecyclePolicy`, `ecr:GetLifecyclePolicy`에 해당하는 권한이, IAM에는 `iam:CreateRole`, `iam:GetRole`, `iam:TagRole`, `iam:PutRolePolicy`, `iam:GetRolePolicy`에 해당하는 권한이 필요하다. 향후 변경이나 Rollback에서 리소스를 수정·삭제하려면 그 작업에 맞는 추가 권한도 별도 검토한다.

GitHub 연결 단계에는 `hyunmyungchoi/Spring-React-MSA`의 Repository Variable을 설정할 GitHub 권한과 인증된 `gh` CLI가 필요하다. 이 권한은 Terraform Plan 확인이나 Apply에는 필요하지 않는다.

Terraform과 Provider 선택은 `versions.tf`와 `.terraform.lock.hcl`로 고정한다. Lock 파일은 재현 가능한 Provider 설치와 공급망 검증을 위해 커밋하고, `.terraform/` 디렉터리는 커밋하지 않는다.

## AWS CLI 인증

장기 Access Key를 Terraform 파일에 직접 넣지 않는다. 이 Learning 계정의 현재 운영자는 제한된 IAM 사용자 `hyun-terraform-admin`으로 AWS CLI의 브라우저 로그인을 사용하고, GitHub Workflow는 OIDC로 단기 자격 증명을 받는다.

PowerShell 예시:

```powershell
aws configure set region ap-northeast-2
aws login
aws sts get-caller-identity
aws configure get region
```

다른 계정이나 조직 환경에서는 IAM Identity Center Profile과 `aws sso login --profile <profile-name>`을 사용할 수 있다. 어떤 방식이든 Plan 전에 호출 주체가 승인된 Account와 IAM Principal인지, Region이 `ap-northeast-2`인지 확인한다.

Access Key가 필요한 예외 상황에서도 키를 코드, `terraform.tfvars`, README, Git 기록에 넣지 않는다. `aws sts get-caller-identity`의 Account ID는 외부 보고 시 마스킹한다.

## 초기화와 공통 검증

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

terraform init
terraform fmt -check -recursive
terraform validate
terraform test
```

`terraform.tfvars`는 Git에서 제외한다. 현재 적용된 Budget 설정, 알림 이메일, NAT와 ECS ON/OFF 값이 들어 있으므로 민감한 계정별 설정으로 취급하고, 새 Plan Gate가 끝나기 전에 변경하거나 덮어쓰지 않는다. State에도 리소스 식별자와 Budget 이메일이 기록될 수 있다.

ECS Compute Foundation의 현재 입력은 다음 의미를 가진다.

```hcl
enable_ecs_compute_foundation = true
learning_runtime_enabled      = false
ecs_instance_type             = "m6i.xlarge"
```

첫 번째 값은 기반 리소스를 Plan에 포함하고, 두 번째 값은 실제 유료 EC2 용량을 0대로 유지한다. Runtime을 켤 때는 `learning_runtime_enabled = true`로 변경한 새 저장 Plan을 별도로 검토하고 승인받아야 한다.

Frontend Hosting은 Runtime Flag와 독립적이다.

```hcl
enable_frontend_hosting = false
```

Frontend Hosting Foundation이 적용됐으므로 모든 후속 Plan은 `enable_frontend_hosting=true`를 유지하고 기존 Foundation·Application 입력과 `learning_runtime_enabled=false`도 그대로 보존한다. 이 값을 `false`로 내리면 여섯 Bucket과 두 Distribution의 삭제 Plan이 되므로 일상적인 Frontend OFF 수단으로 사용하지 않는다.

Database Task Foundation의 현재 입력은 다음과 같다.

```hcl
enable_database_tasks_foundation = true
database_migration_images = {
  user-service  = "<ECR URL>@sha256:<verified digest>"
  member-bff    = "<ECR URL>@sha256:<verified digest>"
  stock-service = "<ECR URL>@sha256:<verified digest>"
}
```

첫 값으로 DB Role/Schema Bootstrap Task Definition과 최소 권한 IAM/7일 Log Group을 적용했고, Secret 초기화와 실제 RDS Bootstrap 검증까지 완료했다. 두 번째 Map은 Git에서 제외된 `terraform.tfvars`에만 실제 ECR Digest Reference를 저장하며 세 Key 전체가 있어야 한다. 승인한 저장 Plan으로 Flyway Migration Task Definition 3개와 서비스별 IAM/Log Group을 적용하고 실제 Migration까지 검증했다. Secret Version 생성과 Task 실행은 Terraform Apply와 별도의 승인 단계이며 [DB Bootstrap·Flyway Runbook](../../../docs/runbooks/aws-database-bootstrap-and-flyway.md)을 따른다.

## 검토 완료·적용된 Flyway Build Once·Migration 기록

- Source SHA: `f0c88e32b883c391dcf993dfbf40839312de0f39`
- GHCR Build Once: GitHub Actions Run `29642831008`, 서비스 3개 Test·Build·Digest 검증 성공
- ECR Promote: GitHub Actions Run `29643089643`, 재빌드 없이 복사하고 GHCR·ECR Digest 일치 확인
- Migration Foundation Plan SHA-256: `aefce55a1598c6aef45eeadc7753be9876362293e051584d4d125af030eac959`
- Migration Foundation Apply: `12 added, 0 changed, 0 destroyed`
- Runtime ON Plan SHA-256: `a5bd0f244804683f40e6f8732fbfbcd24378b51afb9179f3facb9144afca960d`
- Flyway 실행: User Service → Member BFF → Stock Service, 모두 Exit Code `0`
- 실제 RDS 검증: History 3, V1 성공 3, Table 5, 올바른 소유자 5, 실패·교차 권한·Seed Row 0
- Runtime OFF Plan SHA-256: `d201b1122da65502450391326e9ef468290509f63bbe399bc0b8f03ee01af328`
- 최종 상태: ASG `0/0/0`, EC2/ECS Task 0, RDS `stopped`, Remote State 107개, `No changes`

## 검토 완료된 `tfplan.ecr` Apply 기록

> 이 절은 이미 완료된 Apply의 승인 기록이다. `tfplan.ecr`을 다시 Apply하지 않으며, 이후 변경은 아래의 일반 Plan 검토 절차로 새 Plan을 생성하고 별도 승인을 받는다.

이 절은 이미 생성하고 검토한 정확한 Repository 상대 경로 `infra/aws/terraform/tfplan.ecr`에만 적용한다. 이 Plan은 Git에서 제외된 Local Artifact이며, 현재 예상 크기는 33,850 bytes다.

- 예상 SHA-256: `2c149a0c3215b6acaeba6a99883eca4fcc10fb6d6fa09f620368c78bb2c24603`
- 예상 요약: `Plan: 18 to add, 0 to change, 0 to destroy.`
- 예상 추가: ECR Repository 8개, Lifecycle Policy 8개, IAM Role 1개, Inline Policy 1개
- 적용된 Foundation 변경 또는 교체: 없음
- 삭제: 없음

Apply 승인 요청 전과 승인 직후 Apply 직전에 모두 정확한 경로, Hash, Plan 내용을 다시 확인한다.

```powershell
Set-Location C:\Portfolio

$planPath = "C:\Portfolio\infra\aws\terraform\tfplan.ecr"
$expectedSha256 = "2c149a0c3215b6acaeba6a99883eca4fcc10fb6d6fa09f620368c78bb2c24603"
$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $planPath).Hash.ToLowerInvariant()

if ($actualSha256 -ne $expectedSha256) {
  throw "tfplan.ecr SHA-256 mismatch; do not apply."
}

Set-Location C:\Portfolio\infra\aws\terraform
terraform show -no-color tfplan.ecr
```

`terraform show`에서 다음을 직접 확인한다.

- 정확히 `18 to add, 0 to change, 0 to destroy`
- 위의 Backend ECR Repository 8개와 Lifecycle Policy 8개만 추가됨
- GitHub OIDC Role 1개와 ECR Push Inline Policy 1개만 추가됨
- OIDC Audience가 `sts.amazonaws.com`이고 Subject가 `repo:hyunmyungchoi/Spring-React-MSA:ref:refs/heads/master`임
- `ecr:GetAuthorizationToken`만 `Resource = "*"`를 사용하고 나머지 Image 권한은 8개 ECR ARN으로 제한됨
- NAT Gateway, EIP, VPC Endpoint, ECS, EC2, ALB, RDS, ElastiCache, MSK 또는 Foundation 변경·교체·삭제가 없음

경로, Hash, 요약 또는 내용이 하나라도 다르면 멈춘다. 이 Gate의 명시적 승인은 변경되지 않은 이 저장 Plan에만 유효하다.

명시적 승인을 받은 뒤에만 저장 Plan 자체를 Apply한다.

```powershell
terraform apply tfplan.ecr
```

> 이 검토 완료 Gate에서는 인자 없는 `terraform apply`를 실행하지 않는다. 인자 없는 명령은 새 Plan을 생성하므로 검토된 `tfplan.ecr`과 다른 변경을 적용하여 승인 경계를 우회할 수 있다.

이 저장소 작업에서 Apply를 자동 실행하지 않는다.

## 향후 변경의 일반 Plan 검토 절차

이 절은 현재의 `tfplan.ecr` Gate와 별개다. 현재 Plan이 Apply된 뒤 새로운 변경을 검토하거나, 코드·State·입력 변수·자격 증명·저장 Plan 중 하나가 변경되어 현재 Gate가 무효가 됐을 때 사용한다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

terraform plan -out=tfplan.<purpose>
terraform show -no-color tfplan.<purpose>
```

Add/Change/Destroy 수, 예상 리소스, IAM 범위, 비용, 교체와 삭제를 검토하고 그 정확한 새 Plan에 대해 다시 명시적 승인을 받는다. 승인 뒤에는 검토한 파일 이름을 지정해 `terraform apply tfplan.<purpose>`로 실행한다. 저장 Plan은 `tfplan`과 `tfplan.*` 패턴으로 Git에서 제외하며 공유하지 않는다.

## 검토 완료·적용된 새 `tfplan-ecs-compute-foundation`

> 이 절은 2026-07-18에 검토·승인·Apply한 기록이다. State와 코드가 이미 변경됐으므로 이 저장 Plan을 다시 Apply하지 않는다.

- 절대 경로: `C:\Portfolio\infra\aws\terraform\tfplan-ecs-compute-foundation`
- 크기: 62,407 bytes
- SHA-256: `c749c7e114ac68a1a0ae4fbc3ecb6b8a7cd3e8e4479d046b40d2ccad408e6fec`
- 요약: `9 added, 0 changed, 0 destroyed`
- ASG 용량: `min=0`, `desired=0`, `max=0`
- Instance 계약: `m6i.xlarge`, ECS 최적화 AL2023, 암호화한 30 GiB `gp3`, IMDSv2 필수, Public IP 없음
- Subnet: 현재 `ap-northeast-2a`와 `ap-northeast-2b`의 Private App Subnet 2개
- AZ 제공 검사: `m6i.xlarge`가 두 선택 AZ에서 모두 제공됨
- 계정 Standard On-Demand vCPU 한도: 32 vCPU, 설계 최대 2대는 8 vCPU
- 기존 리소스 변경·교체·삭제: 없음
- 적용 결과: `9 added, 0 changed, 0 destroyed`
- 사후 검증: Cluster/Capacity Provider `ACTIVE`, ASG와 EC2 `0`, 재계획 `No changes`

추가 대상은 ECS Cluster 1, Cluster-Capacity Provider 연결 1, ASG 1, Launch Template 1, ECS Capacity Provider 1, EC2 Instance Role 1, Instance Profile 1과 Policy Attachment 2다. Task Definition, ECS Service, ALB와 실행 EC2 Instance는 포함하지 않았다. Apply 과정에서 AWS가 ECS와 Auto Scaling의 계정 공용 Service-Linked Role 2개를 자동 생성했으며 이는 Terraform의 9개 관리 주소와 별도인 AWS 서비스 동작이다.

Apply 과정에서 ECS는 Capacity Provider가 관리하는 ASG에 필수 `AmazonECSManaged` Tag를 자동으로 추가한다. 사후 재계획에서 이 정상적인 서비스 Tag를 제거하려는 드리프트가 한 번 확인되어 Terraform의 ASG Tag 집합에도 명시했고, 이후 재계획에서 변경이 없어야 한다.

Apply 직전 다음 검사를 실행했다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

aws sts get-caller-identity
aws configure get region

$expectedSha256 = "c749c7e114ac68a1a0ae4fbc3ecb6b8a7cd3e8e4479d046b40d2ccad408e6fec"
$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath .\tfplan-ecs-compute-foundation).Hash.ToLowerInvariant()

if ($actualSha256 -ne $expectedSha256) {
  throw "tfplan-ecs-compute-foundation SHA-256 mismatch; do not apply."
}

terraform show -no-color .\tfplan-ecs-compute-foundation
```

당시 Hash와 Plan 내용에 대한 명시적 승인을 받은 뒤 다음 저장 Plan만 지정해 Apply했다.

```powershell
terraform apply .\tfplan-ecs-compute-foundation
```

## 폐기한 첫 `tfplan-ecs-compute-foundation` 검토 기록

> 이 절은 Apply 승인 대상이 아니다. 사전 가용성 검사에서 Instance Type과 AZ의 불일치를 발견해 저장 Plan 파일을 삭제했으며, 아래 Hash에 대한 Apply 승인을 요청하거나 재사용하지 않는다.

- 이전 Artifact도 같은 파일명을 사용했지만 아래 Hash의 파일은 당시 삭제했으며 승인 대상이 아님
- 크기: 62,263 bytes
- SHA-256: `62f5700ba1ba297828899cbf4fd4e364248d1139c20876020ed238ecd945648a`
- 요약: `9 added, 0 changed, 0 destroyed`
- ASG 용량: `min=0`, `desired=0`, `max=0`
- 삭제·교체: 없음

Plan 내용 자체는 Cluster, Capacity Provider 연결, Launch Template, ASG, ECS Capacity Provider와 EC2용 IAM 리소스만 추가했고 Task Definition, ECS Service, ALB, RDS/NAT 변경 또는 EC2 실행 용량은 포함하지 않았다. 그러나 `m5a.xlarge`가 Private App B의 `ap-northeast-2b`에 제공되지 않으므로 이 Plan은 운영 가능성 검사를 통과하지 못했다. 새 Instance 선택을 코드·테스트·문서에 반영한 뒤 파일명과 Hash가 다른 새 Plan으로 다시 검토한다.

## 검토 완료·적용된 `tfplan-data-layer` 기록

> 이 절은 2026-07-18에 검토·승인·Apply한 기록이다. State가 변경됐으므로 이 저장 Plan을 다시 Apply하지 않는다.

- 절대 경로: `C:\Portfolio\infra\aws\terraform\tfplan-data-layer`
- 크기: 50,570 bytes
- SHA-256: `2829c5adf181d1017bc4d0e2135f543a410fe22ac7a06d8a4088d0c6a43b4987`
- 적용 결과: `10 added, 0 changed, 0 destroyed`
- 추가: RDS 1, DB Subnet Group 1, DB Parameter Group 1, 빈 Secret Container 7
- 기존 Foundation/ECR/OIDC/NAT 변경·교체: 없음
- 삭제: 없음

Apply 전 호출 주체, Region, Plan Hash와 내용을 다음과 같이 확인했다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

aws sts get-caller-identity
aws configure get region

$expectedSha256 = "2829c5adf181d1017bc4d0e2135f543a410fe22ac7a06d8a4088d0c6a43b4987"
$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath .\tfplan-data-layer).Hash.ToLowerInvariant()

if ($actualSha256 -ne $expectedSha256) {
  throw "tfplan-data-layer SHA-256 mismatch; do not apply."
}

terraform show -no-color .\tfplan-data-layer
```

승인된 저장 Plan만 지정해 Apply했으며 RDS `available`, PostgreSQL 16.14, Private/암호화/Single-AZ, 20 GiB `gp3`, Backup 7일, 삭제 보호, Monitoring 비활성, Managed Master Secret `active`를 확인했다. Application Secret 7개는 Version 0개로 실제 값이 없음을 검증했다. Apply 뒤 재계획은 `No changes`였다. 이후 RDS는 `stopped`로 전환했고, AWS의 7일 자동 재시작 시각을 운영 점검 대상으로 기록했다.

## Apply 이후 GitHub 연결과 ECR 게시

검토된 `tfplan.ecr` Apply, Role ARN의 GitHub Repository Variable 등록, Workflow의 `master` 반영과 Backend 8개 게시까지 완료됐다. 아래 명령은 등록과 수동 게시 절차의 기록이다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
$roleArn = terraform output -raw github_actions_ecr_role_arn

Set-Location C:\Portfolio
gh variable set AWS_ECR_PUSH_ROLE_ARN `
  --repo hyunmyungchoi/Spring-React-MSA `
  --body $roleArn
```

수동 Workflow는 Default Branch에 있어야 실행할 수 있다. Workflow는 PR #3으로 `master`에 반영했고, `spring-user-service` 단일 게시와 같은 SHA 재실행의 Skip 동작을 먼저 검증했다.

```powershell
gh workflow run ecr-build-push.yml `
  --repo hyunmyungchoi/Spring-React-MSA `
  --ref master `
  -f deploy_target=spring-user-service

gh run watch --repo hyunmyungchoi/Spring-React-MSA
```

단일 서비스 검증 뒤 `deploy_target=all` 실행으로 Backend 8개를 게시했다. 전체 게시 실행은 GitHub Actions run `29561837114`, 기준 SHA는 `3564959efa1637e60fe72f009d4fa1a5809de01b`다. ECR Repository는 Immutable SHA Tag, Push 시 Basic Scan, Tagged Image 5개와 Untagged Image 1일 보존 정책을 유지한다.

## Destroy

학습 종료 후에는 먼저 별도의 삭제 Plan을 만들고 검토한다.

```powershell
terraform plan -destroy -out=tfplan.destroy
terraform show -no-color tfplan.destroy
```

삭제 Plan에 대해 별도 명시적 승인을 받은 뒤 검토한 Plan 파일을 지정해 Apply한다. ECR Repository는 `force_delete = false`이므로 이미지가 남아 있으면 삭제되지 않는다. VPC도 연결된 ENI, ALB, NAT Gateway, ECS, RDS 같은 후속 리소스가 남아 있으면 삭제에 실패한다. 의존 관계와 데이터 백업을 확인하고 역순으로 삭제한다.

## 네트워크와 송신 제한

- Public Route Table만 Internet Gateway 기본 경로를 가진다.
- Public Subnet도 자동 Public IPv4를 부여하지 않는다.
- Private App Route Table은 단일 NAT 기본 경로와 S3 Gateway Endpoint 경로를 가진다.
- Private App의 ECR API, CloudWatch, Secrets Manager와 외부 HTTPS 호출은 NAT를 사용하고 S3 Traffic은 Gateway Endpoint를 사용한다.
- Private Data Route Table에는 VPC Local 경로만 있으며 Internet 기본 경로와 Endpoint 경로가 없다.

현재 적용한 송신 구조는 다음과 같다.

- Public Subnet A의 고정 EIP 기반 단일 Zonal NAT Gateway
- 두 Private App Subnet이 같은 NAT Gateway를 공유
- Private App Route Table에 S3 Gateway Endpoint 연결
- Private Data Subnet에는 Internet 기본 경로 없음
- Interface Endpoint와 AZ별 NAT Gateway는 Learning 범위에서 제외

## Security Group 요약

| 출발지 | 목적지 | 포트 | 용도 |
|---|---|---|---|
| CloudFront origin-facing Prefix List | ALB SG | 443 | Public Domain 적용 뒤 HTTPS API Origin |
| ALB SG | ECS SG | 8080, 8090 | Member/Admin Gateway |
| ECS SG | ECS SG | 8079, 8087, 9000, 8081, 8083, 8084 | 내부 서비스 호출 |
| ECS SG | Data SG | 5432, 6379, 9092 | PostgreSQL, Redis, 미래 Kafka |
| ECS SG | AWS Public API/외부 서비스 | 443 | ECR, CloudWatch, Secrets Manager, Toss API |

Application, Data, Kafka, SSH Port는 Internet에서 직접 접근할 수 없다. SG 응답 Traffic은 상태 기반으로 허용되므로 별도의 임시 고포트 수신 규칙이 필요하지 않다.

## 예상 비용

적용된 Foundation의 VPC, Subnet, Route Table, Security Group에는 별도 시간당 리소스 요금이 없고 Internet Gateway 자체도 무료다. 현재 NAT Gateway·Public IPv4 3개·RDS·EC2 1대·Valkey·Public ALB 실행 요금이 발생한다. 아래 금액은 세전 목록가 추정치이며 Credit과 Free Tier를 반영하지 않는다.

- 일반 AWS Budget Monitoring과 Email 알림: 무료
- ECR Apply 이후: 주로 저장된 Image 용량, Data Transfer, Scan 방식에 따라 비용 발생
- 현재 계획의 Push 시 Basic Scan: Account 전체 Enhanced Scan을 활성화하지 않음
- 현재 Public IPv4 3개: NAT EIP 1개와 두 AZ Public ALB IPv4 2개, 합계 USD 0.015/시간·월 730시간 기준 약 USD 10.95
- 현재 NAT Gateway 1개: 서울 리전 기준 USD 0.059/시간, 월 730시간 기준 약 USD 43.07와 GB당 USD 0.059 처리비
- NAT와 EIP 상시 ON 고정비: 약 USD 46.72/월, Data 처리비와 전송비 별도
- RDS `db.t4g.micro` 실행: USD 0.025/시간, 730시간 기준 약 USD 18.25/월
- RDS `gp3` 20 GiB: USD 0.131/GB-월, 약 USD 2.62/월
- Secret 8개: USD 0.40/Secret-월, 약 USD 3.20/월과 API 호출 비용
- Data Layer 상시 실행 고정비: 약 USD 24.07/월
- 현재 NAT/EIP와 Data Layer를 모두 상시 실행: 약 USD 70.79/월로 USD 50 Budget 초과
- RDS 정지 중에도 Storage와 Secret 약 USD 5.82/월은 남는다. 현재 NAT/EIP를 계속 켜면 합계 약 USD 52.54/월이므로 Budget을 지키려면 사용하지 않을 때 NAT도 OFF해야 한다.
- Runtime OFF에서 ECS Compute를 `0/0/0`으로 내리면 ECS Cluster, Launch Template, ASG, IAM과 Capacity Provider 자체에는 추가 시간당 요금 없음
- ECS Runtime ON 1대: 서울 리전 `m6i.xlarge` USD 0.236/시간과 30 GiB `gp3` 약 USD 2.736/월, 730시간 기준 합계 약 USD 175.02/월 추가
- Capacity Provider가 최대 2대로 확장한 경우: 같은 기준 약 USD 350.03/월 추가
- Runtime ON Valkey `cache.t4g.micro` 1개: USD 0.0192/시간
- Runtime ON Public ALB 1개: USD 0.0225/시간과 사용량 기준 LCU 비용, 두 AZ의 Public IPv4 2개 USD 0.010/시간
- RDS 실행·NAT/EIP·EC2 1대·Valkey·ALB와 ALB IPv4를 합친 Runtime ON 기본 시간당 비용은 약 USD 0.3767이다. 현재도 발생하는 NAT/EIP USD 0.064를 제외한 증분은 약 USD 0.3127이며, EC2 2대까지 확장하면 약 USD 0.6127이다. EBS, RDS/Secret Storage, LCU, NAT 처리, Data Transfer는 별도다.
- ASG가 0대로 돌아가면 Instance와 `DeleteOnTermination` EBS Volume도 삭제되어 ECS Compute 실행비는 멈추지만, 기존 NAT/EIP·RDS Storage·Secrets 비용은 별도로 남음
- Frontend Apply 뒤에는 S3 저장·요청·전송과 CloudFront 요청·전송·Invalidation의 사용량 기반 비용이 추가되며 정확한 금액은 실제 Traffic과 Cache Hit Ratio에 따라 달라짐

가격은 변경될 수 있으므로 Apply 직전 [Amazon ECS 요금](https://aws.amazon.com/ecs/pricing/), [Amazon EC2 On-Demand 요금](https://aws.amazon.com/ec2/pricing/on-demand/), [Amazon EBS 요금](https://aws.amazon.com/ebs/pricing/), [Amazon ECR 요금](https://aws.amazon.com/ecr/pricing/), [Amazon VPC 요금](https://aws.amazon.com/ko/vpc/pricing/), [Amazon RDS for PostgreSQL 요금](https://aws.amazon.com/rds/postgresql/pricing/), [AWS Secrets Manager 요금](https://aws.amazon.com/secrets-manager/pricing/)과 [AWS Budgets 요금](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/)을 다시 확인한다.

## Remote State 운영 주의사항

- `backend.tf`는 S3 Backend 사용 선언이므로 Git에서 관리하고, 계정별 Bucket과 Role을 기록한 `backend.s3.hcl`은 Git에서 제외한다.
- `*.tfstate`, `*.tfstate.*`와 수동 State 백업은 Git에서 제외한다.
- State에는 리소스 식별자와 Budget Email 같은 민감 정보가 포함될 수 있다.
- State를 잃으면 Terraform 관리 관계가 사라지므로 S3 Versioning과 Git 외부의 암호화 백업을 유지한다.
- S3 Versioning, 서버 측 암호화, Public Access 차단, HTTPS 전용 Bucket Policy와 최소 권한을 사용하는 Remote Backend로 이전했다.
- State Lock은 DynamoDB가 아니라 S3 Native Lockfile(`use_lockfile = true`)을 사용한다.
- [`infra/aws/bootstrap/terraform-state`](../bootstrap/terraform-state/README.md)의 검토한 Plan으로 SSE-S3, Versioning, Public Access 차단, HTTPS-only Policy와 전용 State Role을 AWS에 적용하고 재계획 `No changes`를 검증했다.
- `terraform init -migrate-state`로 66개 State 주소를 이전하고 이전 전후 주소·관리 데이터, Native Lockfile 생성·해제와 재계획 `No changes`를 검증했다. 작업 폴더의 평문 Local State는 제거했으며 이전 전 원본은 Windows EFS 암호화 백업으로 보존한다.

## 다음 단계

> 2026-07-23 갱신: 최초 관리자 Bootstrap과 후속 Runtime OFF·Foundation Cleanup을 완료했다. 이어 Backup Restore 격리 Terraform·Validator, 38/38 계약 테스트, 감사 Foundation Apply, Restore ON `11/0/0` 적용, 읽기 전용 검증, 복원 DB 즉시 정지와 Restore Cleanup `0/0/11`을 완료했다. 원본 RDS는 `stopped`이고 임시 Restore 리소스는 0이다.

Post-Restore Full Smoke Runtime ON Saved Plan `tfplan-post-restore-full-smoke-runtime-on`은 230,705 bytes, SHA-256 `1dc1bf8bcc9eccb667659722e3c038f355293f7eb880c33fd14707c8f105f55e`, State serial 95 기준이다. 변경은 Valkey 6·Runtime Alarm 29·ALB/HTTPS/Host Rule/`origin` 5 생성, ECS Service 8·ASG·Container Insights 변경으로 `40 added, 10 changed, 0 destroyed`다. 운영 Gate 만료는 `2026-07-23 17:23:37.608 KST`이며 Redis Password는 Plan에 직렬화되지 않았다.

승인된 Saved Plan은 원본 RDS를 `available`로 올린 뒤 정확히 `40 added, 10 changed, 0 destroyed`로 적용했다. ECS·Container Health 8/8, ASG `1/1/2`, Valkey·RDS `available`, ALB Target 2/2, Cloud Map 8/8과 Runtime Alarm 29/29 `OK`를 확인했다. HTTPS 12/12·Root 308, Member/Admin OAuth·Session·CSRF·보호 REST·Logout, WebSocket 네 Frame·REST History와 SNS `Published 2`·`Delivered 2`·`Failed 0`을 검증했고 동일 ON 입력은 `No changes`, State serial 101이다. RDS Freeable Memory Alarm은 256MiB 임계값에 최신 최소 153.2MiB로 실제 `ALARM`이므로 최종 OFF 뒤 다음 ON 전에 별도 검토한다. 적용한 Saved Plan은 승인 Hash를 다시 확인한 뒤 삭제했다.

1. 완료: Source SHA `a7b3e0387c6817fd5a781ccf3ac532e04f38c9e1`의 Backend 8개 GHCR Build Once → ECR Digest Promote와 8/8 Digest 일치 검증
2. 완료: Runtime Secret 6개 초기화, Application Foundation OFF와 Cloud Map custom health 교정 Apply, ECS/ASG 0·RDS 정지·`No changes` 검증
3. 완료: Runtime ON과 서비스 계약 교정 Apply, RDS/Valkey/ECS/ALB, Health·Digest·Cloud Map 8/8, curl 6/6와 `No changes` 검증
4. 완료: Runtime OFF Saved Plan Apply, ECS/ASG 0·ALB/Valkey 삭제·RDS 정지와 `No changes` 검증
5. 완료: Frontend S3 6개·CloudFront 2개 Apply, GitHub 변수, 첫 전체 배포 6/6과 정적 curl 6/6·`No changes` 검증
6. 완료: Global DNS State 권한·Hosted Zone Import·ACM·Route 53/CloudFront/TLS와 API·OAuth·WebSocket Origin, 정적 curl 6/6와 `No changes`
7. 완료: WebSocket Gateway Route·Member BFF Public Origin 교정 적용, Runtime ON HTTPS·OAuth·Session·WebSocket 네 프레임·채팅 영속성·Logout·`No changes` 검증
8. 완료: 관측성 Foundation·Topic Policy와 Runtime ON Apply, HTTPS·OAuth·Session·WebSocket Smoke, SNS Email 구독 복구·확인 및 실알림 `Published 2`·`Delivered 2`·`Failed 0`, `No changes` 검증
9. 완료: 승인된 관측성 Runtime OFF Saved Plan `0/10/40` 적용, ECS·ASG·ALB·Valkey·Runtime Alarm 종료, RDS 정지, 정적 curl 6/6와 `No changes` 검증
10. 완료: Runtime OFF 알림 전용 Watchdog·Recovery Plan 적용, Baseline·Heartbeat·Alarm→SNS·`No changes` 검증
11. 완료: 최초 관리자 Bootstrap과 관리자 등록 차단 — Runtime ON·Smoke·Runtime OFF·RDS 정지·Foundation Cleanup `0/0/4`·Secret 7일 삭제 예약·감사 Log 보존·`No changes` 검증
12. 완료: Backup Restore 사전 점검·격리 Terraform·38/38 계약 테스트·감사 Foundation Apply·Restore ON `11/0/0` 적용·읽기 전용 검증·복원 DB 정지·Cleanup `0/0/11`·임시 리소스 0·`No changes`
13. 완료: 원본 Full Smoke Runtime ON Plan `40/10/0` 적용, ECS/ALB/Valkey/RDS, HTTPS 12/12·Member/Admin OAuth·Session·WebSocket·REST·SNS Alarm·`No changes` 검증
14. 완료: Post-Restore Full Smoke 최종 Runtime OFF Plan `0/10/40` 적용, ECS/ASG/ALB/Valkey/Runtime Alarm 0, RDS 정지, curl 6/6·OFF API 502·State serial 107 `No changes`
15. 완료: RDS 메모리·Connection 분석, AWS DB 서비스 Hikari Pool `5/1` 교정·38/38 테스트·Commit/Push·Runtime OFF Foundation Plan `3/3/3` 적용, State serial 108·OFF `No changes`
16. 완료: Hikari `5/1` Runtime ON Plan `40/11/0` 적용, 30분 Connection·FreeableMemory·Swap·CPU, Full curl/WebSocket/SNS Alarm, State serial 113·ON `No changes` 검증
17. 완료: Hikari 재측정 Runtime OFF Saved Plan SHA-256 `5e3f9b9a03dceab9eb57491b57b05a8c090693c2c41c10f047ee2c9b86cd779d`을 `0/10/40`으로 적용, ECS·ASG·ALB·Valkey·Runtime Alarm·`origin` 0, RDS 정지, State serial 119·OFF `No changes`, 정적 curl 6/6·Root 308·API 502 검증
18. 완료: DB Class·FreeableMemory Alarm과 Member BFF Prometheus 500 사전 진단 — `db.t4g.micro` 유지, 영속 RDS Alarm 5개와 Member BFF Prometheus 200/404 교정 결정
19. 다음: Alarm·Member BFF 구현·테스트, Member BFF Build Once·ECR Promote와 Runtime OFF Foundation Saved Plan 생성

2026-07-24 진단에서 Hikari `5/1` 재측정값은 DatabaseConnections 평균 3.87·최대 6, FreeableMemory 최소 190.14 MiB, Swap 최대 0.45 MiB, CPU 평균 4.07%였다. Class는 `db.t4g.micro`를 유지하고 FreeableMemory 128 MiB·SwapUsage 64 MiB·DatabaseConnections 16과 기존 CPU·FreeStorage를 합친 영속 Alarm 5개를 후속 구현한다. Member BFF 500은 Prometheus Registry 누락과 `NoResourceFoundException` catch-all 500 변환이 원인이며, Member BFF Image만 교정한다. 현재 Terraform·AWS 적용값은 기존 RDS Alarm 3개·FreeableMemory 256 MiB이고 RDS는 `stopped`다. 상세 범위는 [RDS Alarm·Member BFF Prometheus 교정 계획](../../../docs/plans/2026-07-24-rds-alarm-prometheus-plan.md)을 따른다.

Kubernetes↔AWS DR은 Learning 적용 범위에서 제외하고 후속 학습 과제로 보류한다.
