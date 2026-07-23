# AWS Learning Runtime 결정

> 문서 상태: Learning 목표 설계 승인, Backup Restore·Cleanup과 원본 Full Smoke Runtime ON Apply·curl 검증, 최종 Runtime OFF·RDS 정지 완료
>
> 기준일: 2026-07-23
>
> 저장소 상태: Foundation·ECR/OIDC·Private App 송신·RDS/Secrets·ECS Compute·DB Bootstrap/Flyway·Application Runtime·Frontend Hosting·Public Domain/TLS·RDS Restore Drill 코드 적용, AWS DB 서비스 Hikari Pool `5/1` 교정과 Terraform 계약 테스트 38/38 완료
>
> AWS 적용 상태: 최초 관리자 Bootstrap과 Backup Restore·Cleanup, Post-Restore Full Smoke와 최종 Runtime OFF를 완료했다. 이어 AWS DB 서비스 Hikari Pool `5/1` Foundation Plan을 `3/3/3`, 재측정 Runtime ON Plan을 `40/11/0`으로 적용했다. 현재 ECS·Container Health 8/8, ASG `1/1/2`, ALB Target 2/2, RDS·Valkey `available`, Runtime Alarm 29/29 `OK`, State serial 113이며 동일 ON 입력은 `No changes`다. 30분 지표와 Member/Admin OAuth·Session·WebSocket·REST·SNS Smoke는 통과했다. Pool은 Connection을 안정 구간 3으로 줄였지만 FreeableMemory Alarm은 실측에 따라 `ALARM`이므로 Runtime OFF 뒤 DB Class와 Alarm을 별도 결정한다.

이 문서는 AWS Foundation 이후 Learning 환경에 추가할 Runtime의 승인된 결정을 기록한다. 현재 적용된 리소스와 운영 절차는 [Terraform 운영 Runbook](../../infra/aws/terraform/README.md), 이미 적용된 네트워크 기준선은 [AWS Foundation 설계](04-aws-foundation-design.md)를 따른다.

이 문서에서 `승인`은 설계 선택이 끝났다는 뜻이다. Terraform 코드가 존재하거나 AWS에 Apply됐다는 뜻이 아니다.

## 1. 결정 현황

| 우선순위 | 항목 | 승인한 결정 | 구현 상태 |
|---|---|---|---|
| P0 | Private Subnet 송신 | 단일 Zonal NAT Gateway와 S3 Gateway Endpoint | 적용·검증 완료 |
| P0 | Terraform State | S3 Remote Backend와 S3 Native Lockfile | 적용·이전·검증 완료 |
| P0 | Learning 보안 경계 | 관리자 공개 가입 차단, 4자 비밀번호 위험 수용, 원본 Session ID 미노출 | 코드·Task Definition 적용, AWS Runtime ON 검증 완료 |
| P0 | 이미지 무결성 | GHCR Build Once, OCI Digest 기준 ECR Promote | Backend 8개 Build Once·Promote·8/8 Digest 검증 완료 |
| P1 | ECS 구조 | ECS on EC2, ASG Capacity Provider, Learning ON/OFF | Runtime ON에서 ASG `1/1/2`, Service 8개 `1/1/0` 배치 검증 완료 |
| P1 | 데이터베이스 | PostgreSQL 16 공유 인스턴스·서비스별 Schema·Flyway | DB Secret·Bootstrap·Flyway V1 3개 실제 실행과 사후 검증 완료 |
| P1 | Frontend | 독립 Private S3 6개와 Member/Admin CloudFront 2개 | Apply·AWS 계약·첫 전체 배포·curl 6/6 검증 완료 |
| P1 | Secret | Secrets Manager로 통일 | Container 7개·최소 권한 IAM 적용, DB Secret 3개와 Runtime Secret 6개 계약 초기화 완료 |
| P1 | 도메인 | 기존 Hosted Zone Import와 별도 Global DNS State | Global DNS/ACM·Public Alias/TLS와 Runtime ON Full Smoke 검증 완료 |
| P2 | DR | Learning 범위에서 제외하고 후속 학습 과제로 보류 | 보류 |

## 2. 목표 구조

```mermaid
flowchart TB
    U["사용자"] --> R53["Route 53"]
    R53 --> CF1["Member CloudFront"]
    R53 --> CF2["Admin CloudFront"]
    CF1 --> S31["Private Member S3"]
    CF1 --> S32["Private Community S3"]
    CF1 --> S33["Private Stock S3"]
    CF2 --> S34["Private Admin S3"]
    CF2 --> S35["Private Admin Users S3"]
    CF2 --> S36["Private Admin Logs S3"]
    CF1 --> ALB["Public ALB"]
    CF2 --> ALB

    ALB --> ECS["Private App ECS on EC2"]
    ECS --> RDS[("Private Data RDS PostgreSQL 16")]
    ECS --> REDIS[("Private Data Redis")]
    ECS --> NAT["Single NAT Gateway in Public A"]
    ECS --> S3EP["S3 Gateway Endpoint"]

    SM["Secrets Manager"] --> ECS
    GHCR["GHCR source SHA + OCI digest"] --> PROMOTE["Digest-verified Promote"]
    PROMOTE --> ECR["ECR same digest"]
    ECR --> ECS
```

Learning 환경만 실제 AWS에 적용한다. 다중 NAT, Interface Endpoint, Multi-Region과 Kubernetes↔AWS DR은 운영 환경 비교 자료로만 다루며 이번 Apply 범위에 넣지 않는다.

## 3. Terraform State

Local State에서 다음 구조로 이전했다.

- S3 Remote Backend
- S3 Versioning 활성화
- 서버 측 암호화 활성화
- Block Public Access 전체 활성화
- HTTPS 요청만 허용하는 Bucket Policy
- Backend 전용 최소 권한 IAM 적용
- `use_lockfile = true`인 S3 Native Lockfile 사용
- DynamoDB Lock Table은 만들지 않음

Backend Bucket과 권한은 해당 Backend가 자기 자신을 관리할 수 없으므로 별도 Bootstrap 단계에서 먼저 생성한다. 기존 Local State는 `terraform init -migrate-state`로 이전하고, 이전 전후 `terraform state list`와 Plan의 무변경 여부를 확인한다.

Learning 구현값은 다음으로 확정한다.

- Bootstrap 위치: `infra/aws/bootstrap/terraform-state`
- Bucket 이름: `spring-react-msa-learning-tfstate-{account-id}-ap-northeast-2`
- 암호화: 별도 KMS Key 없이 SSE-S3 `AES256`
- Main State Key: `learning/runtime/terraform.tfstate`
- Lock Key: `learning/runtime/terraform.tfstate.tflock`
- Global DNS State Key: `global/dns/terraform.tfstate`
- Global DNS Lock Key: `global/dns/terraform.tfstate.tflock`
- 접근: `hyun-terraform-admin`만 Assume할 수 있는 전용 최소 권한 State Role
- Bootstrap 자체 State: Git에서 제외한 Local State와 암호화된 개인 백업

Bootstrap Terraform의 저장 Plan을 적용해 S3 Bucket과 전용 IAM 권한을 생성한 뒤 Main State 66개 주소를 S3로 이전했다. 이전 전후 리소스 주소와 State 관리 데이터를 비교했으며, Versioning, SSE-S3 `AES256`, Public Access 차단, HTTPS-only Policy, Native Lockfile 생성·해제와 Terraform 재계획 `No changes`를 검증했다. 새 Backend는 새 Lineage와 Serial 1로 시작하며, 이전 전 Local State는 Git 외부의 Windows EFS 암호화 백업으로 보존한다.

State Key는 최소한 다음 수명주기로 분리한다.

```text
global/dns/terraform.tfstate
learning/runtime/terraform.tfstate
```

State에는 리소스 식별자와 민감한 속성이 기록될 수 있다. 실제 비밀번호와 Token은 `.tf`, `.tfvars`, Terraform Output 또는 Secret Version 리소스에 넣지 않는다.

## 4. Private Subnet 송신

- Public Subnet A에 고정 Elastic IP를 사용하는 NAT Gateway 1개를 배치한다.
- 두 Private App Subnet의 기본 경로 `0.0.0.0/0`는 같은 NAT Gateway를 사용한다.
- ECR API/Registry, ECS, CloudWatch Logs, Secrets Manager와 외부 Toss API 통신은 NAT Gateway를 사용한다.
- Private App Route Table에 S3 Gateway Endpoint를 연결한다.
- ECR Image Layer를 포함한 S3 Traffic은 S3 Gateway Endpoint를 사용한다.
- Private Data Route Table에는 Internet 기본 경로를 만들지 않는다.
- Interface Endpoint와 AZ별 NAT Gateway는 Learning 환경에 적용하지 않는다.
- ECS Security Group은 AWS 공개 API와 외부 서비스에 필요한 TCP 443 송신만 `0.0.0.0/0`으로 허용한다.

단일 NAT가 있는 가용 영역에 장애가 발생하면 두 Private App Subnet의 외부 송신이 모두 영향을 받을 수 있다. Learning 환경에서는 월 고정비와 구성 복잡도를 줄이기 위해 이 위험을 명시적으로 수용한다. NAT Gateway를 OFF 하더라도 고정 EIP는 다음 ON에서 재사용할 수 있도록 유지한다.

현재 구현은 `enable_nat_gateway`로 NAT Gateway와 Private App 기본 경로만 ON/OFF하고, 고정 EIP와 추가 요금이 없는 S3 Gateway Endpoint는 유지한다. 서울 리전 기준 NAT Gateway는 시간당 USD 0.059와 처리량 GB당 USD 0.059, Public IPv4는 시간당 USD 0.005다. 730시간 계속 ON이면 Data 처리비와 전송비를 제외한 고정비만 약 USD 46.72이며, NAT를 OFF해도 EIP 유지 비용 약 USD 3.65/월은 남는다. 현재 USD 50 Budget에서는 상시 ON보다 필요한 학습 시간에만 켜는 방식을 사용한다. 가격과 Gateway Endpoint 과금 기준은 [Amazon VPC 가격](https://aws.amazon.com/vpc/pricing/)과 [S3 Gateway Endpoint 문서](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html)를 따른다.

검토한 저장 Plan으로 EIP, NAT Gateway, Private App 기본 경로, S3 Gateway Endpoint와 ECS HTTPS 송신 규칙의 5개를 적용했다. NAT `available`, App 기본 경로 `active`, S3 Endpoint `available`, Private Data 외부 경로 0개와 Terraform 재계획 `No changes`를 검증했다.

## 5. ECS on EC2와 ON/OFF

### 초기 Capacity

- ECS 최적화 Amazon Linux 2023 AMI와 두 App AZ 모두에서 제공되는 `m6i.xlarge` On-Demand Instance를 사용한다.
- Auto Scaling Group은 Capacity Provider와 연결한다.
- Runtime ON: ASG `min=1`, `desired=1`, `max=2`
- Runtime OFF: ASG `min=0`, `desired=0`, `max=0`
- Capacity Provider Managed Scaling은 Rolling Deployment 중 Pending Task를 수용할 수 있도록 두 번째 Instance까지 확장한다.
- Learning에서는 ECS Service Application Auto Scaling을 사용하지 않는다.

Backend 8개는 각각 독립된 Task Definition과 ECS Service를 가진다. Runtime ON에서는 Service별 `desired_count=1`, OFF에서는 `0`을 사용한다. 서비스별 CPU/Memory 시작값은 [ECS Resource Baseline](01-resource-baseline.md)을 따른다.

현재 Terraform은 `enable_ecs_compute_foundation`, `enable_application_runtime_foundation`, `learning_runtime_enabled`를 분리한다. Compute와 Application Foundation을 적용해 Digest 고정 Task Definition·Service·Cloud Map 8개, Target Group 2개와 서비스별 IAM/Log Group을 유지한다. `learning_runtime_enabled=false`에서는 8개 Service가 `desired_count=0`이고 유료 Public ALB와 Valkey가 없으며, `true`에서만 ASG `1/1/2`, Service별 Task 1개, Public ALB와 단일 Valkey를 만든다. Runtime OFF에서 ECS Service 8개 `0/0/0`, ASG `0/0/0`과 `No changes`를 검증했고, 후속 Runtime ON에서 Service·Container Health·Digest·Cloud Map 8/8, curl 6/6, ASG 자동 확장 2대 후 1대 축소와 재계획 `No changes`까지 확인했다. 최종 Runtime OFF 적용 뒤 실행 Workload 0, ALB·Valkey 삭제와 RDS 정지 상태로 다시 수렴했다.

Container Instance는 두 Private App Subnet을 사용하고 Public IP와 SSH Ingress를 갖지 않는다. ECS 최적화 Amazon Linux 2023 AMI, `m6i.xlarge`, 암호화한 30 GiB `gp3`, IMDSv2 필수와 SSM Session Manager 접근을 사용한다. Container Insights는 Learning 비용을 줄이기 위해 Compute 단계에서 비활성화하고, Task Log와 최소 Alarm은 Service/관측성 단계에서 별도 확정한다.

ECS Capacity Provider가 ASG에 자동으로 추가하는 필수 `AmazonECSManaged` Tag도 Terraform에서 명시적으로 관리한다. 이를 누락하면 Apply 후 재계획에서 Terraform이 AWS 서비스 Tag를 제거하려는 드리프트가 발생하므로 계약 테스트로 고정한다.

정상 상태 Instance가 1개이므로 두 AZ에 Subnet을 만들었더라도 ECS Compute는 Multi-AZ 고가용성이 아니다. Instance 또는 해당 AZ 장애 시 ASG가 대체 Instance를 시작하는 동안 8개 Backend가 함께 중단될 수 있으며 Learning 환경에서는 이 위험을 수용한다.

Application Task는 `awsvpc`를 사용하고 Cloud Map Private DNS Namespace `learning.spring-react-msa.internal`에 서비스별 A Record를 등록한다. 기존 코드가 사용하는 `http://서비스:고정포트` 형태를 그대로 유지하고 Service Connect Sidecar는 사용하지 않는다. Cloud Map Service 8개에는 ECS 관리형 custom health와 `failure_threshold=1`을 명시해 실제 AWS와 Terraform 상태가 수렴하도록 했다. 한 `m6i.xlarge`에 Task ENI 8개를 배치할 수 있도록 Account `awsvpcTrunking=enabled`도 ASG가 0대인 상태에서 적용했으며 다음 Runtime ON Instance부터 사용한다.

Redis 호환 Runtime은 ElastiCache Redis OSS가 아니라 Valkey `7.2`, `cache.t4g.micro`, 단일 Node, Replica·Multi-AZ·Snapshot 없음으로 확정한다. Private Data Subnet과 Data Security Group만 사용하고 저장·전송 암호화와 RBAC를 강제한다. 기본 사용자는 비활성화하고 Application Password는 Terraform Ephemeral Variable과 Provider의 `passwords_wo`에만 전달해 Plan과 State에 저장하지 않는다. Endpoint는 Runtime ON 동안에만 SSM Parameter Store `String`으로 게시하고 Password는 기존 Secrets Manager JSON Key를 사용한다.

Valkey는 정지 기능이 없으므로 Runtime OFF에서 삭제하고 다음 ON에 재생성한다. 세션과 Cache는 Learning 환경에서 폐기 가능한 데이터로 취급하며 RDS처럼 보존하지 않는다. 서울 리전 현재 On-Demand 가격은 `cache.t4g.micro` Valkey Node당 USD 0.0192/시간, 730시간 기준 약 USD 14.02이며 실제 사용 시간만 과금된다.

`learning_runtime_enabled`는 ECS ASG/Service, ALB와 Valkey 같은 실행 비용 리소스의 목표 상태를 제어한다. NAT는 `enable_nat_gateway`로 별도 관리하며 RDS 정지는 Terraform 리소스 삭제와 다르므로 별도 운영 명령 또는 자동화로 수행한다.

RDS Instance는 최대 7일 연속으로만 정지할 수 있고 이후 AWS가 자동으로 다시 시작한다. OFF 운영 자동화는 `AutomaticRestartTime`을 감시해 필요하면 다시 정지하고 Budget Alarm으로 예기치 않은 실행 비용을 확인해야 한다. 정지 중에도 Storage와 Backup Storage 비용은 남는다. 자세한 제한은 [AWS RDS 임시 정지 문서](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html)를 따른다.

### OFF 상태에 유지하는 리소스

- VPC, Subnet, Route Table, Security Group
- NAT용 Elastic IP
- ECR Repository와 Image
- S3 Backend와 Lockfile
- RDS Instance와 Backup; Instance 실행 상태만 정지
- Secrets Manager Secret
- Application Task Definition, ECS Service `desired_count=0`, Cloud Map Namespace/Service, Target Group과 7일 Log Group
- Route 53 Hosted Zone과 ACM DNS 검증 Record
- Frontend S3, CloudFront, `app`과 `admin` Record

OFF 상태에서는 ALB Origin과 Backend API가 제공되지 않는다. Frontend가 유지되더라도 API 기능이 비활성임을 화면에서 분명히 표시해야 한다.

## 6. 데이터베이스와 Migration

- PostgreSQL 16 RDS Instance 1개를 사용한다.
- Database 이름은 `spring_msa`다.
- 비용은 Instance를 공유해 줄이고, 소유권은 Schema와 DB 사용자로 분리한다.
- 구현값은 PostgreSQL `16.14`, `db.t4g.micro`, Single-AZ, 암호화된 고정 20 GiB `gp3`다.
- Performance Insights와 Enhanced Monitoring은 Learning 비용 절감을 위해 끄고, Automated Backup은 7일 유지한다.
- Public 접근은 차단하고 Private Data Subnet 2개와 기존 Data Security Group만 사용한다.

| Service | Schema | DB 사용자 원칙 |
|---|---|---|
| User Service | `user_service` | 자기 Schema만 접근 |
| Member BFF | `member_bff` | 자기 Schema만 접근 |
| Stock Service | `stock_service` | 자기 Schema만 접근 |
| Community Service | 현재 생성하지 않음 | 영속화 구현 후 별도 결정 |

다른 서비스의 테이블을 직접 조회하지 않는다. RDS Master 계정은 Bootstrap과 관리 작업에만 사용하며 Application Task에는 제공하지 않는다.

Schema 변경은 Liquibase가 아니라 서비스별 Flyway SQL Migration으로 관리한다. 각 서비스는 독립된 Migration 경로와 `flyway_schema_history`를 가지며 AWS에서는 다음 설정을 사용한다.

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.sql.init.mode=never
```

테스트 계정과 Seed Data는 AWS Learning Migration에 포함하지 않는다. Flyway는 Private App Subnet에서 실행하는 일회성 ECS Migration Task로 수행하고 성공 후 Application Service를 배포한다.

세 서비스의 Flyway V1 SQL, PostgreSQL 16 Testcontainers 검증과 전용 `FlywayMigrationMain`을 구현했다. 서비스별 DB 사용자·Schema·Grant를 만드는 Bootstrap Task Definition과 최소 권한 IAM/Log Group을 AWS에 적용하고, Secret 3개 초기화와 실제 RDS Bootstrap을 완료했다. RDS 호환 Revision 2와 읽기 전용 검증 Task로 안전한 Role 3개, Schema 3개, 자기 Schema 권한 조합 3개, 교차 Schema 권한 0개를 확인했다. 이후 Build Once·Digest Promote한 ECR Image를 사용하는 Migration Task Definition 3개를 적용하고 실제 Flyway V1을 순차 실행했다. 사후 검증에서 History 3개, V1 성공 3개, Application Table과 올바른 소유자 각 5개, 실패 Migration·교차 권한·Seed Data 0개를 확인했다.

검토한 저장 Plan으로 RDS, DB Subnet Group, Parameter Group과 빈 Secret Container 7개를 AWS에 적용했다. RDS 보안·Backup 설정과 Managed Master Secret을 검증한 뒤 비용 통제를 위해 RDS를 정지했다. 2026-07-23 Automated Backup `RestoreWindow`로 약 115.2시간의 복원 가능 구간, 최신 시점 지연 최초 약 44.1분·Foundation Saved Plan 직전 약 102분, Backup 1개·Snapshot 4개와 복원 대상 가용성을 확인했다. 격리 PITR·읽기 전용 Fargate Validator Terraform과 전체 계약 테스트 38개를 구현했고 감사 Foundation을 `1 added, 0 changed, 0 destroyed`로 적용했다. 후속 Restore ON Plan은 `11 added, 0 changed, 0 destroyed`로 적용했고 `2026-07-23 11:02:47 KST` 시점의 별도 Private RDS를 복원했다. Fargate Validator가 Schema·Role·Flyway·Table·활성 관리자 계약을 Exit Code `0`으로 검증했으며, 검증 직후 복원 DB를 정지했다.

RDS는 Runtime OFF 때 삭제하지 않고 정지한다. Automated Backup 보존 기간은 7일, PITR은 활성화하고 의도적인 최종 삭제 전 Final Snapshot을 생성한다. 이번 관측 RTO 약 28분 35초와 RPO 지연 약 2시간 54분 14초는 단일 Learning 훈련값이며 운영 보장값으로 표시하지 않는다.

## 7. Frontend

- 배포 단위는 `member`, `community`, `stock`, `admin`, `admin-users`, `admin-logs`이며 각각 Private S3 Bucket 한 개를 사용한다.
- 공개 진입점은 Member와 Admin CloudFront Distribution 두 개만 사용한다. Member는 Member·Community·Stock Origin을, Admin은 Admin·Users·Logs Origin을 가진다.
- S3 Website Endpoint와 Public Bucket은 사용하지 않고 Origin Access Control로 CloudFront만 S3에 접근시킨다.
- `app.hyuncloudlab.com`은 Member Distribution, `admin.hyuncloudlab.com`은 Admin Distribution을 가리킨다.
- `/community/*`, `/stock/*`, `/manage/users/*`, `/manage/logs/*`는 경로별 전용 S3 Origin으로 전달한다. Prefix 자체는 Slash 경로로 Redirect하고 CloudFront Function이 해당 SPA Entry와 Asset 경로로 Rewrite한다.
- 수동 Workflow는 선택한 Frontend의 Workspace Script만 Build하고 전용 Bucket만 동기화한 뒤 해당 경로만 Invalidate한다. 예를 들어 Stock 배포는 Community와 Member 객체를 변경하지 않는다.
- API, OAuth2, Logout과 WebSocket 경로는 Cache를 끄고 전체 Viewer Header·Cookie·Query를 전달하며 SPA Rewrite 없이 HTTPS ALB Origin으로 보낸다. 이 계약과 ACM·Route 53·CloudFront 단계별 Apply를 완료했으며 Runtime OFF API 502까지 경로 연결을 확인했다. 실제 Backend 계약은 Runtime ON Full Smoke에서 검증한다.
- 기존 Frontend Nginx Image는 Docker/Kubernetes 경로에만 남기고 AWS용 ECR/ECS에는 배치하지 않는다.

CloudFront용 ACM 인증서는 `us-east-1`, ALB용 인증서는 `ap-northeast-2`에서 관리한다. Learning Runtime의 ALB Origin은 Public Subnet에 두되 80은 열지 않고, AWS 관리형 `com.amazonaws.global.cloudfront.origin-facing` Prefix List에서 오는 443만 Security Group에 허용한다. 별도 Secret Header는 Terraform State에 비밀을 남기므로 이번 단계에서는 사용하지 않는다. 따라서 직접 Internet 접근은 차단되지만 다른 CloudFront Distribution을 암호학적으로 구분하지 못하는 잔여 위험은 Learning 범위에서 명시적으로 수용한다.

현재 Terraform module은 BucketOwnerEnforced, Public Access 전체 차단, SSE-S3, Versioning, 7일 Noncurrent Version 정리, OAC `always` SigV4를 고정한다. `.github/workflows/aws-frontend-deploy.yml`은 Node `24.18.0`, pnpm `10.0.0`, Frozen Lockfile과 GitHub OIDC를 사용한다. 여섯 Frontend Lint·Build, 선택 Matrix 계약 14개와 Terraform `validate`·`test` 20/20을 통과했다. Saved Plan SHA-256 `f49031685f65ff8ed8274316e34e1c195431a3d1912ac279114b14b23f0aa5e8`을 승인된 그대로 Apply해 `49 added, 0 changed, 0 destroyed`로 완료했다. AWS에서 S3 보안 6/6, CloudFront `Deployed` 2/2·Origin 3+3·Function 연결 6개, Function `DEPLOYED` 2/2, OAC와 IAM Trust를 확인했고 재계획은 `No changes`였다. Source SHA `f29249373feae470e2c30758e3245d43d22fef25`의 [Run 29677216377](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29677216377)에서 6개 배포 Job과 필수 단계 24/24가 성공했고, S3 Cache metadata 6/6과 CloudFront 정적 curl 6/6 HTTP 200을 확인했다. 상세 절차는 [AWS Frontend Runbook](../runbooks/aws-frontend-hosting.md)을 따른다.

Public Domain Flag를 활성화해 Root와 `app`은 Member Distribution, `admin`은 Admin Distribution에 연결했고 Root 요청은 `app`으로 308 Redirect한다. API Behavior는 AWS 관리형 `CachingDisabled`와 `AllViewer` Policy를 사용하며 SPA Function을 연결하지 않는다. State 권한, Global DNS/ACM과 Runtime OFF Public Domain Routing 세 Saved Plan을 승인된 Hash 그대로 적용했고, A/AAAA 3+3·정적 curl 6/6·Root 308·TLS·재계획 `No changes`를 검증했다. 다음 Runtime ON Plan은 HTTPS ALB와 `origin` A Alias를 일시 생성해 OAuth·Session·WebSocket까지 검증하는 별도 승인 Gate다.

## 8. Secret 관리

Application Secret은 Secrets Manager로 통일하고 SSM SecureString은 사용하지 않는다. 비밀이 아닌 URL, Host, Port, Client ID, 기능 Flag는 ECS 일반 환경 변수 또는 SSM Parameter Store `String`을 사용한다.

초기 Secret 경계는 다음과 같다.

| Secret | 내용 |
|---|---|
| RDS Managed Master Secret | RDS가 생성·관리하며 AWS가 이름을 부여하는 Master 자격 증명 |
| `/spring-react-msa/learning/user-service` | User DB 비밀번호 |
| `/spring-react-msa/learning/member-bff` | Member BFF DB 비밀번호와 Client Secret |
| `/spring-react-msa/learning/stock-service` | Stock DB 비밀번호와 Toss Client Secret |
| `/spring-react-msa/learning/admin-bff` | Admin BFF Client Secret |
| `/spring-react-msa/learning/auth-server` | Member/Admin Client Secret Hash |
| `/spring-react-msa/learning/shared/redis` | Redis 비밀번호 |
| `/spring-react-msa/learning/shared/internal-api` | 내부 API Token |

Terraform은 Secret Container, IAM Policy와 ECS ARN 참조만 관리한다. 실제 Secret Value는 Terraform State에 넣지 않는다. 서비스별 Task Execution Role에는 필요한 ARN의 `secretsmanager:GetSecretValue`만 허용한다. Secret을 변경한 뒤에는 ECS Service를 새로 배포해야 실행 중인 Task가 새 값을 받는다.

Learning에서는 Secrets Manager 기본 AWS 관리형 KMS Key를 사용한다. RDS Master Secret은 Application Task에 제공하지 않으며 서비스별 DB 비밀번호 생성과 DB Role 반영을 담당하는 Bootstrap 절차를 구현·검증했다.

## 9. Domain과 DNS

- Route 53에 이미 존재하는 `hyuncloudlab.com` Public Hosted Zone을 새로 만들지 않는다.
- 기존 Hosted Zone은 `global/dns` State로 Import하고 `prevent_destroy`를 적용한다.
- 도메인 등록, 결제, 연락처와 자동 갱신은 Terraform 관리 대상에서 제외한다.
- Learning Runtime은 Data Source로 기존 Public Hosted Zone을 조회한다.
- AWS가 자동 생성한 NS와 SOA Record는 별도 생성하거나 삭제하지 않는다.
- 기존 사용자 생성 Record가 있으면 첫 Apply 전에 목록을 확인하고 필요한 Record만 개별 Import한다.

Terraform이 관리할 Application Record는 다음과 같다.

| Record | Target | 수명주기 |
|---|---|---|
| `app.hyuncloudlab.com` | Member CloudFront | Frontend와 함께 유지 |
| `admin.hyuncloudlab.com` | Admin CloudFront | Frontend와 함께 유지 |
| `hyuncloudlab.com` | Member CloudFront의 `app` Redirect | Frontend와 함께 유지 |
| `origin.hyuncloudlab.com` | Public ALB | Runtime ON일 때만 생성 |
| ACM 검증 CNAME | ACM 인증서 | 인증서와 함께 유지 |

2026-07-19에 State Role의 Global DNS Key 권한을 적용한 뒤 기존 Public Hosted Zone을 별도 State로 Import했다. CloudFront용 `us-east-1` 인증서와 Origin용 `ap-northeast-2` 인증서가 모두 `ISSUED`이며 DNS 검증 `3+1`을 확인했다. 이어 Runtime OFF Gate에서 Root·Member·Admin A/AAAA `3+3`, CloudFront Custom Domain/TLS/API Origin과 Prefix List HTTPS 제한을 적용했다. 정적 curl 6/6 HTTP 200, Root 308·Path/Query 보존, Runtime OFF API 502와 재계획 `No changes`를 검증했다.

## 10. Learning 보안 경계

- AWS에서 관리자 공개 가입 Endpoint는 활성화하지 않는다.
- 최초 관리자 생성은 일회성 Bootstrap 절차로만 수행하고 이후 공개 Route를 닫는다.
- 현재 4~100자 비밀번호 규칙은 Learning 편의를 위해 유지한다.
- 4자 비밀번호는 운영 권장 보안 기준이 아니라 이 프로젝트가 명시적으로 수용한 Learning 위험이다.
- Admin Session 조회 응답에는 원본 Session ID를 반환하지 않고 SHA-256 Fingerprint 또는 Masked ID만 반환한다.
- 원본 Session ID가 필요한 강제 Logout은 별도 인증된 Endpoint 내부에서만 처리하고 감사 로그를 남긴다.

이 결정의 공개 가입 차단과 Session ID 제거는 코드와 AWS에 적용했다. Admin Registration Controller는 `prod` 기본값과 AWS Task 환경 변수에서 비활성이며, 로컬 Kubernetes만 명시적으로 활성화한다. Admin Session API와 Frontend는 원본 ID 대신 SHA-256 Fingerprint를 사용한다. AWS Frontend 가입 UI 비노출, User Service의 멱등 일회성 Bootstrap, 임시 Secret·최소 권한 Task·감사 Log 계약도 구현했다. 4~100자 공개 요청 규칙은 Learning 환경에 남지만 AWS 공개 Route는 등록하지 않으며 Bootstrap 비밀번호는 별도 20~72 UTF-8 byte 기준을 사용한다. Source SHA `8e5aaa06540541e365e5cfaf7cc559c8b777ae63`의 User Service·Admin BFF Build Once·ECR Promote와 Admin 선택 배포, Private ECS Task `created`·`already_present`, 공개 가입 404와 관리자 OAuth·Session·Logout, Runtime OFF와 임시 Foundation Cleanup까지 완료했다.

## 11. Image Build Once와 Promote

승인한 목표는 Backend 8개 Image를 서비스와 Source SHA당 한 번만 만드는 것이다.

1. GHCR Workflow가 Backend Image를 한 번 Build한다.
2. Git SHA Tag와 최상위 OCI Manifest Digest를 기록한다.
3. Kubernetes는 GHCR Digest를 배포 기준으로 사용한다.
4. ECR Workflow는 `source_sha`를 명시적으로 입력받는다.
5. ECR Workflow는 재빌드하지 않고 GHCR Image를 Digest 기준으로 ECR에 복사한다.
6. Promote 후 GHCR과 ECR의 최상위 Digest가 같을 때만 성공한다.
7. ECR SHA Tag가 이미 있으면 Digest가 같을 때만 Skip하고 다르면 실패한다.
8. `latest`는 배포 기준으로 사용하지 않는다.

현재 `.github/workflows/ghcr-build-push.yml`은 서비스·Source SHA당 최초 한 번만 Build하고 최상위 OCI Digest를 검증한다. `.github/workflows/ecr-build-push.yml`은 Docker Build 없이 `crane copy`로 GHCR Digest를 ECR에 Promote하고 두 Registry의 Digest가 같을 때만 성공한다. Source SHA `a7b3e0387c6817fd5a781ccf3ac532e04f38c9e1`의 Backend 8개를 GHCR Run `29648349144`에서 Build Once하고 ECR Run `29648492164`에서 Promote해 8/8 Digest 일치를 실제 검증했다.

WebSocket Gateway 교정 Source SHA `5fc26bdc355d0417d29bbc1941a0d9c0996e4200`은 [GHCR Run 29685219294](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29685219294)에서 `spring-member-gateway` 하나만 테스트·Build했고, Kubernetes Bot Commit `522a0013f2daea87748c7ce16057128e4528b8fa`가 새 GHCR Digest를 매니페스트에 고정했다. [ECR Run 29685323647](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29685323647)은 같은 Source SHA를 재빌드 없이 승격했으며 GHCR·ECR·Kubernetes OCI Digest가 모두 `sha256:7396a5ce119ced79f994c9d5e2908e4e98e3dc89c74afaf8f4f2a729006f44fa`로 일치했다.

## 12. DR 범위

Kubernetes↔AWS 복제, RTO/RPO 보장, DNS Failover, Writer Fencing과 Failback은 Learning 구현 범위에서 제외한다. 기존 DR 문서는 운영 환경 참고 설계로만 유지하며 현재 실행 가능한 기능이나 승인된 운영 Runbook으로 표시하지 않는다.

Learning에서 적용할 복구 기준은 다음으로 제한한다.

- RDS Automated Backup 7일과 PITR
- 의도적인 RDS 삭제 전 Final Snapshot
- Terraform Backend Versioning
- ECR의 Git SHA Tag와 Digest 보존
- 백업이 아니라 실제 Restore 훈련으로 복구 가능성 확인

## 13. 구현 순서와 승인 Gate

1. 완료: 기존 Local State 백업과 S3 Backend Migration·Native Lockfile 검증
2. 완료: 단일 NAT Gateway, 고정 EIP와 S3 Gateway Endpoint
3. 완료: Secrets Manager Container 7개와 DB Bootstrap 최소 권한 IAM 적용, DB Secret 3개 초기화
4. 완료: RDS·Secret·Bootstrap, GHCR Build Once·ECR Promote, Flyway V1 3개 실제 실행과 사후 검증
5. 완료: ECS Cluster, Launch Template, ASG와 Capacity Provider 적용·AWS 검증·재계획 `No changes`
6. 완료: Runtime Secret 초기화, Application Foundation, Runtime ON·서비스 계약 교정과 curl Smoke 6/6, 재계획 `No changes` 검증
7. 완료: Runtime OFF Saved Plan 적용, ECS/ASG 0·Valkey/ALB 삭제·RDS 정지와 재계획 `No changes` 검증
8. 완료: Frontend S3 6개·CloudFront 2개 Apply, GitHub 변수, 첫 전체 배포 6/6과 정적 curl 6/6·`No changes` 검증
9. 완료: State Role·기존 Hosted Zone Import·ACM, Root·Member·Admin A/AAAA와 CloudFront HTTPS/API Origin, 정적 curl 6/6·Root 308·`No changes`
10. 완료: WebSocket Gateway Route와 Member BFF Public Origin 교정 적용, Runtime ON HTTPS·OAuth·Session·WebSocket 네 프레임·채팅 영속성·Logout·`No changes` 검증
11. 완료: CloudWatch Logs·Metrics·Alarms, SNS 실알림, Learning ON/OFF와 알림 전용 Watchdog
12. 완료: 최초 관리자 Bootstrap·공개 가입 차단·Runtime OFF·RDS 정지·Foundation Cleanup·Secret 7일 삭제 예약·감사 Log 보존
13. 완료: Backup Restore 사전 점검·격리 Terraform·38/38 계약 테스트·감사 Foundation Apply·Restore ON `11/0/0` 적용·읽기 전용 검증·복원 DB 정지·Cleanup `0/0/11`·임시 리소스 0·`No changes`
14. 완료: 원본 Full Smoke Runtime ON Plan `40/10/0` 적용, HTTPS/OAuth/Session/WebSocket/REST/SNS·`No changes` 검증
15. 완료: 최종 Runtime OFF Plan `0/10/40` 적용, ECS·ASG·ALB·Valkey·Runtime Alarm 0, 원본 RDS 정지, 정적 curl 6/6·OFF API 502·State serial 107 `No changes`
16. 완료: RDS 메모리 실측 분석, AWS DB 서비스 Hikari Pool `5/1` 교정·38/38 테스트·Commit/Push·Runtime OFF Foundation Plan `3/3/3` 적용, State serial 108·OFF `No changes`
17. 완료: Hikari `5/1` Runtime ON Plan `40/11/0` 적용, 30분 RDS 지표·Full curl/WebSocket/SNS Alarm·ON `No changes` 검증, State serial 113
18. 다음: Hikari 재측정 Runtime OFF Saved Plan 생성·적용과 RDS 정지, 이후 DB Class·Alarm·Member BFF Prometheus 별도 결정

각 단계는 `fmt`, `validate`, `test`, 저장 Plan 검토, 비용 확인과 명시적 Apply 승인을 거친다. 뒤 단계 리소스를 앞 단계 Plan에 섞지 않는다.

## 14. Application Foundation 이후 남은 작업

상위 아키텍처와 Application Runtime 세부 구현값은 다음과 같이 확정했다.

- RDS 구현값은 확정 완료: PostgreSQL 16.14, `db.t4g.micro`, Single-AZ, 20 GiB `gp3`, `postgres16` Parameter Group, 삭제 보호, UTC 일요일 18:00 유지보수 시간
- Redis: Valkey 7.2, `cache.t4g.micro`, Single Node, Runtime OFF 삭제
- 내부 통신: `awsvpcTrunking` + `awsvpc` + Cloud Map A Record, Service Connect 미사용
- IAM: Task Role 없음, 서비스별 Execution Role이 자기 ECR/Log와 필요한 Secret/Redis Host Parameter만 읽음
- ALB: Runtime ON에만 생성, `app`/`admin` Host Rule과 Gateway Readiness Health Check 사용. HTTPS/ACM·CloudFront Origin과 Runtime OFF Gate C, Runtime ON Full Smoke 검증 완료

Application Foundation 적용 전에 필요했던 다음 항목은 완료했다.

- Runtime Secret 6개를 `Initialize-LearningRuntimeSecrets.ps1`로 초기화하고 `AWSCURRENT`와 JSON Key 계약만 검증
- Source SHA 기준 ECR Digest 8개를 Git과 문서에 노출하지 않고 Plan 입력으로 고정
- Runtime OFF Application Foundation 저장 Plan을 검토·Apply하고 ECS/ASG 0, RDS 정지와 재계획 `No changes` 검증

Application Foundation 최초 Apply는 56개 리소스를 추가했다. 빈 Cloud Map custom health block을 AWS가 상태에 남기지 않는 수렴 문제를 발견해 `failure_threshold=1`과 회귀 테스트를 추가했고, Cloud Map Service 8개를 교체한 뒤 실제 AWS custom health 8/8과 Remote State 165개 주소를 확인했다.

Runtime ON 검증 이후 남은 작업은 다음과 같다.

- 완료: Restore Drill Cleanup `0/0/11`, 임시 리소스 0·감사 Log 보존·`No changes`
- 완료: 원본 Full Smoke Runtime ON Saved Plan `40/10/0`·SHA-256·비용·Static curl 6/6·SNS/Alarm 기반선 검증
- 완료: 원본 RDS 시작·Runtime ON Apply·전체 curl Smoke와 실제 RDS Memory Alarm 확인
- 완료: 최종 Runtime OFF Saved Plan `0/10/40` 적용, 원본 RDS 정지와 동일 OFF 입력 `No changes`
- 완료: RDS 메모리 분석과 Hikari Pool `5/1` Runtime OFF Foundation 적용, State serial 108·동일 OFF 입력 `No changes`
- 완료: Hikari `5/1` Runtime ON 최소 30분 Connection·FreeableMemory·Swap·Hikari Timeout 재측정과 전체 Smoke
- 다음: 별도 승인으로 Runtime OFF Saved Plan을 생성·적용하고 RDS 정지, DB Class·Alarm·Member BFF Prometheus는 별도 결정

CloudWatch Log 보존 기간은 7일로 코드와 계약 테스트에 고정했고 Frontend 독립 배포, HTTPS/DNS, Public Domain Runtime ON Full Smoke, Alarm과 Watchdog을 AWS에 적용·검증했다. 관리자 Bootstrap과 Backup Restore·Cleanup, 원본 Full Smoke·최종 Runtime OFF도 완료했다. Hikari 재측정은 Connection 평균 3.87·최대 6·안정 구간 3, FreeableMemory 평균 197.09MiB·최소 190.14MiB, Swap 최대 0.45MiB였고 전체 curl/WebSocket/SNS와 `No changes`를 통과했다. Pool 효과는 확인됐지만 256MiB Alarm은 계속 `ALARM`이므로 비용 종료 후 DB Class와 Alarm을 함께 재검토한다.
