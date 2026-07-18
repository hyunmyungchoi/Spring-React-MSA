# Terraform State Backend Bootstrap

이 Stack은 `spring-react-msa` Learning 환경의 기존 Local State를 S3 Remote Backend로 이전하기 전에 필요한 관리 리소스만 만든다. Main Terraform과 분리되어 있으며 이 디렉터리의 작은 Bootstrap State는 Local로 유지한다.

## 생성 대상

- 계정 ID와 Region을 포함한 S3 State Bucket 1개
- S3 Versioning과 SSE-S3 `AES256`
- Object Ownership `BucketOwnerEnforced`
- Block Public Access 4개 설정 전체 활성화
- HTTP 요청을 거부하는 Bucket Policy
- `learning/runtime/terraform.tfstate`와 `.tflock`에만 접근하는 IAM Role
- `hyun-terraform-admin`이 해당 Role만 Assume할 수 있는 Inline Policy

DynamoDB Lock Table과 KMS Key는 만들지 않는다. Terraform S3 Native Lockfile인 `use_lockfile = true`를 사용한다.

## 현재 상태

2026-07-18에 검토한 저장 Plan으로 관리 리소스 9개를 AWS에 Apply했다. S3 Versioning, SSE-S3 `AES256`, Public Access 차단, `BucketOwnerEnforced`, HTTPS-only Policy와 전용 IAM Role을 검증했다. 이어 Main State 66개 주소를 S3로 이전하고 Native Lockfile 생성·해제와 Terraform 재계획 `No changes`를 확인했다.

## 검증과 Plan

```powershell
Set-Location C:\Portfolio\infra\aws\bootstrap\terraform-state
terraform init -backend=false
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan '-out=tfplan.backend'
terraform show -no-color tfplan.backend
```

Plan은 다음 9개 추가만 포함해야 한다.

1. S3 Bucket
2. Bucket Ownership Controls
3. Bucket Public Access Block
4. Bucket Versioning
5. Bucket SSE-S3 Encryption
6. HTTPS-only Bucket Policy
7. State Access IAM Role
8. Role Inline Policy
9. Operator IAM User의 AssumeRole Inline Policy

변경 또는 삭제가 있거나 다른 AWS 리소스가 나타나면 Apply하지 않는다.

## 적용 기록과 State Migration

Bootstrap Apply에는 검토한 Plan 파일만 사용했으며, 아래 명령은 동일한 절차를 재현하거나 복구할 때 사용한다.

```powershell
terraform apply tfplan.backend
terraform output state_bucket_name
terraform output state_access_role_arn
```

Main Terraform 이전에는 다음 순서를 사용했으며 복구 또는 재구성 시에도 동일하게 검증한다.

1. Git에서 관리하는 `backend.tf`의 S3 Backend 선언을 확인한다.
2. `backend.s3.hcl.example`을 Git에서 제외되는 `backend.s3.hcl`로 복사한다.
3. Bootstrap Output의 Bucket 이름과 Role ARN을 `backend.s3.hcl`에 기록한다.
4. 기존 Local State를 별도 안전 위치에 백업한다.
5. `terraform init -migrate-state -force-copy -input=false -backend-config=backend.s3.hcl`을 실행한다.
6. 이전 전후 `terraform state list`가 동일한지 확인한다.
7. `terraform plan` 결과가 `No changes`인지 확인한다.
8. S3 Version 목록과 Lockfile 생성·해제를 검증한다.

`backend.s3.hcl`에는 Access Key와 Secret Key를 기록하지 않는다. AWS CLI의 현재 IAM 인증을 사용해 전용 State Role을 Assume한다.

## Bootstrap State 주의사항

Backend Bucket은 자기 자신을 저장하는 Remote Backend에 의존할 수 없으므로 Bootstrap State는 이 디렉터리에 Local로 남는다. `terraform.tfstate`는 Git에서 제외하고 암호화된 개인 백업에 보관한다. Bucket을 재구성해야 할 때는 기존 Bucket을 Import해 관리 관계를 복구한다.
