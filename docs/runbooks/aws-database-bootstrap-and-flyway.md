# AWS Learning DB Bootstrap 및 Flyway 실행 Runbook

## 목적

Learning 환경의 PostgreSQL RDS에 서비스별 DB 사용자와 Schema를 만든 뒤, 같은 애플리케이션 이미지에 포함된 Flyway SQL을 일회성 ECS Task로 실행한다.

- `user_service_app`은 `user_service` Schema에만 `USAGE`, `CREATE` 권한을 가진다.
- `member_bff_app`은 `member_bff` Schema에만 `USAGE`, `CREATE` 권한을 가진다.
- `stock_service_app`은 `stock_service` Schema에만 `USAGE`, `CREATE` 권한을 가진다.
- Schema 자체는 Bootstrap 관리 계정이 유지하고 서비스가 생성한 Table은 해당 서비스 Role이 소유한다.
- RDS Master 계정은 Bootstrap에만 사용하며 애플리케이션과 Flyway Task에는 제공하지 않는다.
- Community Service는 영속성 기능이 없으므로 이번 단계에서 사용자나 Schema를 만들지 않는다.

## 안전 경계

다음 세 작업은 서로 다른 승인 단위다.

1. Terraform Apply: Task Definition, 최소 권한 IAM Role, CloudWatch Log Group을 생성한다.
2. Secret 초기화: 기존의 빈 Secret Container 세 곳에 최초 DB 사용자명과 무작위 비밀번호 Version을 기록한다.
3. Runtime 실행: RDS를 시작하고 ECS ASG를 ON으로 전환한 뒤 Bootstrap/Flyway Task를 실행한다.

Terraform에는 실제 비밀번호를 넣지 않는다. Secret ARN과 JSON Key 참조만 Terraform State에 기록된다. Secret 초기화 스크립트도 비밀번호를 화면에 출력하지 않으며, 이미 정상 Version이 존재하면 덮어쓰지 않는다.

## 1. 정적 검증

```powershell
cd C:\Portfolio\infra\aws\terraform
terraform fmt -check -recursive
terraform validate
terraform test
```

세 백엔드의 Testcontainers Migration Test는 Docker Desktop이 실행 중일 때 별도로 수행한다.

```powershell
cd C:\Portfolio\BackEnd\spring-user-service
.\gradlew.bat test --tests "*UserServiceMigrationTest" --no-daemon

cd C:\Portfolio\BackEnd\spring-member-bff-service
.\gradlew.bat test --tests "*MemberBffMigrationTest" --no-daemon

cd C:\Portfolio\BackEnd\spring-member-stock-service
.\gradlew.bat test --tests "*StockServiceMigrationTest" --no-daemon
```

## 2. Database Task Foundation 적용

> 적용 완료: 검토한 저장 Plan을 SHA-256 승인 후 Apply해 `4 added, 0 changed, 0 destroyed`를 확인했다. 최초 Task Definition Revision 1은 RDS 호환성 수정 후 Revision 2로 교체했으며 현재 Revision 2가 `ACTIVE`다.

최초 Bootstrap Foundation Apply 당시 `terraform.tfvars`는 다음 상태를 사용했다.

```hcl
enable_database_tasks_foundation = true
database_migration_images        = {}
learning_runtime_enabled         = false
```

첫 Apply에서는 DB Bootstrap Task만 등록했다. 당시에는 Flyway Image Digest가 없었으므로 Migration Task를 생성하지 않았고 RDS와 ECS 실행 용량도 OFF로 유지했다. 이후 6~7절의 Build Once·Promote와 별도 저장 Plan으로 Migration Task 3개를 추가했다.

저장 Plan을 만든 뒤 변경 개수, 교체·삭제 0개, Plan 파일 SHA-256을 검토하고 명시적으로 승인된 Plan만 Apply한다.

## 3. DB Secret 최초 초기화

> 실행 완료: SHA-256으로 승인한 스크립트가 세 Secret의 최초 `AWSCURRENT` Version을 생성했다. 승인된 사용자명, 40자 무작위 비밀번호와 정확한 JSON Key를 값 노출 없이 확인했고 재실행 시 세 Secret을 모두 Skip했다.

Terraform Apply와 별도로 승인받은 뒤 다음 스크립트를 실행한다.

```powershell
cd C:\Portfolio
$expectedAccountId = "<승인된 12자리 AWS Account ID>"
.\infra\aws\scripts\Initialize-LearningDatabaseSecrets.ps1 -ExpectedAccountId $expectedAccountId -WhatIf
.\infra\aws\scripts\Initialize-LearningDatabaseSecrets.ps1 -ExpectedAccountId $expectedAccountId
```

생성되는 JSON Contract는 다음과 같지만 실제 값은 표시하거나 Git에 저장하지 않는다.

```json
{
  "db_username": "서비스별 고정 사용자명",
  "db_password": "AWS가 생성한 40자 무작위 값"
}
```

## 4. 짧은 Runtime ON

DB 작업을 실제로 실행할 때만 새로운 저장 Plan으로 `learning_runtime_enabled = true`를 승인·적용한다. 이 설정은 ECS ASG를 `min=1`, `desired=1`, `max=2`로 만든다. Terraform과 별도로 정지된 RDS를 시작하고 다음 상태를 기다린다.

- RDS: `available`
- ECS Cluster: Container Instance 1개 이상 `ACTIVE`
- ASG: InService Instance 1개

RDS 시작·정지는 Terraform 리소스 생성/삭제가 아니라 운영 상태 전환이므로 Runbook 작업으로 관리한다.

> 실행 기록: Bootstrap 검증을 위해 RDS를 시작하고 Runtime ON Plan을 적용했다. ECS Container Instance 1개가 `ACTIVE`, RDS가 `available`인 상태에서 일회성 Task를 실행했으며 검증 후 다시 OFF로 전환했다.

## 5. DB Role 및 Schema Bootstrap

Private App Subnet, ECS Security Group, Public IP 없음으로 Task를 실행한다.

```powershell
cd C:\Portfolio
$taskFamily = terraform -chdir=infra/aws/terraform output -raw database_bootstrap_task_family
.\infra\aws\scripts\Invoke-LearningDatabaseTask.ps1 -TaskDefinition $taskFamily -ExpectedAccountId $expectedAccountId
```

성공 기준은 ECS Container Exit Code `0`과 CloudWatch Log의 다음 완료 메시지다.

```text
Database bootstrap completed for 3 service schemas.
```

로그에는 사용자명과 Schema 이름만 남고 비밀번호는 남지 않아야 한다. Bootstrap은 Role이 없으면 생성하고, 있으면 Secret의 현재 비밀번호로 동기화하므로 재실행할 수 있다.

> 실행 완료: 최초 Revision 1은 RDS 관리 계정이 일반 PostgreSQL SUPERUSER와 다르다는 제한 때문에 Exit Code `3`으로 실패했다. `NOSUPERUSER` 변경과 서비스 Role로의 Schema 소유권 이전을 제거하고, Schema는 Bootstrap 관리 계정이 소유하되 서비스 Role에는 자기 Schema의 `USAGE`, `CREATE`만 주는 Revision 2로 수정했다. Revision 2 Bootstrap은 Exit Code `0`으로 완료됐다.

실제 DB 권한은 다음 읽기 전용 스크립트로 확인한다.

```powershell
cd C:\Portfolio
.\infra\aws\scripts\Test-LearningDatabaseBootstrap.ps1 -ExpectedAccountId $expectedAccountId
```

검증 실행 결과는 안전한 Role `3`, Schema `3`, 자기 Schema 권한 조합 `3`, 교차 Schema 권한 `0`, Application Table `0`이었다. Application Table은 다음 Flyway 단계 전이므로 0개가 정상이다.

## 6. Flyway 이미지 Build Once 및 Promote

현재 최종 Source SHA로 세 애플리케이션 이미지를 GHCR에서 한 번만 빌드하고, 최상위 OCI Digest 기준으로 ECR에 Promote한다.

- `spring-user-service`
- `spring-member-bff-service`
- `spring-member-stock-service`

GHCR과 ECR Digest가 완전히 같은지 검증한 뒤 ECR의 `repository_url@sha256:...` 값을 `database_migration_images`에 넣는다. `latest`나 변경 가능한 SHA Tag만으로 Task Definition을 만들지 않는다.

> 실행 완료: Source SHA `f0c88e32b883c391dcf993dfbf40839312de0f39`에서 GHCR Actions Run `29642831008`로 세 Image를 최초 Build했고, ECR Actions Run `29643089643`으로 재빌드 없이 Promote했다. 세 서비스 모두 GHCR·ECR 최상위 OCI Digest 일치를 확인했다.

## 7. Flyway Task 등록 및 실행

새 저장 Plan을 만들어 세 Migration Task Definition과 서비스별 최소 권한 Execution Role/Log Group을 적용한다. 각 Task는 애플리케이션 Task와 동일한 ECR 이미지 Digest를 사용하지만 전체 Spring Context 대신 전용 `FlywayMigrationMain`만 실행한다.

등록 후 다음 순서로 각각 실행한다.

1. User Service
2. Member BFF
3. Stock Service

```powershell
$tasks = terraform -chdir=infra/aws/terraform output -json database_migration_task_definition_arns | ConvertFrom-Json
.\infra\aws\scripts\Invoke-LearningDatabaseTask.ps1 -TaskDefinition $tasks.'user-service' -ExpectedAccountId $expectedAccountId
.\infra\aws\scripts\Invoke-LearningDatabaseTask.ps1 -TaskDefinition $tasks.'member-bff' -ExpectedAccountId $expectedAccountId
.\infra\aws\scripts\Invoke-LearningDatabaseTask.ps1 -TaskDefinition $tasks.'stock-service' -ExpectedAccountId $expectedAccountId
```

각 Task의 Exit Code가 하나라도 `0`이 아니면 애플리케이션 배포로 넘어가지 않는다. 세 Task가 성공하면 읽기 전용 검증 Task를 실행한다.

```powershell
.\infra\aws\scripts\Test-LearningFlywayMigrations.ps1 -ExpectedAccountId $expectedAccountId
```

검증은 서비스별 `flyway_schema_history` 3개와 V1 성공 이력 3개, 예상 Application Table 5개, Table 소유자, 교차 Schema 권한 0개, Seed Data 0건을 확인한다. 이 검증까지 통과해야 Migration 완료로 처리한다.

> 실행 완료: 승인한 저장 Plan으로 Migration Log Group·최소 권한 Execution Role/Policy·Digest 고정 Task Definition을 서비스별 3개씩 총 12개 추가했다. Runtime ON과 RDS `available` 확인 후 User Service, Member BFF, Stock Service 순서로 실행해 모두 Exit Code `0`을 확인했다. `Test-LearningFlywayMigrations.ps1` 검증도 위의 모든 기대값으로 Exit Code `0`을 반환했다.

## 8. Runtime OFF

검증이 끝나면 다음 순서를 지킨다.

1. 실행 중인 일회성 Task가 없는지 확인한다.
2. 새 저장 Plan으로 `learning_runtime_enabled = false`를 승인·적용해 ECS ASG를 `0/0/0`으로 내린다.
3. RDS를 정지한다.
4. ECS Instance 0개, RDS `stopped`, Terraform 재계획 `No changes`를 확인한다.

RDS는 최대 7일 후 AWS가 자동 재시작할 수 있으므로 `AutomaticRestartTime`과 Budget 알림을 계속 확인한다.

> 실행 완료: Flyway 검증 후 승인된 OFF Plan Apply 결과는 `0 added, 1 changed, 0 destroyed`다. ASG `min/desired/max=0/0/0`, EC2·ECS Container Instance·실행/대기 Task·Service 0개, RDS `stopped`, Terraform `No changes`를 확인했다. 현재 AWS가 표시한 `AutomaticRestartTime`은 2026-07-25 22:34:06 KST이다.

## 실패 및 복구

- Secret 초기화 실패: 기존 정상 Secret Version은 덮어쓰지 않는다. 오류 원인을 수정한 후 다시 실행한다.
- Bootstrap 실패: CloudWatch Log를 확인하고 Role/Schema 상태를 점검한 뒤 같은 Task를 재실행한다.
- Flyway 실패: 실패한 Version SQL을 이미 적용된 환경에서 수정하지 않는다. 새 Version의 순방향 보정 Migration을 만든다.
- 파괴적 Migration 복구: 필요하면 RDS Automated Backup/PITR로 별도 Instance에 복원하고 데이터 확인 후 전환한다.
- Task 실행 중 EC2/RDS를 먼저 OFF하지 않는다.
