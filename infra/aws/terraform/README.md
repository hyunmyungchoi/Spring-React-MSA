# AWS Terraform 운영 Runbook

이 디렉터리는 `spring-react-msa` 학습 환경의 현재 AWS 인프라를 관리한다. 기본 리전은 `ap-northeast-2`다. Foundation 기준선, Backend ECR/GitHub OIDC, Private App 송신, RDS/Secrets Data Layer, ECS Compute와 Database Bootstrap Task Foundation까지 Apply했고 실제 RDS에서 DB Role·Schema Bootstrap도 검증했다. RDS는 비용 통제를 위해 정지했고 ECS ASG는 `0/0/0`으로 유지한다. Flyway Migration 및 ECS Application Task/Service 같은 실제 Workload는 아직 배포하지 않는다. 후속 Runtime의 승인된 목표와 미구현 경계는 [AWS Learning Runtime 결정](../../../docs/aws-migration/07-learning-runtime-design.md)을 따른다.

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

짧은 Runtime ON에서 Revision 2 Bootstrap Task가 Exit Code `0`으로 완료됐다. 별도 읽기 전용 검증 Task도 Exit Code `0`이었으며 안전한 Role 3개, Schema 3개, 자기 Schema 권한 조합 3개, 교차 Schema 권한 0개, Application Table 0개를 실제 RDS에서 확인했다. 이는 Bootstrap과 네트워크·Security Group 경계가 동작했다는 뜻이며 Flyway는 아직 실행하지 않았으므로 Application Table이 0개인 것이 정상이다.

검증 후 승인된 OFF Plan을 Apply해 ASG를 `min=0`, `desired=0`, `max=0`으로 내리고 RDS를 정지했다. 현재 ECS Container Instance·실행/대기 Task·Service는 모두 0개, RDS는 `stopped`, Bootstrap Task Definition Revision 2는 `ACTIVE`, Remote State는 95개 주소이며 재계획 결과는 `No changes`다. AWS가 표시한 RDS 자동 재시작 예정 시각은 2026-07-25 19:54:58 KST이므로 그 전에 상태와 Budget을 다시 확인한다.

### 현재 AWS에 생성하지 않은 대상

- Flyway Migration ECS Task Definition과 Migration Task 실행 이력
- ECS Application Task Definition, Service와 실행 중인 Container/EC2 Instance
- ALB, Target Group, Listener, ACM, Route 53 Record
- ElastiCache, MSK
- S3 Frontend Bucket, CloudFront, Frontend ECR Repository
- 자동 배포

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

Database Task Foundation의 현재 입력은 다음과 같다.

```hcl
enable_database_tasks_foundation = true
database_migration_images        = {}
```

첫 값으로 DB Role/Schema Bootstrap Task Definition과 최소 권한 IAM/7일 Log Group을 적용했고, Secret 초기화와 실제 RDS Bootstrap 검증까지 완료했다. 두 번째 Map은 현재 Source가 포함된 불변 ECR Digest를 확보하기 전까지 비워 두므로 Flyway Migration Task Definition은 생성하지 않는다. 실제 Secret Version 생성과 Task 실행은 Terraform Apply와 별도의 승인 단계이며 [DB Bootstrap·Flyway Runbook](../../../docs/runbooks/aws-database-bootstrap-and-flyway.md)을 따른다.

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
| Internet | ALB SG | 80, 443 | 미래의 공개 진입점 |
| ALB SG | ECS SG | 8080, 8090 | Member/Admin Gateway |
| ECS SG | ECS SG | 8079, 8087, 9000, 8081, 8083, 8084 | 내부 서비스 호출 |
| ECS SG | Data SG | 5432, 6379, 9092 | PostgreSQL, Redis, 미래 Kafka |
| ECS SG | AWS Public API/외부 서비스 | 443 | ECR, CloudWatch, Secrets Manager, Toss API |

Application, Data, Kafka, SSH Port는 Internet에서 직접 접근할 수 없다. SG 응답 Traffic은 상태 기반으로 허용되므로 별도의 임시 고포트 수신 규칙이 필요하지 않다.

## 예상 비용

적용된 Foundation의 VPC, Subnet, Route Table, Security Group에는 별도 시간당 리소스 요금이 없고 Internet Gateway 자체도 무료다. Public IPv4와 NAT Gateway 요금은 현재 발생하며 ALB는 아직 생성하지 않았다. ECS Compute Foundation은 적용했지만 ASG가 0대라 실행 요금이 없다. 아래 Data Layer와 ECS 금액은 세전 목록가 추정치이며 Credit과 Free Tier를 반영하지 않는다.

- 일반 AWS Budget Monitoring과 Email 알림: 무료
- ECR Apply 이후: 주로 저장된 Image 용량, Data Transfer, Scan 방식에 따라 비용 발생
- 현재 계획의 Push 시 Basic Scan: Account 전체 Enhanced Scan을 활성화하지 않음
- 현재 Public IPv4 1개: USD 0.005/시간, 월 730시간 기준 약 USD 3.65
- 현재 NAT Gateway 1개: 서울 리전 기준 USD 0.059/시간, 월 730시간 기준 약 USD 43.07와 GB당 USD 0.059 처리비
- NAT와 EIP 상시 ON 고정비: 약 USD 46.72/월, Data 처리비와 전송비 별도
- RDS `db.t4g.micro` 실행: USD 0.025/시간, 730시간 기준 약 USD 18.25/월
- RDS `gp3` 20 GiB: USD 0.131/GB-월, 약 USD 2.62/월
- Secret 8개: USD 0.40/Secret-월, 약 USD 3.20/월과 API 호출 비용
- Data Layer 상시 실행 고정비: 약 USD 24.07/월
- 현재 NAT/EIP와 Data Layer를 모두 상시 실행: 약 USD 70.79/월로 USD 50 Budget 초과
- RDS 정지 중에도 Storage와 Secret 약 USD 5.82/월은 남는다. 현재 NAT/EIP를 계속 켜면 합계 약 USD 52.54/월이므로 Budget을 지키려면 사용하지 않을 때 NAT도 OFF해야 한다.
- ECS Compute Foundation을 `0/0/0`으로 Apply한 상태: ECS Cluster, Launch Template, ASG, IAM과 Capacity Provider 자체에는 추가 시간당 요금 없음
- ECS Runtime ON 1대: 서울 리전 `m6i.xlarge` USD 0.236/시간과 30 GiB `gp3` 약 USD 2.736/월, 730시간 기준 합계 약 USD 175.02/월 추가
- Capacity Provider가 최대 2대로 확장한 경우: 같은 기준 약 USD 350.03/월 추가
- ASG가 0대로 돌아가면 Instance와 `DeleteOnTermination` EBS Volume도 삭제되어 ECS Compute 실행비는 멈추지만, 기존 NAT/EIP·RDS Storage·Secrets 비용은 별도로 남음

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

1. 구현·로컬 검증을 마친 Build Once Workflow를 `master`에 반영하고 현재 Flyway Source Image 3개를 동일 OCI Digest로 ECR에 Promote
2. 세 Flyway Migration Task Definition을 저장 Plan으로 적용하고 User Service, Member BFF, Stock Service 순서로 실행
3. Backend 8개의 Application Task Definition, ECS Service, Service Discovery와 ALB 구현
4. Frontend S3/CloudFront·Route 53/ACM 구현
5. 관측성, Backup Restore 훈련과 Runtime 자동화

Kubernetes↔AWS DR은 Learning 적용 범위에서 제외하고 후속 학습 과제로 보류한다.
