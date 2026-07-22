# AWS 최초 관리자 Bootstrap Runbook

이 Runbook은 AWS Learning 환경에 최초 `ROLE_ADMIN` 계정을 한 번만 만들고 공개 관리자 가입 경로를 계속 닫아 두는 절차를 정의한다. 관리자 식별자, 비밀번호, Secret Value, Account ID, ECR Digest와 Saved Plan 파일은 문서나 Git에 기록하지 않는다.

> 저장소 상태(2026-07-23): User Service의 일회성 `AdminBootstrapMain`, 임시 ECS Task Definition·Execution Role·Secrets Manager Container, 7일 감사 Log Group, AWS Admin 가입 UI 비노출과 비등록 경로 404 처리를 구현했다. User Service PostgreSQL 통합 테스트, Admin BFF 테스트, Admin Frontend lint/build, Workflow 단위 테스트, Terraform `fmt`와 전체 mock 테스트 32/32를 통과했다. 아직 Commit/Push, Image Build Once·ECR Promote, AWS Apply, 관리자 생성과 공개 Domain Smoke는 수행하지 않았다.

## 보안 계약

- 공개 `POST /admin-bff/registration/admin`은 AWS ECS에서 `ADMIN_BFF_REGISTRATION_ENABLED=false`로 고정하고 Controller Bean 자체를 등록하지 않는다.
- AWS Admin 정적 Frontend는 `VITE_ADMIN_REGISTRATION_ENABLED=false`로 Build해 가입 탭을 표시하지 않는다. Backend 비등록이 실제 보안 경계이며 UI 비노출만으로 차단을 판정하지 않는다.
- 최초 관리자는 Public HTTP가 아니라 Private App Subnet의 ECS EC2 일회성 Task로만 생성한다.
- 입력 네 개(`login_id`, `email`, `password`, `username`)는 Terraform State에 넣지 않고 임시 Secrets Manager Secret Version으로 주입한다.
- Task Role은 두지 않는다. Execution Role은 해당 User Service ECR Image, 감사 Log Group, User Service DB Secret과 임시 Bootstrap Secret만 읽는다.
- 실행 시 비밀이 아닌 `ADMIN_BOOTSTRAP_AUDIT_ACTOR`와 `ADMIN_BOOTSTRAP_REQUEST_ID`를 `RunTask` override로 반드시 넣는다. Task Definition에는 실행 주체를 고정하지 않는다.
- BCrypt strength 12를 사용하며 비밀번호는 UTF-8 20~72 byte다. 평문 비밀번호와 PII는 Log에 남기지 않고 결과, 실행 주체, 요청 ID와 SHA-256 Identity Fingerprint만 기록한다.
- PostgreSQL `SERIALIZABLE` Transaction과 Advisory Transaction Lock으로 동시 실행을 직렬화한다. Admin이 없을 때만 `ROLE_USER`와 `ROLE_ADMIN`을 함께 생성한다.
- 같은 식별자·비밀번호 재실행은 `already_present`로 성공한다. 다른 Admin, 충돌 식별자, 비활성 계정, 역할 또는 비밀번호 불일치는 실패하고 Transaction을 Rollback한다.

## 1. 변경과 Image 검증

1. User Service, Admin BFF, Admin Frontend와 Terraform 테스트를 다시 실행한다.
2. 한 Source SHA로 Commit/Push한다.
3. GHCR에서 User Service와 Admin BFF를 Build Once한다.
4. 같은 OCI Digest를 재빌드 없이 ECR로 Promote하고 GHCR↔ECR Digest 일치를 확인한다.
5. AWS Admin Frontend는 `spring-admin-web`만 선택 배포한다. Workflow가 `VITE_ADMIN_REGISTRATION_ENABLED=false`를 주입하는지 확인한다.

Image 절차는 [AWS Image Build Once·ECR Promote](aws-image-build-once-promote.md), 정적 배포는 [AWS Frontend S3·CloudFront](aws-frontend-hosting.md)를 따른다.

## 2. Runtime OFF Foundation Saved Plan

RDS가 `stopped`이고 ECS/ASG/ALB/Valkey가 없는 상태에서 `enable_admin_bootstrap_foundation=true`와 검증한 새 Application Digest를 사용해 Saved Plan을 만든다.

Plan에는 다음만 포함돼야 한다.

- 새 User Service·Admin BFF Digest를 사용하는 Application Task Definition Revision과 필요한 ECS Service 갱신
- 임시 Admin Bootstrap Task Definition 1개
- 임시 Execution Role·Inline Policy 각 1개
- 값이 없는 임시 Secrets Manager Secret Container 1개
- `/ecs/<name-prefix>/admin-bootstrap` 7일 감사 Log Group 1개
- Runtime 유료 리소스 시작, RDS 변경, 공개 가입 활성화 없음

`terraform show`에서 Task Role 없음, Read-only Root, UID 65534, Digest Image, Secret 여섯 개, `ADMIN_BFF_REGISTRATION_ENABLED=false`와 IAM Secret Resource 정확히 두 개를 검토한다. Saved Plan SHA-256과 Add/Change/Destroy를 별도 승인받은 뒤에만 Apply한다.

## 3. 임시 Secret 초기화

Foundation Apply 후 네 Key를 가진 Secret Version을 Terraform 밖에서 한 번 생성한다.

| Key | 제약 |
| --- | --- |
| `login_id` | 공백 제거 후 1~50자 |
| `email` | 공백 제거·소문자 정규화 후 유효한 Email, 최대 255자 |
| `password` | UTF-8 20~72 byte, 재시도 검증 전까지 안전하게 보관 |
| `username` | 공백 제거 후 1~100자 |

PowerShell History와 Transcript에 실제 값이 남지 않게 대화형 입력을 사용하고 `aws secretsmanager put-secret-value`에 전달한 메모리 변수는 즉시 제거한다. Secret Value를 출력하는 `get-secret-value`, `echo`, `Write-Host`, Terraform variable 또는 Saved Plan 입력은 사용하지 않는다. 검증은 `AWSCURRENT` Version 1개와 승인된 Key 이름 네 개만 확인한다.

## 4. RDS·Runtime ON과 일회성 실행

1. RDS 시작 승인을 받고 `available`과 자동 Backup·삭제 보호 유지 상태를 확인한다.
2. 새 Digest와 기존 Runtime Secret을 보존한 Runtime ON Saved Plan을 검토·승인·Apply한다.
3. ECS Service 8개와 Container Instance가 정상인 뒤 Bootstrap Task를 같은 Cluster·Private App Subnet·ECS Security Group에서 실행한다.
4. `RunTask` Container Override에는 호출한 STS ARN과 승인 추적용 Request ID만 넣는다. 이 두 값은 비밀이 아니지만 감사 Log와 CloudTrail에서 추적 가능해야 한다.
5. Task 종료를 기다려 Essential Container Exit Code가 0이고 Log가 `result=created`인지 확인한다.
6. 동일 Secret Version과 새 Request ID로 한 번 더 실행해 Exit Code 0, `result=already_present`인지 확인한다.
7. DB 검증 Task 또는 제한된 SQL로 활성 `ROLE_ADMIN` 사용자가 정확히 1명이고 같은 사용자가 `ROLE_USER`도 갖는지만 확인한다. 식별자, Hash와 Secret Value는 출력하지 않는다.

실패 시 Public 가입 Route를 임시로 열지 않는다. Bootstrap Transaction은 Commit 전에 실패하면 전체 Rollback되므로 Task Log, Exit Code, RDS 연결과 schema를 교정한 뒤 같은 입력으로 재시도한다.

## 5. HTTPS·OAuth·Session·404 Smoke

Admin 정적 화면에서 가입 탭이 보이지 않는지 확인한 뒤 Bootstrap 계정으로 Admin OAuth2 로그인, `ADMINSESSIONID`, CSRF, `/admin-bff/auth/me`, 사용자 목록과 Logout을 검증한다. Cookie·Password·Token은 캡처나 문서에 남기지 않는다.

공개 가입 경로는 유효한 개인정보를 보내지 않고 CSRF Cookie를 먼저 발급받은 다음 빈 JSON으로 404만 검증한다.

```powershell
$cookieJar = Join-Path $env:TEMP "spring-msa-admin-bootstrap-cookies.txt"

try {
  curl.exe --fail --silent --show-error `
    --cookie-jar $cookieJar `
    "https://admin.hyuncloudlab.com/admin-bff/auth/me" | Out-Null

  $csrfLine = Get-Content -LiteralPath $cookieJar |
    Where-Object { $_ -match "ADMIN-XSRF-TOKEN" } |
    Select-Object -Last 1
  $csrfToken = ($csrfLine -split "`t")[-1]
  if ([string]::IsNullOrWhiteSpace($csrfToken)) {
    throw "Admin CSRF cookie was not issued."
  }

  $status = curl.exe --silent --show-error `
    --output NUL `
    --write-out "%{http_code}" `
    --cookie $cookieJar `
    --header "X-ADMIN-XSRF-TOKEN: $csrfToken" `
    --header "Content-Type: application/json" `
    --request POST `
    --data "{}" `
    "https://admin.hyuncloudlab.com/admin-bff/registration/admin"

  if ($status -ne "404") {
    throw "Public admin registration must return 404; actual=$status"
  }
}
finally {
  Remove-Item -LiteralPath $cookieJar -Force -ErrorAction SilentlyContinue
  Remove-Variable csrfLine, csrfToken, status -ErrorAction SilentlyContinue
}
```

404 응답은 `RESOURCE_NOT_FOUND` 공통 Envelope여야 한다. 201, 400 또는 Validation Error면 Controller가 활성화된 것이므로 즉시 실패로 처리하고 Runtime을 내린다. 403은 CSRF Cookie/Header 전달을 먼저 교정한 뒤 재검사한다.

## 6. Runtime OFF와 임시 리소스 제거

1. Smoke 완료 후 Runtime OFF Saved Plan을 승인·Apply하고 ECS/ASG/ALB/Valkey와 Runtime Alarm을 종료한다.
2. RDS가 `stopped`인지 확인하고 다음 자동 시작 시각을 기록한다.
3. `enable_admin_bootstrap_foundation=false` Cleanup Saved Plan으로 임시 Task Definition, Execution Role·Policy와 Secret을 제거한다.
4. Secret은 7일 Recovery Window 삭제 예약 상태여야 한다. 복구하지 않는다.
5. 7일 감사 Log Group은 유지하고 Secret Value가 Log에 없는지 확인한다.
6. 같은 OFF 입력 재계획이 `No changes`인지 확인하고 적용한 Saved Plan 파일을 삭제한다.

Bootstrap 계정 자체는 Cleanup Plan으로 삭제하지 않는다. 잘못된 계정을 만들었다면 DB 직접 삭제나 공개 재가입으로 우회하지 말고 별도 데이터 교정 승인과 감사 절차를 사용한다.
