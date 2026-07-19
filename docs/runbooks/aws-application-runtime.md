# AWS Learning Application Runtime Runbook

이 Runbook은 Backend 8개의 ECS Application Foundation과 짧은 Runtime ON/OFF 검증 순서를 정의한다. 실제 Secret 값, Account ID, ECR Digest와 저장 Plan 파일은 문서나 Git에 기록하지 않는다.

> 실행 상태(2026-07-19): Runtime ON과 서비스 계약 교정, curl Smoke 6/6 검증 후 Runtime OFF까지 완료했다. 현재 ECS Service 8개 `0/0/0`, Task·Container Instance·EC2 0, ASG `0/0/0`, Public ALB·Valkey·Redis Host Parameter 삭제, RDS `stopped`이며 Terraform 재계획은 `No changes`다. Cloud Map Service 8개, Task Definition 8개와 Gateway Target Group 2개는 다음 ON을 위해 유지한다.

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
6. Git에서 제외된 `terraform.tfvars` 또는 현재 PowerShell Session의 `TF_VAR_application_images`에 ECR `repository@sha256:...` 8개를 주입한다. 일회성 Plan은 메모리 환경 변수를 우선하고 작업 뒤 즉시 제거한다.

`latest` 또는 변경 가능한 SHA Tag만으로 Task Definition을 만들지 않는다.

현재 실행 기록:

- Source SHA: `a7b3e0387c6817fd5a781ccf3ac532e04f38c9e1`
- GHCR Backend 8개 Build Once: GitHub Actions Run `29648349144`
- ECR 재빌드 없는 Promote: GitHub Actions Run `29648492164`
- 검증 결과: 서비스별 최상위 OCI Digest 8/8 일치

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

> 실행 완료: 승인된 스크립트 Hash를 재확인한 뒤 기존 DB Key를 보존하면서 Runtime Secret 6개를 초기화했다. Secret Value를 출력하지 않고 각 Secret의 `AWSCURRENT` 1개와 위 JSON Key 계약 6/6을 검증했다.

## 3. Application Foundation OFF Plan

먼저 `enable_application_runtime_foundation=true`, `learning_runtime_enabled=false`로 저장 Plan을 만든다. 이 단계는 RDS를 시작하거나 Valkey/ALB/EC2를 만들지 않는다.

검토 기준:

- Task Definition, ECS Service, Cloud Map, IAM/Log와 Target Group만 추가
- ECS Service 8개 모두 `desired_count=0`
- Image 8개 모두 `@sha256:` 고정
- 서비스별 Execution Role이 자기 ECR/Log와 필요한 Secret ARN만 읽음
- `awsvpcTrunking=enabled`
- Cloud Map Service 8개에 ECS 관리형 custom health와 `failure_threshold=1` 명시
- RDS/NAT/VPC 교체·삭제 없음
- ALB·Valkey·EC2 실행 용량 없음

Plan 파일 SHA-256을 승인받은 뒤 그 파일만 Apply한다. Apply 후 ECS Service 8개가 모두 Desired/Running/Pending `0/0/0`이고 재계획이 `No changes`인지 확인한다.

> 실행 완료: 최초 OFF Plan은 56개 리소스를 추가했고 ECS/ASG 0, RDS 정지와 유료 Public ALB·Valkey 미생성을 확인했다. AWS가 빈 custom health block을 상태에 남기지 않아 재계획이 수렴하지 않는 문제는 `failure_threshold=1` 명시와 회귀 테스트로 보정했다. 교정 Apply 뒤 Cloud Map custom health 8/8, Remote State 165개 주소와 재계획 `No changes`를 확인했다.

## 4. Runtime ON

1. AWS 로그인 주체, Account와 `ap-northeast-2`를 확인한다.
2. RDS를 시작하고 `available`까지 기다린다.
3. NAT가 `available`, Private App 기본 경로가 `active`인지 확인한다.
4. Shared Redis Secret의 `redis_password`를 화면에 출력하지 않고 `TF_VAR_redis_password` Ephemeral Variable로 설정한다.
5. `learning_runtime_enabled=true` 저장 Plan을 만든다.

ON Plan 고정비 검토 기준은 대략 다음과 같다.

- `m6i.xlarge`: USD 0.236/시간
- NAT Gateway: USD 0.059/시간, 처리비 별도
- Public IPv4: 기존 NAT EIP USD 0.005/시간 + ALB가 두 AZ에서 사용하는 IPv4 2개 USD 0.010/시간
- Valkey `cache.t4g.micro`: USD 0.0192/시간
- Application ALB: USD 0.0225/시간, LCU 별도
- RDS `db.t4g.micro`: USD 0.025/시간, Storage 별도

EC2 1대 기준 합계는 EBS, Secret, Storage, LCU, Data 처리와 전송을 제외하고 약 USD 0.3767/시간이다. 현재도 발생하는 NAT/EIP USD 0.064/시간을 제외한 Runtime ON 증분은 약 USD 0.3127/시간이다. Managed Scaling으로 EC2가 2대가 되면 같은 기준 약 USD 0.6127/시간까지 증가한다. 실제 Price List와 Plan을 다시 확인하고 새 SHA-256 승인을 받은 뒤에만 Apply한다.

> 첫 Apply 기록: 승인된 Runtime ON Plan은 ASG를 `1/1/2`로 올리고 Valkey Application User·Subnet Group·Parameter Group을 만든 뒤, Valkey 엔진이 인증 없는 `default` 사용자를 거부해 중단됐다. ECS Service는 `0/0/0`을 유지했으며 ALB, Listener/Rule, Valkey Node, User Group과 Redis Host Parameter는 생성되지 않았다. 실패한 Plan은 State 변경으로 무효화하므로 재사용하지 않는다.

> 복구 Plan 준비 완료(Apply 전): `tfplan-application-runtime-on-recovery`는 7개 생성, 8개 갱신, 삭제·교체 0개다. Public ALB·Listener·Host Rule 2개, Password 인증 Application User만 포함하는 Valkey User Group, 단일 Valkey Node와 Redis Host Parameter를 만들고 ECS Service 8개 Desired를 1로 바꾼다. SHA-256은 `3cd3eeae10b6cdb3182d0c1e727056f1a87570eea279e73ea98fd84db8b1980e`이며 Redis Password가 Plan JSON·파일에 남지 않음을 확인했다. 새 Hash 승인 전에는 Apply하지 않는다.

> 복구 Apply와 Smoke 진단: 복구 Plan Apply는 성공했고 Valkey `available`, TLS·저장 암호화·Password RBAC, RDS `available`, ASG `1/1/2`, ALB Target 2/2 `healthy`를 확인했다. Member BFF 로그에서는 `WRONGPASS`, Stock Service 로그에서는 빈 `TOSS_API_CLIENT_ID` Binding 실패를 확인했다. 반복 재시작을 막기 위해 두 서비스만 Desired 0으로 일시 정지했다.

> 서비스 계약 교정 Plan 준비 완료(Apply 전): `tfplan-runtime-on-service-contract-fix`는 Admin BFF·Authorization Server·Member BFF·Stock Service Task Definition 4개를 새 Revision으로 교체하고 같은 ECS Service 4개를 갱신한다. Redis 사용 Task에 `SPRING_DATA_REDIS_USERNAME=spring-msa`를 추가하고 Stock에는 Git 제외 로컬 환경의 비어 있지 않은 학습용 Toss Client ID를 전달한다. SHA-256은 `cf805d691a67cc0f40087f56a561427ab6790894bc5c6a8ff9180b4fc7b5f847`이며 Data, ALB, Valkey, RDS, Network, IAM과 Image Digest 변경은 없다. 원본 Secret 값은 Plan에 포함되지 않았다.

> 서비스 계약 교정 Apply 결과: `4 added, 4 changed, 4 destroyed`이며 Destroy는 이전 Task Definition Revision 등록 해제다. 교정 뒤 Member BFF와 Stock Service 최신 로그에서 이전 오류가 재발하지 않고 Spring 시작 완료를 확인했다. Service 8개 `1/1/0`, Task/Container `HEALTHY` 8/8, Digest 8/8, Cloud Map A 등록 8/8, ALB Target 2/2, 아래 curl 6/6 HTTP 200과 재계획 `No changes`를 확인했다.

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

> Smoke 완료: 아래 여섯 curl은 모두 HTTP 200이었다. Admin 익명 `/auth/me` 응답에는 원본 `sessionId`가 없고 AWS Task의 `ADMIN_BFF_REGISTRATION_ENABLED=false`도 확인했다. 비등록 `/registration/admin`에 CSRF를 포함한 빈 요청을 보내면 공통 예외 처리기가 `NoResourceFoundException`을 500으로 매핑하는 별도 문제가 있으며 실제 가입 Controller 실행이나 데이터 생성은 없었다. 404 매핑 개선은 Backend 이미지 변경이 필요한 후속 항목이다.

Runtime ON Apply와 AWS 상태 검증을 마친 뒤 아래 `curl.exe` 명령으로 실제 ALB 경로를 확인한다. 두 Host Header는 DNS 적용 전에도 ALB Listener Rule을 정확히 선택하기 위해 필요하다.

```powershell
$AlbDns = terraform -chdir=C:\Portfolio\infra\aws\terraform output -raw public_alb_dns_name
if ([string]::IsNullOrWhiteSpace($AlbDns)) {
  throw "public_alb_dns_name Terraform Output이 비어 있습니다."
}

# Public ALB -> Member/Admin Gateway 자체 Readiness
curl.exe --fail --silent --show-error --connect-timeout 10 --max-time 30 `
  -H "Host: app.hyuncloudlab.com" `
  "http://$AlbDns/actuator/health/readiness"
curl.exe --fail --silent --show-error --connect-timeout 10 --max-time 30 `
  -H "Host: admin.hyuncloudlab.com" `
  "http://$AlbDns/actuator/health/readiness"

# Member/Admin Gateway -> Cloud Map -> Authorization Server
curl.exe --fail --silent --show-error --connect-timeout 10 --max-time 30 `
  -H "Host: app.hyuncloudlab.com" `
  "http://$AlbDns/.well-known/openid-configuration"
curl.exe --fail --silent --show-error --connect-timeout 10 --max-time 30 `
  -H "Host: admin.hyuncloudlab.com" `
  "http://$AlbDns/.well-known/openid-configuration"

# Public ALB -> Gateway -> Cloud Map -> Member/Admin BFF
curl.exe --fail --silent --show-error --connect-timeout 10 --max-time 30 `
  -H "Host: app.hyuncloudlab.com" `
  "http://$AlbDns/bff/health"
curl.exe --fail --silent --show-error --connect-timeout 10 --max-time 30 `
  -H "Host: admin.hyuncloudlab.com" `
  "http://$AlbDns/admin-bff/health"
```

여섯 요청은 모두 HTTP 2xx여야 한다. 응답 본문이나 Header에 Secret, Cookie 또는 Session ID가 나타나면 기록하거나 Commit하지 말고 즉시 실패로 처리한다. 나머지 User/Community/Stock 내부 서비스는 외부 Health Route를 추가하지 않고 ECS Container Health와 Cloud Map 조회로 검증한다.

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

> Runtime OFF 완료: 승인된 `tfplan-runtime-off-after-application-smoke` SHA-256 `f9f827bccc98316d1ccbbbf1169588f11232bba88c45f2a875db9a8de501940c`를 그대로 Apply해 `0 added, 9 changed, 10 destroyed`로 완료했다. ECS Service 8개 `0/0/0`, Task·Container Instance·EC2 0과 ASG `0/0/0`, Public ALB·Valkey·Redis Host Parameter 삭제를 확인했다. Cloud Map Service 8개는 등록 0, Task Definition 8개는 `ACTIVE`, Gateway Target Group 2개는 유지됐다. RDS는 `stopped`, 자동 재시작 예정은 2026-07-26 05:15:28 KST이며 Backup 7일·삭제 보호를 유지한다. 재계획은 `No changes`다.

Runtime ON Plan에 사용한 Ephemeral Redis Password 환경 변수는 작업 종료 즉시 현재 PowerShell Session에서 제거한다.
