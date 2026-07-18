# AWS Learning Image Build Once·Promote Runbook

## 목적

백엔드 애플리케이션 이미지를 GHCR에서 서비스·Source SHA당 한 번만 빌드하고, 같은 OCI Manifest와 Layer를 재빌드 없이 Amazon ECR로 복사한다. Kubernetes와 AWS ECS가 Registry 주소는 달라도 동일한 최상위 OCI Digest를 사용하도록 만드는 절차다.

이번 Database Migration 단계의 대상은 다음 세 서비스다.

- `spring-user-service`
- `spring-member-bff-service`
- `spring-member-stock-service`

## 안전 경계

- GHCR Workflow가 Test와 최초 Build를 담당한다.
- ECR Workflow는 Docker build를 실행하지 않고 `crane copy`만 사용한다.
- ECR 접근은 장기 Access Key가 아니라 GitHub OIDC 단기 자격 증명을 사용한다.
- `source_sha`는 40자 전체 소문자 Git SHA이며 `origin/master`에서 도달할 수 있어야 한다.
- 기존 ECR SHA Tag가 같은 Digest면 Skip하고, 다른 Digest면 무결성 오류로 실패한다.
- GHCR과 ECR의 최상위 Digest가 완전히 같아야 성공한다.
- `latest`는 편의용 GHCR Tag일 뿐 Kubernetes나 ECS 배포 기준으로 사용하지 않는다.

## 1. 로컬 검증

```powershell
cd C:\Portfolio
python -m unittest `
  infra/ci/test_select_build_matrix.py `
  infra/ci/test_update_k8s_image_tags.py `
  infra/ci/test_registry_image_integrity.py `
  infra/ci/test_ghcr_build_push_workflow.py `
  infra/ci/test_ecr_build_push_workflow.py

cd C:\Portfolio\infra\aws\terraform
terraform fmt -check -recursive
terraform validate
terraform test
```

GitHub Actions 전용 Linter로 `.github/workflows`도 검사한다. Workflow 변경을 기본 Branch에 반영하기 전에는 원격 수동 실행으로 넘어가지 않는다.

## 2. GHCR 최초 Build

Workflow 변경이 검토·승인되어 `master`에 반영된 뒤 Actions에서 `Build and Push Images to GHCR`을 실행한다.

```text
deploy_target = database-migrations
ref           = master
```

Workflow는 세 서비스마다 `${source_sha}` GHCR Tag를 먼저 조회한다.

- Tag가 없으면 Test 후 한 번만 Build하고 SHA Tag를 Push한다.
- Tag가 있으면 다시 Build하지 않는다.
- Build 결과 Digest와 GHCR에서 다시 조회한 Digest가 같아야 성공한다.
- `latest`는 같은 Digest를 가리키도록 Registry 내에서 다시 Tag한다.
- Kubernetes Manifest는 `ghcr.io/...@sha256:...` 형식으로 갱신한다.

완료 후 Workflow가 사용한 40자 `source_sha`를 기록한다. GitHub Actions Run ID와 Digest도 운영 기록에 남기되 Secret이나 Registry Token은 남기지 않는다.

## 3. ECR Promote

Actions에서 `Promote Backend Images from GHCR to Amazon ECR`을 수동 실행한다.

```text
source_sha    = <2단계의 전체 40자 Git SHA>
deploy_target = database-migrations
ref           = master
```

각 Matrix Job은 다음 순서로 동작한다.

1. `source_sha`가 `origin/master`의 Commit인지 검사한다.
2. GHCR SHA Tag의 최상위 OCI Digest를 조회한다.
3. GitHub OIDC로 ECR 전용 IAM Role을 Assume한다.
4. ECR에 같은 SHA Tag가 있는지 조회한다.
5. 없으면 GHCR Digest Reference를 `crane copy`로 ECR에 복사한다.
6. 있으면 Digest가 같은 경우만 Skip한다.
7. 복사 후 GHCR과 ECR Digest를 다시 비교한다.

공식 `crane copy`는 원격 Image를 Digest를 유지한 채 복사하는 용도다. Dockerfile과 Source를 다시 빌드하지 않는다.

## 4. Terraform Migration Image Map 생성

Promote가 성공한 뒤 로그인된 로컬 터미널에서 읽기 전용 Helper를 실행한다.

```powershell
cd C:\Portfolio
$expectedAccountId = "<승인된 12자리 AWS Account ID>"
.\infra\aws\scripts\Get-LearningMigrationImageMap.ps1 `
  -SourceSha <전체 40자 Git SHA> `
  -ExpectedAccountId $expectedAccountId
```

출력은 다음 형태이며 실제 Repository URL과 Digest만 화면에 표시한다.

```hcl
database_migration_images = {
  user-service  = "<ECR user-service URL>@sha256:<digest>"
  member-bff    = "<ECR member-bff URL>@sha256:<digest>"
  stock-service = "<ECR stock-service URL>@sha256:<digest>"
}
```

세 Key가 모두 없으면 Terraform은 Migration Task Definition을 만들지 않는다. 일부 Key만 입력하는 것도 검증 오류로 거부한다.

## 5. 다음 승인 Gate

위 Map을 Git에서 제외된 `terraform.tfvars`에 반영한 뒤 새 저장 Plan을 만든다. 예상 변경은 세 Flyway Migration Task Definition, 서비스별 최소 권한 IAM과 Log Group이며 Runtime 용량 변경이나 RDS 시작은 포함하지 않아야 한다.

Plan의 Add/Change/Destroy, IAM 범위, Image Digest와 SHA-256을 검토하고 정확한 저장 Plan에 대한 별도 승인을 받은 뒤에만 Apply한다. Apply 후에도 Flyway Task 실행, Runtime ON과 RDS 시작은 각각 운영 승인 단위로 처리한다.

## 실패 처리

- GHCR SHA Tag 조회 실패: 권한 오류를 “미존재”로 취급하지 않는다. Login과 Package 권한을 고친다.
- 기존 ECR SHA Tag의 Digest 불일치: 덮어쓰거나 삭제하지 않고 무결성 사고로 중단한다. 새로운 Source Commit으로 다시 Build한다.
- Registry Copy 실패: 일부 ECR Image가 생성됐을 수 있다. 같은 `source_sha`로 재실행하면 같은 Digest만 Skip하고 미완료 대상만 다시 시도한다.
- Digest 비교 실패: 해당 Image를 ECS나 Flyway Task에 입력하지 않는다.
