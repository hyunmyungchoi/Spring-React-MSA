# AWS Frontend S3·CloudFront 배포 Runbook

이 Runbook은 여섯 Frontend를 서로 독립적으로 Build·배포하면서 공개 진입점은 Member와 Admin CloudFront 두 개로 유지하는 절차를 정의한다.

> 실행 상태(2026-07-19): Terraform module, CloudFront 경로 함수, 선택 배포 Workflow와 계약 테스트를 구현했다. pnpm `10.0.0`으로 여섯 산출물의 lint·build를 검증했고 Terraform `validate`, `test` 20/20을 통과했다. 승인한 Saved Plan을 Apply해 S3 6개·CloudFront 2개와 배포 IAM을 생성하고 AWS 계약과 재계획 `No changes`를 검증했다. GitHub Variable 3개를 연결하고 첫 `all` 배포와 CloudFront curl 6/6 HTTP 200까지 완료했다.

## 배포 경계

| 배포 대상 | Workspace·Script | 전용 S3 | CloudFront 경로 |
| --- | --- | --- | --- |
| `spring-member-web` | `member`, `build:prod` | `member` | Member 기본 경로 |
| `spring-community-web` | `member`, `build:community` | `community` | `/community/*` |
| `spring-stock-web` | `member`, `build:stock` | `stock` | `/stock/*` |
| `spring-admin-web` | `admin`, `build:prod` | `admin` | Admin 기본 경로 |
| `spring-admin-users-web` | `admin`, `build:users` | `admin-users` | `/manage/users/*` |
| `spring-admin-logs-web` | `admin`, `build:logs` | `admin-logs` | `/manage/logs/*` |

S3 Bucket은 여섯 개이며 모두 Private, `BucketOwnerEnforced`, Public Access 전체 차단, SSE-S3, Versioning을 사용한다. CloudFront만 Origin Access Control의 항상 서명된 SigV4 요청으로 객체를 읽는다. Member와 Admin Distribution은 각각 세 S3 Origin을 가지며 CloudFront Function이 SPA 문서와 정적 Asset 경로를 해당 Origin 안에서 다시 쓴다. [AWS OAC 문서](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)의 일반 S3 Origin 방식을 사용하고 S3 Website Endpoint는 사용하지 않는다.

`/stock` 같은 Prefix 자체는 `/stock/`로 리다이렉트한 뒤 Stock 동작으로 들어간다. 동작 패턴은 `/stock/*`이므로 `/stockholm`처럼 Prefix 경계 밖인 URI를 Stock Origin으로 보내지 않는다. Community와 Admin 하위 앱도 같은 규칙을 사용한다.

## 1. 로컬 계약 검증

```powershell
Set-Location C:\Portfolio\FrontEnd
corepack prepare pnpm@10.0.0 --activate
pnpm install --frozen-lockfile

pnpm --filter member run lint
pnpm --filter member run build:prod
pnpm --filter member run build:community
pnpm --filter member run build:stock

pnpm --filter admin run lint
pnpm --filter admin run build:prod
pnpm --filter admin run build:users
pnpm --filter admin run build:logs

Set-Location C:\Portfolio
python -m unittest `
  infra/ci/test_select_frontend_deploy_matrix.py `
  infra/ci/test_aws_frontend_deploy_workflow.py

Set-Location C:\Portfolio\infra\aws\terraform
terraform fmt -check -recursive
terraform validate
terraform test
```

`FrontEnd/pnpm-lock.yaml`이 바뀌면 검증을 중단한다. 산출물은 여섯 Entry 문서가 모두 남아 있어야 한다.

AWS Workflow는 Admin Build에 `VITE_ADMIN_REGISTRATION_ENABLED=false`를 고정한다. 이 값은 가입 탭을 표시하지 않게 하지만 실제 보안 경계는 AWS Admin BFF의 조건부 Controller 비등록이다. 최초 관리자 Bootstrap 변경 뒤 `spring-admin-web`을 다시 배포하고 화면 비노출과 Backend 404를 함께 검증한다.

## 2. Saved Plan Gate

현재 `terraform.tfvars`와 메모리 입력의 기존 Application Digest·Client ID를 그대로 유지한 상태에서 Frontend Flag만 활성화한다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
terraform plan `
  -var="enable_frontend_hosting=true" `
  -out=tfplan-frontend-hosting-foundation
terraform show -no-color .\tfplan-frontend-hosting-foundation
Get-FileHash .\tfplan-frontend-hosting-foundation -Algorithm SHA256
```

검토 기준은 다음과 같다.

- Private S3 Bucket 여섯 개와 각 보안·Versioning·Lifecycle·Bucket Policy만 추가
- CloudFront Distribution 두 개, Origin Access Control 한 개와 경로 함수 두 개만 추가
- `master` branch의 GitHub OIDC Subject만 신뢰하는 Frontend 배포 Role과 최소 권한 Policy만 추가
- 기존 VPC, NAT, RDS, ECS, ECR, Secret, Cloud Map과 Task Definition은 변경·교체·삭제 0개
- `learning_runtime_enabled=false`, ECS/ASG 0과 RDS 정지 상태 유지

Plan 파일의 정확한 SHA-256을 별도 승인받은 뒤 해당 파일만 Apply한다. 인자 없는 `terraform apply`는 사용하지 않는다.

검토 완료한 Saved Plan은 다음과 같다.

- 경로: `C:\Portfolio\infra\aws\terraform\tfplan-frontend-hosting-foundation`
- 크기: 143,862 bytes
- SHA-256: `f49031685f65ff8ed8274316e34e1c195431a3d1912ac279114b14b23f0aa5e8`
- 요약: `49 to add, 0 to change, 0 to destroy`
- 생성 범위: S3 관련 42개, CloudFront Distribution 2개·Function 2개·OAC 1개, GitHub IAM Role·Policy 각 1개
- 기존 Foundation·Runtime 변경·교체·삭제: 0개

이 Plan은 승인된 SHA 그대로 Apply해 `49 added, 0 changed, 0 destroyed`로 완료했다. S3 6/6의 Public Access 차단·BucketOwnerEnforced·SSE-S3·Versioning·Lifecycle·OAC 전용 Policy, CloudFront 2/2 `Deployed`와 Origin 3+3·경로 네 개·Function 연결 6개, Function 2/2 `DEPLOYED`, OAC `always/sigv4`, GitHub OIDC `master` 전용 Trust를 실제 AWS에서 확인했다. 같은 입력 재계획은 `No changes`였다. 적용된 Plan 파일은 재사용하지 않고 검증 뒤 로컬에서 삭제한다.

## 3. Apply 뒤 GitHub 연결

Apply 뒤 Output을 사용해 세 Repository Variable을 등록한다. Account ID, Bucket 이름과 Distribution ID를 문서나 Git에 기록하지 않는다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
$roleArn = terraform output -raw github_actions_frontend_role_arn
$distributionIds = terraform output -json frontend_cloudfront_distribution_ids | ConvertFrom-Json

gh variable set AWS_FRONTEND_DEPLOY_ROLE_ARN `
  --repo hyunmyungchoi/Spring-React-MSA `
  --body $roleArn
gh variable set AWS_MEMBER_CLOUDFRONT_DISTRIBUTION_ID `
  --repo hyunmyungchoi/Spring-React-MSA `
  --body $distributionIds.member
gh variable set AWS_ADMIN_CLOUDFRONT_DISTRIBUTION_ID `
  --repo hyunmyungchoi/Spring-React-MSA `
  --body $distributionIds.admin
```

Distribution 생성에는 시간이 걸릴 수 있다. Status가 `Deployed`가 되고 Bucket Policy가 정확한 Distribution ARN만 허용하는지 확인한다. Bucket이 비어 있는 동안 CloudFront의 403은 예상 상태이며 배포 성공으로 판정하지 않는다.

> 실행 완료: Role ARN과 Member/Admin Distribution ID를 값 비노출 방식으로 Repository Variable 세 곳에 등록하고 변수 이름과 Active Workflow를 확인했다.

## 4. 선택 배포

Stock만 배포하는 명령은 다음과 같다.

```powershell
gh workflow run aws-frontend-deploy.yml `
  --repo hyunmyungchoi/Spring-React-MSA `
  --ref master `
  -f deploy_target=spring-stock-web
```

이 실행은 `member` Workspace의 `build:stock`만 실행하고 Stock 전용 Bucket만 동기화하며 Member Distribution의 `/stock`, `/stock/*`만 무효화한다. Member와 Community Bucket에는 Upload/Delete를 수행하지 않는다.

그룹 배포는 `all-member`, `all-admin`, 전체 배포는 `all`을 사용한다. Workflow 동시 실행은 직렬화해 같은 Distribution의 Invalidation과 배포 순서가 서로 추월하지 않게 한다.

첫 전체 배포 기록:

- Source SHA: `f29249373feae470e2c30758e3245d43d22fef25`
- GitHub Actions: [Run 29677216377](https://github.com/hyunmyungchoi/Spring-React-MSA/actions/runs/29677216377)
- 결과: Matrix 준비·계약 테스트 성공, Frontend 6개 Job과 pnpm/Build/S3 Sync/Invalidation 필수 단계 24/24 성공

## 5. curl Smoke Test

첫 전체 배포 뒤 CloudFront 기본 Domain으로 여섯 정적 진입점을 확인한다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform
$domains = terraform output -json frontend_cloudfront_domain_names | ConvertFrom-Json

curl.exe --fail --silent --show-error --location --head "https://$($domains.member)/"
curl.exe --fail --silent --show-error --location --head "https://$($domains.member)/community/"
curl.exe --fail --silent --show-error --location --head "https://$($domains.member)/stock/"
curl.exe --fail --silent --show-error --location --head "https://$($domains.admin)/"
curl.exe --fail --silent --show-error --location --head "https://$($domains.admin)/manage/users/"
curl.exe --fail --silent --show-error --location --head "https://$($domains.admin)/manage/logs/"
```

모두 HTTP 2xx여야 한다. Stock 단독 재배포 뒤에는 Stock Asset과 Entry 문서만 바뀌고 Community 객체 Version과 페이지 응답이 유지되는지도 확인한다.

> 첫 Smoke 완료: S3 Entry 문서의 `no-cache`와 Hash Asset의 1년 immutable Cache metadata를 6/6 확인했다. Member Root·Community·Stock과 Admin Root·Users·Logs의 curl은 모두 HTTP 200·HTML이었다.

## 6. Custom Domain과 API Origin 후속 단계

- 현재 AWS에 적용된 Distribution은 정적 S3 Origin만 가진다. 코드에는 API, OAuth2, Logout, Session과 WebSocket의 HTTPS ALB Origin, Custom Domain과 Root Redirect가 구현됐지만 별도 Saved Plan Gate 전에는 적용하지 않는다.
- 적용 순서는 State 권한 확장, Global DNS Hosted Zone Import·ACM DNS 검증, Runtime State의 CloudFront/Route 53/ALB TLS 변경이다. 상세 절차는 [AWS Domain·TLS Runbook](aws-domain-tls.md)을 따른다.
- Runtime OFF에서는 정적 화면은 제공할 수 있지만 Backend API는 제공하지 않는다. API 의존 기능은 사용자에게 비활성 상태를 표시해야 한다.
- 여섯 S3 Bucket과 두 CloudFront Distribution은 사용량 기반 Storage·Request·전송·Invalidation 비용이 발생할 수 있다.

## 7. 롤백

오류가 난 배포 단위만 정상 Git Revision으로 되돌린 뒤 같은 `deploy_target`으로 다시 배포한다. Bucket Versioning은 보조 복구 수단이지만 HTML과 Hash Asset의 일관성을 함께 맞춰야 하므로 개별 객체 한 개만 임의 복구하지 않는다. 다른 Frontend Bucket을 동기화하거나 전체 Distribution을 무효화하지 않는다.
