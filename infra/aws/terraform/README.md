# AWS Terraform 운영 Runbook

이 디렉터리는 `spring-react-msa` 학습 환경의 현재 AWS 인프라를 관리한다. 기본 리전은 `ap-northeast-2`다. Foundation 기준선과 Backend ECR/GitHub OIDC 확장은 모두 Apply됐으며, GitHub Repository Variable 연결도 완료됐다. ECS 등의 실제 워크로드는 아직 배포하지 않는다.

## 현재 상태와 범위

### 적용된 Foundation 기준선

현재 Local State에는 다음 네트워크와 Budget 리소스가 적용되어 있다.

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

### 현재 생성하지 않는 대상

- NAT Gateway, Elastic IP, VPC Endpoint
- ECS Cluster, EC2 Auto Scaling Group, Capacity Provider, Task Definition, Service
- ALB, Target Group, Listener, ACM, Route 53 Record
- RDS, ElastiCache, MSK
- S3 Frontend Bucket, CloudFront, Frontend ECR Repository
- Secrets Manager와 자동 배포

## 사전 요구사항

- Terraform `1.15.x`
- HashiCorp AWS Provider `6.x`
- AWS CLI v2
- 기본 리전 `ap-northeast-2`
- 현재 Local State와 `terraform.tfvars`
- Account에 등록된 GitHub OIDC Provider `https://token.actions.githubusercontent.com`

Terraform 실행 주체에는 다음 권한이 필요하다.

- 적용된 Foundation을 Refresh할 VPC/EC2 Network와 Budgets 조회 권한
- ECR Repository, Tag, Scan 설정, Lifecycle Policy를 생성하고 조회할 ECR 권한
- 기존 GitHub OIDC Provider를 조회할 `iam:GetOpenIDConnectProvider` 권한
- 전용 IAM Role을 생성·조회·Tag하고 Inline Policy를 등록·조회할 IAM 권한

예를 들어 ECR에는 `ecr:CreateRepository`, `ecr:DescribeRepositories`, `ecr:ListTagsForResource`, `ecr:PutLifecyclePolicy`, `ecr:GetLifecyclePolicy`에 해당하는 권한이, IAM에는 `iam:CreateRole`, `iam:GetRole`, `iam:TagRole`, `iam:PutRolePolicy`, `iam:GetRolePolicy`에 해당하는 권한이 필요하다. 향후 변경이나 Rollback에서 리소스를 수정·삭제하려면 그 작업에 맞는 추가 권한도 별도 검토한다.

GitHub 연결 단계에는 `hyunmyungchoi/Spring-React-MSA`의 Repository Variable을 설정할 GitHub 권한과 인증된 `gh` CLI가 필요하다. 이 권한은 Terraform Plan 확인이나 Apply에는 필요하지 않는다.

Terraform과 Provider 선택은 `versions.tf`와 `.terraform.lock.hcl`로 고정한다. Lock 파일은 재현 가능한 Provider 설치와 공급망 검증을 위해 커밋하고, `.terraform/` 디렉터리는 커밋하지 않는다.

## AWS CLI 인증

장기 Access Key를 Terraform 파일에 직접 넣지 않는다. 개인 학습 환경에서는 AWS IAM Identity Center(SDK/CLI SSO) 또는 제한된 IAM 사용자의 CLI Profile을 사용하고, GitHub Workflow는 OIDC로 단기 자격 증명을 받는다.

PowerShell 예시:

```powershell
aws configure sso
aws sso login --profile <profile-name>
$env:AWS_PROFILE = "<profile-name>"
$env:AWS_REGION = "ap-northeast-2"
aws sts get-caller-identity
```

Access Key가 필요한 예외 상황에서도 키를 코드, `terraform.tfvars`, README, Git 기록에 넣지 않는다. `aws sts get-caller-identity`의 Account ID는 외부 보고 시 마스킹한다.

## 초기화와 공통 검증

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

terraform init
terraform fmt -check -recursive
terraform validate
terraform test
```

`terraform.tfvars`는 Git에서 제외한다. 현재 적용된 Foundation의 Budget 설정과 알림 이메일이 들어 있으므로 민감 데이터로 취급하고, 검토된 저장 Plan Gate가 끝나기 전에 변경하거나 덮어쓰지 않는다. State에도 리소스 식별자와 Budget 이메일이 기록될 수 있다.

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
- Private App/Data Route Table에는 VPC Local 경로만 있다.
- NAT Gateway가 없으므로 Private Subnet에서 ECR Image Pull, OS Patch, 외부 API 호출을 할 수 없다.
- Stock Service의 Toss API 호출은 다음 ECS 단계에서 송신 경로를 추가해야 한다.

다음 단계에서 비교할 선택지:

- 단일 NAT Gateway: 저렴하지만 AZ 장애 경계가 넓어진다.
- AZ별 NAT Gateway: 가용성은 높지만 고정비가 두 배가 된다.
- Gateway/Interface VPC Endpoint 조합: AWS 서비스 Traffic을 NAT에서 분리할 수 있지만 Endpoint별 시간당 비용이 발생할 수 있다.

## Security Group 요약

| 출발지 | 목적지 | 포트 | 용도 |
|---|---|---|---|
| Internet | ALB SG | 80, 443 | 미래의 공개 진입점 |
| ALB SG | ECS SG | 8080, 8090 | Member/Admin Gateway |
| ECS SG | ECS SG | 8079, 8087, 9000, 8081, 8083, 8084 | 내부 서비스 호출 |
| ECS SG | Data SG | 5432, 6379, 9092 | PostgreSQL, Redis, 미래 Kafka |

Application, Data, Kafka, SSH Port는 Internet에서 직접 접근할 수 없다. SG 응답 Traffic은 상태 기반으로 허용되므로 별도의 임시 고포트 수신 규칙이 필요하지 않다.

## 예상 비용

적용된 Foundation의 VPC, Subnet, Route Table, Security Group에는 별도 시간당 리소스 요금이 없고 Internet Gateway 자체도 무료다. Public IPv4, NAT Gateway, ALB, Compute, Database, Log 수집은 아직 생성하지 않았다.

- 일반 AWS Budget Monitoring과 Email 알림: 무료
- ECR Apply 이후: 주로 저장된 Image 용량, Data Transfer, Scan 방식에 따라 비용 발생
- 현재 계획의 Push 시 Basic Scan: Account 전체 Enhanced Scan을 활성화하지 않음
- 향후 Public IPv4: 주소당 USD 0.005/시간
- 미생성 NAT Gateway: 서울 리전 기준 약 USD 0.059/시간, 월 730시간 기준 약 USD 43.07/개와 Data 처리비 절감

가격은 변경될 수 있으므로 Apply 직전 [Amazon ECR 요금](https://aws.amazon.com/ecr/pricing/), [Amazon VPC 요금](https://aws.amazon.com/ko/vpc/pricing/), [AWS Budgets 요금](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/)을 다시 확인한다.

## Local State 주의사항

- `*.tfstate`와 `*.tfstate.*`는 Git에서 제외한다.
- State에는 리소스 식별자와 Budget Email 같은 민감 정보가 포함될 수 있다.
- State를 잃으면 Terraform 관리 관계가 사라지므로 Apply 전에 안전한 Local Backup 책임을 정한다.
- 팀 운영이나 자동 배포 전에는 S3 Backend, State Lock, Encryption, 최소 권한을 별도 설계한다.

## 다음 단계

1. ECR Basic Scan 결과를 운영 기준에 맞게 검토하고 취약점 허용 기준 정의
2. NAT Gateway와 VPC Endpoint 비용·가용성 비교
3. ECS on EC2 기반 Cluster/ASG/Capacity Provider 설계
4. ALB, ACM, Route 53 구성
5. RDS/ElastiCache와 DB Migration·Backup·복구 설계
7. CloudWatch Logs/Metrics/Alarms와 SRE Runbook 작성
