# AWS Learning Application Runtime Runbook

이 Runbook은 Backend 8개의 ECS Application Foundation과 짧은 Runtime ON/OFF 검증 순서를 정의한다. 실제 Secret 값, Account ID, ECR Digest와 저장 Plan 파일은 문서나 Git에 기록하지 않는다.

## 적용 단위

Application Foundation은 다음 무료 또는 실행 전 리소스를 유지한다.

- OCI Digest 고정 Task Definition 8개
- `desired_count=0`인 ECS Service 8개
- 서비스별 최소 권한 Execution Role/Policy와 7일 Log Group
- Cloud Map Private DNS Namespace와 Service 8개
- Gateway Target Group 2개
- Account `awsvpcTrunking=enabled`

Runtime ON에서만 다음 유료 리소스를 만든다.

- ECS ASG `min=1`, `desired=1`, `max=2`
- Backend Service별 Task 1개
- Public ALB와 HTTP Listener/Host Rule
- Valkey 7.2 `cache.t4g.micro` 단일 Node와 Redis Host SSM Parameter

RDS는 Terraform이 삭제·재생성하지 않는다. 별도 명령으로 시작하고 검증 후 다시 정지한다. NAT도 `enable_nat_gateway`로 별도 ON/OFF한다.

## 1. Source와 Image 고정

1. Backend/Frontend/Terraform 테스트를 모두 통과시킨다.
2. 변경을 Commit/Push해 Source SHA를 고정한다.
3. GitHub Actions GHCR Workflow를 `deploy_target=all-backend`로 실행해 Backend 8개만 서비스·Source SHA당 한 번 Build한다.
4. ECR Workflow는 같은 SHA의 GHCR OCI Digest를 재빌드 없이 Promote한다.
5. 서비스별 GHCR/ECR 최상위 Digest가 같은지 확인한다.
6. Git에서 제외된 `terraform.tfvars`의 `application_images` 8개 값을 ECR `repository@sha256:...`로 기록한다.

`latest` 또는 변경 가능한 SHA Tag만으로 Task Definition을 만들지 않는다.

## 2. Runtime Secret 초기화

로컬 Docker의 OAuth/Toss Secret Pair를 읽고 AWS Secret JSON Key를 초기화하는 스크립트의 SHA-256을 먼저 검토한다.

```powershell
Get-FileHash C:\Portfolio\infra\aws\scripts\Initialize-LearningRuntimeSecrets.ps1 -Algorithm SHA256
```

명시적으로 승인한 뒤에만 실행한다.

```powershell
& C:\Portfolio\infra\aws\scripts\Initialize-LearningRuntimeSecrets.ps1 `
  -ExpectedAccountId <12자리 Account ID> `
  -Confirm
```

스크립트는 기존 Member BFF/Stock DB Key를 보존하고 다음 Key만 추가·초기화한다.

| Secret | JSON Key |
| --- | --- |
| Member BFF | 기존 `db_username`, `db_password` + `bff_client_secret` |
| Stock Service | 기존 `db_username`, `db_password` + `toss_api_client_secret` |
| Admin BFF | `admin_bff_client_secret` |
| Authorization Server | `bff_client_secret_hash`, `admin_bff_client_secret_hash` |
| Shared Redis | AWS 생성 `redis_password` |
| Shared Internal API | AWS 생성 `internal_api_token` |

Secret Value는 출력하지 않는다. 검증은 Secret별 `AWSCURRENT` 존재와 승인된 JSON Key 이름만 확인한다.

## 3. Application Foundation OFF Plan

먼저 `enable_application_runtime_foundation=true`, `learning_runtime_enabled=false`로 저장 Plan을 만든다. 이 단계는 RDS를 시작하거나 Valkey/ALB/EC2를 만들지 않는다.

검토 기준:

- Task Definition, ECS Service, Cloud Map, IAM/Log와 Target Group만 추가
- ECS Service 8개 모두 `desired_count=0`
- Image 8개 모두 `@sha256:` 고정
- 서비스별 Execution Role이 자기 ECR/Log와 필요한 Secret ARN만 읽음
- `awsvpcTrunking=enabled`
- RDS/NAT/VPC 교체·삭제 없음
- ALB·Valkey·EC2 실행 용량 없음

Plan 파일 SHA-256을 승인받은 뒤 그 파일만 Apply한다. Apply 후 ECS Service 8개가 모두 Desired/Running/Pending `0/0/0`이고 재계획이 `No changes`인지 확인한다.

## 4. Runtime ON

1. AWS 로그인 주체, Account와 `ap-northeast-2`를 확인한다.
2. RDS를 시작하고 `available`까지 기다린다.
3. NAT가 `available`, Private App 기본 경로가 `active`인지 확인한다.
4. Shared Redis Secret의 `redis_password`를 화면에 출력하지 않고 `TF_VAR_redis_password` Ephemeral Variable로 설정한다.
5. `learning_runtime_enabled=true` 저장 Plan을 만든다.

ON Plan 고정비 검토 기준은 대략 다음과 같다.

- `m6i.xlarge`: USD 0.236/시간
- NAT Gateway: USD 0.059/시간, 처리비 별도
- Public IPv4: USD 0.005/시간
- Valkey `cache.t4g.micro`: USD 0.0192/시간
- Application ALB: USD 0.0225/시간, LCU 별도
- RDS `db.t4g.micro`: USD 0.025/시간, Storage 별도

합계는 EBS, Secret, Storage, Data 처리와 전송을 제외하고 약 USD 0.3667/시간이다. 실제 Price List와 Plan을 다시 확인하고 새 SHA-256 승인을 받은 뒤에만 Apply한다.

## 5. Smoke Test

- Valkey 상태 `available`, TLS/RBAC 활성
- ECS Container Instance `ACTIVE`, `awsvpcTrunking`으로 Task ENI 8개 수용
- ECS Service 8개 Desired/Running/Pending `1/1/0`
- Container Health `HEALTHY`, Deployment Rollback 없음
- Cloud Map A Record 8개와 서비스별 고정 포트 내부 호출
- ALB Target Health는 Member/Admin Gateway 모두 `healthy`
- DB 3개 서비스가 `ddl-auto=validate`, Flyway 비활성으로 시작
- Admin Registration Route가 AWS에서 등록되지 않음
- Admin Session 응답과 Frontend에 원본 `sessionId`가 없음
- Kafka가 비활성이고 Toss API 미구성 시 내부 Health가 불필요하게 실패하지 않음

ACM/Route 53/CloudFront 단계 전의 HTTP ALB는 Infrastructure Health 검증용이다. 실제 OAuth Browser Flow 완료 판정은 HTTPS와 Public DNS 적용 후 수행한다.

## 6. Runtime OFF

1. `learning_runtime_enabled=false` 새 저장 Plan을 만든다.
2. Service 8개가 `desired_count=0`, ASG가 `0/0/0`이 되는지 확인한다.
3. ALB, Listener/Rule, Valkey와 Redis Host Parameter가 삭제되는지 확인한다.
4. Task Definition/Service/Cloud Map/Target Group/IAM/Log는 유지되는지 확인한다.
5. Plan SHA-256 승인 후 Apply한다.
6. ECS Running/Pending Task와 Container Instance가 0인지 확인한다.
7. RDS를 정지하고 `stopped` 및 `AutomaticRestartTime`을 기록한다.
8. 필요하지 않으면 별도 Plan으로 NAT를 OFF하고 고정 EIP만 유지한다.
9. 재계획 `No changes`와 Budget을 확인한다.

Runtime ON Plan에 사용한 Ephemeral Redis Password 환경 변수는 작업 종료 즉시 현재 PowerShell Session에서 제거한다.
