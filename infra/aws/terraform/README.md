# AWS Foundation Terraform

이 디렉터리는 `spring-react-msa` 학습 환경의 AWS Foundation만 정의한다. 기본 리전은 `ap-northeast-2`이며 실제 워크로드를 배포하지 않는다.

## 범위

생성 대상:

- VPC 1개 (`10.20.0.0/16`)
- Public Subnet 2개
- Private App Subnet 2개
- Private Data Subnet 2개
- Internet Gateway 1개
- Public/App/Data Route Table 각 1개
- ALB/ECS Application/Data Security Group 각 1개와 명시적 규칙
- 알림 이메일을 입력하고 활성화한 경우 월 USD 50 AWS Budget 1개

이번 단계에서 생성하지 않는 대상:

- NAT Gateway, Elastic IP, VPC Endpoint
- ECS Cluster, EC2 Auto Scaling Group, Capacity Provider, ECR
- ALB, Target Group, Listener, ACM, Route 53 Record
- RDS, ElastiCache, MSK
- S3 Frontend Bucket, CloudFront
- Secrets Manager, GitHub OIDC, AWS 배포 Workflow

## 사전 요구사항

- Terraform `1.15.x`
- HashiCorp AWS Provider `6.x`
- AWS CLI v2
- `ap-northeast-2`에서 VPC, EC2 네트워크, Budgets 조회·생성 권한을 가진 AWS 호출 주체
- Budget을 활성화할 경우 실제 알림 이메일

Terraform과 Provider 선택은 `versions.tf`와 `.terraform.lock.hcl`로 고정한다. Lock 파일은 Provider의 재현 가능한 설치와 공급망 검증을 위해 커밋하고, `.terraform/` 디렉터리는 커밋하지 않는다.

## AWS CLI 인증

장기 Access Key를 Terraform 파일에 직접 넣지 않는다. 개인 학습 환경에서는 AWS IAM Identity Center(SDK/CLI SSO) 또는 제한된 IAM 사용자의 CLI Profile을 사용하고, 이후 GitHub 배포에는 OIDC를 사용한다.

PowerShell 예시:

```powershell
aws configure sso
aws sso login --profile <profile-name>
$env:AWS_PROFILE = "<profile-name>"
$env:AWS_REGION = "ap-northeast-2"
aws sts get-caller-identity
```

Access Key가 필요한 예외 상황에서도 키를 코드, `terraform.tfvars`, README, Git 기록에 넣지 않는다. `aws sts get-caller-identity`의 Account ID는 외부 보고 시 마스킹한다.

## 초기화와 검증

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

Copy-Item terraform.tfvars.example terraform.tfvars

terraform init
terraform fmt -check -recursive
terraform validate
terraform test
```

`terraform.tfvars`는 Git에서 제외된다. 예시 파일은 Budget을 기본 비활성화하므로, 실제 Apply 전에 다음 두 값을 로컬 `terraform.tfvars`에서 설정해야 한다.

```hcl
enable_budget      = true
budget_alert_email = "<your-budget-alert-email>"
```

Budget 이메일은 Terraform State에도 기록될 수 있으므로 State를 민감 데이터로 취급한다.

## Terraform Plan

AWS 인증과 실제 Budget 이메일을 준비한 후 실행한다.

```powershell
terraform plan
```

Plan에서 다음을 확인한다.

- Add/Change/Destroy 수
- NAT Gateway, EIP, ECS, EC2, ALB, RDS, ElastiCache가 없는지
- Private App/Data Route Table에 `0.0.0.0/0` 경로가 없는지
- Public Subnet의 자동 Public IPv4 할당이 비활성화됐는지
- Budget 알림 기준이 USD 10, 30, 40, 50인지
- 삭제 또는 교체되는 기존 리소스가 없는지

Plan 파일을 저장해야 한다면 `tfplan` 이름을 사용한다. `tfplan`과 `tfplan.*`은 Git에서 제외되며 공유하지 않는다.

## Apply 전 확인

Apply는 다음 조건을 모두 충족한 뒤 사용자 명시적 승인으로만 실행한다.

1. AWS 계정과 리전이 의도한 대상이다.
2. Budget 이메일이 실제 수신 가능한 주소다.
3. `fmt`, `validate`, `test`, `plan`이 성공했다.
4. Plan에 예상한 Foundation 리소스만 있다.
5. 예상 비용과 NAT 미생성 영향을 이해했다.

승인 후에만 실행:

```powershell
terraform apply
```

이 저장소 작업에서는 `terraform apply`를 자동 실행하지 않는다.

## Destroy

학습 종료 후 먼저 삭제 Plan을 검토한다.

```powershell
terraform plan -destroy
terraform destroy
```

VPC 삭제는 연결된 ENI, ALB, NAT Gateway, ECS, RDS 같은 후속 리소스가 남아 있으면 실패한다. 다음 단계의 리소스를 추가한 뒤에는 의존 관계와 데이터 백업을 확인하고 역순으로 삭제한다.

## 네트워크와 송신 제한

- Public Route Table만 Internet Gateway 기본 경로를 가진다.
- Public Subnet도 자동 Public IPv4를 부여하지 않는다.
- Private App/Data Route Table에는 VPC 로컬 경로만 있다.
- NAT Gateway가 없으므로 Private Subnet에서 ECR 이미지 Pull, OS 패치, 외부 API 호출을 할 수 없다.
- Stock Service의 Toss API 호출은 다음 ECS 단계에서 송신 경로를 추가해야 한다.

다음 단계에서 비교할 선택지:

- 단일 NAT Gateway: 저렴하지만 AZ 장애 경계가 넓어진다.
- AZ별 NAT Gateway: 가용성은 높지만 고정비가 두 배가 된다.
- Gateway/Interface VPC Endpoint 조합: AWS 서비스 트래픽을 NAT에서 분리할 수 있지만 Endpoint별 시간당 비용이 발생할 수 있다.

## Security Group 요약

| 출발지 | 목적지 | 포트 | 용도 |
|---|---|---|---|
| Internet | ALB SG | 80, 443 | 미래의 공개 진입점 |
| ALB SG | ECS SG | 8080, 8090 | Member/Admin Gateway |
| ECS SG | ECS SG | 8079, 8087, 9000, 8081, 8083, 8084 | 내부 서비스 호출 |
| ECS SG | Data SG | 5432, 6379, 9092 | PostgreSQL, Redis, 미래 Kafka |

애플리케이션, 데이터, Kafka, SSH 포트는 인터넷에서 직접 접근할 수 없다. SG 응답 트래픽은 상태 기반으로 허용되므로 별도의 임시 고포트 수신 규칙이 필요하지 않다.

## 예상 비용

이번 Foundation 단계의 VPC, Subnet, Route Table, Security Group에는 별도 시간당 리소스 요금이 없고 Internet Gateway 자체도 무료다. Public IPv4, NAT Gateway, ALB, 컴퓨팅, 데이터베이스, 로그 수집을 생성하지 않으므로 Apply 후 예상 고정비는 사실상 USD 0이다. 데이터 전송이나 기존 Route 53 Hosted Zone·도메인 비용은 별도다.

- 일반 AWS Budget 모니터링과 이메일 알림: 무료
- 향후 Public IPv4: 주소당 USD 0.005/시간
- 미생성 NAT Gateway: 서울 리전 기준 약 USD 0.059/시간, 월 730시간 기준 약 USD 43.07/개와 데이터 처리비 절감

가격은 변경될 수 있으므로 Apply 직전 [Amazon VPC 요금](https://aws.amazon.com/ko/vpc/pricing/)과 [AWS Budgets 요금](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/)을 다시 확인한다.

## Local State 주의사항

- `*.tfstate`와 `*.tfstate.*`는 Git에서 제외한다.
- State에는 리소스 식별자와 Budget 이메일 같은 민감 정보가 포함될 수 있다.
- State를 잃으면 Terraform 관리 관계가 사라지므로 Apply 전에 안전한 로컬 백업 책임을 정한다.
- 팀 운영이나 자동 배포 전에는 S3 Backend, State 잠금, 암호화, 최소 권한을 별도 설계한다.

## 다음 단계

1. NAT Gateway와 VPC Endpoint 비용·가용성 비교
2. ECR와 ECS on EC2 기반 Cluster/ASG/Capacity Provider 설계
3. ALB, ACM, Route 53 구성
4. RDS/ElastiCache와 DB 마이그레이션·백업·복구 설계
5. CloudWatch Logs/Metrics/Alarms와 SRE Runbook 작성
