# AWS Route 53·ACM·TLS·API Origin

이 Runbook은 네 Public Hostname을 세 단계의 Saved Plan으로 연결한다.

| Hostname | Target | 수명주기 |
| --- | --- | --- |
| `hyuncloudlab.com` | Member CloudFront에서 `app`으로 308 Redirect | 상시 |
| `app.hyuncloudlab.com` | Member CloudFront | 상시 |
| `admin.hyuncloudlab.com` | Admin CloudFront | 상시 |
| `origin.hyuncloudlab.com` | Runtime ON의 Public ALB | ON 동안만 |

인증서와 ACM 검증 CNAME은 상시 유지한다. Runtime OFF에는 정적 Frontend와 세 Public Alias가 남지만 `origin` Alias, ALB, ECS Task와 Valkey는 없다.

## 1. Gate A — Global DNS State 권한

```powershell
Set-Location C:\Portfolio\infra\aws\bootstrap\terraform-state
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan `
  -var='additional_state_keys=["global/dns/terraform.tfstate"]' `
  -out=tfplan-global-dns-state-access
terraform show -no-color .\tfplan-global-dns-state-access
Get-FileHash .\tfplan-global-dns-state-access -Algorithm SHA256
```

기존 State Bucket, Runtime State Object와 IAM Role은 삭제·교체하지 않는다. State Role Inline Policy 한 개가 `global/dns/terraform.tfstate`와 `.tflock` Object만 추가 허용해야 한다. Plan Hash를 승인받은 뒤 저장 Plan만 Apply하고 재계획 `No changes`를 확인한다.

Gate A Saved Plan `tfplan-global-dns-state-access`, 12,777 bytes, SHA-256 `ec3f8c9627d3e863d9b36b91cb69ab97b20ab613d35e21300516260bd09a95f6`를 승인된 그대로 Apply했다. 결과는 `0 add, 1 change, 0 destroy`이며 실제 IAM Policy의 State Object 2개·Lock Object 2개, Wildcard 0개와 재계획 `No changes`를 확인했다.

## 2. Gate B — 기존 Hosted Zone Import와 ACM

`infra/aws/global/dns/backend.s3.hcl.example`과 `terraform.tfvars.example`을 Git 제외 파일로 복사한다. Zone ID, State Role ARN과 Bucket 이름은 화면·문서·Git에 기록하지 않는다.

```powershell
Set-Location C:\Portfolio\infra\aws\global\dns
terraform init -backend-config=backend.s3.hcl
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan -out=tfplan-global-dns
terraform show -no-color .\tfplan-global-dns
Get-FileHash .\tfplan-global-dns -Algorithm SHA256
```

Plan은 기존 Public Hosted Zone Import, `prevent_destroy`, ACM 인증서 두 개와 DNS 검증 CNAME만 포함해야 한다. 새 Hosted Zone, NS/SOA Record 삭제·재생성, Registrar 변경은 허용하지 않는다. 승인된 Plan 적용 뒤 두 인증서가 `ISSUED`이고 재계획이 `No changes`인지 확인한다.

Gate B Saved Plan `tfplan-global-dns`, 8,483 bytes, SHA-256 `ce6c35edd8f4a156c6e506cfab8856f218ed5652273b1e44a65701df70a49517`를 승인된 그대로 Apply했다. 결과는 `8 added, 1 changed, 0 destroyed`다. 기존 Hosted Zone Import, 두 인증서 `ISSUED`, DNS 검증 `3+1`, NS 1·SOA 1·검증 CNAME 4·Application Alias 0과 재계획 `No changes`를 확인했다. 적용 Plan은 재사용하지 않고 삭제했다.

## 3. Gate C — CloudFront Domain과 HTTPS Origin

기존 Runtime 입력과 8개 ECR Digest를 그대로 유지하고 `enable_public_domain_routing=true`만 추가한다. Runtime은 OFF 상태를 유지한다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan `
  -var="enable_frontend_hosting=true" `
  -var="enable_public_domain_routing=true" `
  -var="learning_runtime_enabled=false" `
  -out=tfplan-public-domain-routing
terraform show -no-color .\tfplan-public-domain-routing
Get-FileHash .\tfplan-public-domain-routing -Algorithm SHA256
```

검토 대상은 다음과 같다.

- Member Alias: Root와 `app`; Admin Alias: `admin`
- Route 53 A/AAAA Alias 각 3개
- 두 Distribution에 `origin` HTTPS Custom Origin
- Member API/BFF/OAuth Behavior 8개, Admin 7개
- API Behavior는 Cache Disabled, AllViewer, 전체 HTTP Method, SPA Function 미연결
- ALB SG의 Public 80/443 제거와 CloudFront origin-facing Prefix List 443 추가
- Runtime OFF 유지: ALB와 `origin` Record 생성 0, ECS/ASG 0, RDS 상태 변경 없음

Gate C Saved Plan은 `tfplan-public-domain-routing`, 176,890 bytes, SHA-256 `e692c4141975e8641bd65bf76b278ba86762c9b0615b8aa0ec45f55d08c4db90`다. 승인된 Plan을 그대로 Apply했고 결과는 `8 added, 3 changed, 2 destroyed`다.

- 생성 8: Root Redirect Function 1, Root·Member·Admin A/AAAA Alias 6, CloudFront origin-facing Prefix List HTTPS 규칙 1
- 변경 3: Member/Admin Distribution 각 1, Member SPA Function 1
- 삭제 2: 기존 ALB Public CIDR HTTP 80·HTTPS 443 규칙만 삭제
- Member API/BFF/OAuth Behavior 8, Admin 7; Cache Disabled·AllViewer·전체 Method·SPA Rewrite 미연결
- Runtime 변경 0: ECS Service 8개 0, ASG `0/0/0`, RDS `stopped`, ALB·Valkey·`origin` Alias 0
- Plan의 Ephemeral Redis Password 값: `null`

사후 검증에서 CloudFront 2/2 `Deployed`, Function 3/3 `DEPLOYED`, API Behavior Member 8·Admin 7, A/AAAA `3+3`, Public ALB CIDR Ingress 0과 CloudFront Prefix List HTTPS 1개를 확인했다. 정적 curl 6/6 HTTP 200, Root 308과 Path·Query 보존, Runtime OFF API 502, TLS 검증 성공을 확인했다. ECS 8개 0, ASG `0/0/0`, RDS `stopped`, ALB·Valkey 0과 재계획 `No changes`를 확인한 뒤 적용 Plan을 삭제했다.

CloudFront 배포 완료와 재계획 `No changes` 뒤 정적·Redirect를 확인한다.

```powershell
curl.exe --fail --silent --show-error --location --head https://app.hyuncloudlab.com/
curl.exe --fail --silent --show-error --location --head https://app.hyuncloudlab.com/community/
curl.exe --fail --silent --show-error --location --head https://app.hyuncloudlab.com/stock/
curl.exe --fail --silent --show-error --location --head https://admin.hyuncloudlab.com/
curl.exe --fail --silent --show-error --location --head https://admin.hyuncloudlab.com/manage/users/
curl.exe --fail --silent --show-error --location --head https://admin.hyuncloudlab.com/manage/logs/
curl.exe --silent --show-error --head https://hyuncloudlab.com/
```

Root는 `https://app.hyuncloudlab.com/...` 308, 나머지 정적 경로는 HTTP 2xx여야 한다. Runtime OFF에서 API 경로의 5xx는 예상 상태이며 성공으로 판정하지 않는다.

## 4. Runtime ON Full Smoke

RDS 시작, NAT·Runtime ON은 기존 [AWS Application Runtime](aws-application-runtime.md)의 별도 Saved Plan Gate를 따른다. ON Plan에는 HTTPS ALB Listener, `origin` A Alias와 ECS/Valkey/ASG 실행 용량이 포함되고 HTTP Listener는 없어야 한다.

2026-07-19 승인된 Saved Plan `tfplan-runtime-on-public-domain-smoke`, 189,515 bytes, SHA-256 `93ae37575bac8f8cacd6b29661df26d483d1a3c4852c4eab51e08621c6e55f2c`를 그대로 Apply했다. 결과는 `11 added, 9 changed, 0 destroyed`이며 적용 Plan은 검증 뒤 삭제했다.

- 변경 범위: `11 create, 9 update, 0 delete/replace`
- 생성: Public ALB 1, HTTPS 443 Listener 1, `app`·`admin` Host Rule 2, `origin` A Alias 1, Valkey와 Redis Host Parameter 6
- 갱신: ECS Service 8개 Desired `0 → 1`, ASG Min `0 → 1`과 Max `0 → 2`
- ASG의 구성 목표는 `1/1/2`다. Plan의 Desired가 `0`으로 유지돼 보이는 것은 Capacity Provider가 조정한 실제 Desired를 Terraform이 덮어쓰지 않도록 `ignore_changes`한 결과이며, Min 1 적용으로 최소 한 대를 기동한다.
- 무변경: RDS, CloudFront, VPC/Subnet/보안 그룹, Task Definition과 Image Digest
- HTTP Listener, 삭제와 교체는 없고 ALB Idle Timeout 3,600초와 TLS Policy `ELBSecurityPolicy-TLS13-1-2-2021-06`을 사용한다.
- Redis Password는 Secrets Manager에서 Ephemeral 입력으로만 읽었고 Plan JSON과 저장 Plan에 직렬화되지 않았다.
- 승인 전 실제 상태: ECS Service 8개 `0/0/0`, ASG `0/0/0`·Instance 0, RDS `stopped`, Public ALB·Valkey·`origin` A Alias 0

Apply 뒤 ECS Service와 Rollout 8/8, ASG `1/1/2`·Container Instance 1대, Valkey·RDS `available`, ALB `active`, Target 2/2 `healthy`를 확인했다. HTTPS 정적 6개와 Readiness·OIDC·BFF Health 6개는 모두 HTTP 200, Root는 Path·Query를 보존한 308이었고 TLS 검증도 통과했다. Runtime ON 재계획은 `No changes`다.

OAuth·Session Smoke는 무작위 강한 비밀번호의 `ROLE_USER` 계정을 값 비노출 방식으로 생성해 Member Password Login, Authorization Code, `BFFSESSIONID`, Member CSRF Heartbeat와 Logout을 검증했다. 같은 일반 회원의 Admin OAuth는 `admin_role_required`로 거부되고 Admin Session이 제거됐다. 원본 `sessionId`는 응답에 없었다. 성공·진단 과정에서 생성한 감사용 Smoke 계정 4개는 비밀번호를 폐기한 상태로 RDS에 남아 있으며 후속 관리자 Bootstrap·정리 절차에서 처리한다. 실제 `ROLE_ADMIN` 성공 Login은 최초 관리자 Bootstrap이 아직 없어 미검증이다.

WebSocket은 인증된 Cookie 3종과 `Origin: https://app.hyuncloudlab.com`을 사용해 HTTP Upgrade까지 성공했지만 `CONNECTED` 전 Close `1002 Protocol error`로 실패했다. 압축 협상 On/Off가 모두 같고 Member BFF 로그에는 Handler 오류가 없었다. 원인은 Member Gateway에 `/bff/**` 일반 `http://` Route만 있고 `/bff/chat/ws` 전용 `ws://` Route가 없는 구성으로 진단했다.

2026-07-19에 `/bff/chat/ws` 전용 `ws://` Route를 일반 BFF Route보다 높은 우선순위로 추가하고, AWS ECS Task Definition·Docker Compose·Kubernetes에 `GATEWAY_BFF_WEBSOCKET_URI` 계약을 반영했다. Gradle Gateway Route 테스트, Terraform 전체 23개 테스트와 Docker Compose 구문 검증은 통과했다. Source SHA `5fc26bdc355d0417d29bbc1941a0d9c0996e4200`은 [GHCR Run 29685219294](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29685219294)에서 Gateway 하나만 Build하고 [ECR Run 29685323647](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29685323647)에서 재빌드 없이 승격했으며, Kubernetes Bot Commit `522a0013f2daea87748c7ce16057128e4528b8fa`까지 OCI Digest가 일치한다. 아직 ECS Task Definition과 Service에는 적용하지 않았으므로 별도 Saved Plan Apply와 Runtime ON 뒤 공개 `wss://` 메시지 왕복을 재검증해야 한다.

비용 종료용 Saved Plan `tfplan-runtime-off-after-public-domain-smoke`, 195,052 bytes, SHA-256 `94aae90a5306d6b1c9aa8f597ca938213cbd39e9c872b0cc92212277608e53f3`를 승인된 그대로 Apply했다. 결과는 `0 added, 9 changed, 11 destroyed`이며 ECS Service 8개와 ASG를 0으로 내리고 ALB·HTTPS Listener·Host Rule 2개·`origin` A Alias와 폐기 가능한 Valkey 계열 6개만 삭제했다. RDS·CloudFront·Network·Task Definition/Image·IAM·Secret 변경은 없었고 Redis Password는 Apply 프로세스의 Ephemeral 입력 뒤 제거했다.

사후 실제 상태는 ECS Desired/Running/Pending 0, Container Instance·ASG Instance 0, ASG `0/0/0`, Public ALB·Valkey·`origin` A Alias 0이고 RDS는 `stopped`다. RDS 암호화·Private·삭제 보호와 Backup 7일을 유지하며 자동 재시작 예정은 2026-07-26 19:44:07 KST다. OFF 입력 재계획은 `No changes`, 정적 Custom Domain 6/6 HTTP 200, Root 308·Path/Query 보존, Runtime OFF API 502와 TLS를 확인했다. 적용한 OFF Plan은 재사용하지 않고 삭제했다.

검증 순서는 다음과 같다.

1. `origin` 인증서, ALB Listener 443, Target 2/2 Healthy
2. `curl`로 Member/Admin 정적 6개와 API Health 계약
3. 실제 브라우저로 Member/Admin OAuth Login, Session Cookie, CSRF와 Logout
4. `wss://app.hyuncloudlab.com/bff/chat/ws` Upgrade와 메시지 왕복
5. ALB SG에 Public CIDR 규칙 0개, CloudFront Prefix List 443 한 개
6. 재계획 `No changes`

검증 뒤 Runtime OFF Saved Plan을 별도 승인·Apply하고 ECS/ASG 0, ALB·`origin` Alias·Valkey 삭제와 RDS 정지를 확인한다.

## 5. 보안 제한

ALB는 [AWS 관리형 CloudFront origin-facing Prefix List](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/LocationsOfEdgeServers.html)에서 오는 443만 허용한다. 별도 Origin Secret Header는 Terraform State에 비밀을 기록하지 않기 위해 사용하지 않는다. 따라서 일반 Internet에서 ALB로 직접 접근할 수 없지만 다른 CloudFront Distribution과의 강한 배포 단위 식별은 제공하지 않는다. 이 제한을 해소하려면 [CloudFront의 ALB 접근 제한 방식](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/restrict-access-to-load-balancer.html)을 따라 Secret을 State 밖에서 주입·회전하고 ALB Listener Rule에서 검증하는 별도 설계와 승인 단계가 필요하다.
