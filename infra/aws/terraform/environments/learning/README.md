# Learning Environment

이 디렉터리는 `spring-react-msa`의 단일 AWS 학습 환경을 설명한다. 실제 Terraform 실행은 상위 `infra/aws/terraform` 디렉터리에서 수행한다.

- Region: `ap-northeast-2`
- VPC: `10.20.0.0/16`
- Availability Zones: 계정에서 사용 가능한 첫 2개 AZ
- State: S3 Remote Backend와 S3 Native Lockfile
- Outbound: Private App 단일 NAT·고정 EIP·S3 Gateway Endpoint 적용, Private Data 외부 경로 없음
- Data Layer: PostgreSQL 16.14 `db.t4g.micro` Single-AZ RDS와 Secret Container 7개 적용, DB Secret 3개 초기화 및 Role·Schema Bootstrap 검증 완료, RDS는 현재 비용 통제를 위해 정지
- ECS Compute: Cluster·Launch Template·ASG·Capacity Provider 적용·AWS 검증 완료, `m6i.xlarge`, ASG `0/0/0`, EC2 0대
- Budget: 실제 알림 이메일을 입력한 경우에만 활성화

`dev`, `staging`, `prod` 환경은 이번 Foundation 범위에 포함하지 않는다.
