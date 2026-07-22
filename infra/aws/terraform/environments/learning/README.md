# Learning Environment

이 디렉터리는 `spring-react-msa`의 단일 AWS 학습 환경을 설명한다. 실제 Terraform 실행은 상위 `infra/aws/terraform` 디렉터리에서 수행한다.

- Region: `ap-northeast-2`
- VPC: `10.20.0.0/16`
- Availability Zones: 계정에서 사용 가능한 첫 2개 AZ
- State: S3 Remote Backend와 S3 Native Lockfile
- Outbound: Private App 단일 NAT·고정 EIP·S3 Gateway Endpoint 적용, Private Data 외부 경로 없음
- Data Layer: PostgreSQL 16.14 `db.t4g.micro` Single-AZ RDS와 Secret Container 7개 적용, DB Secret 3개 초기화 및 Role·Schema Bootstrap·Runtime ON 검증 완료, 현재 RDS `stopped`; Backup Retention 7일·삭제 보호 유지
- ECS Compute: Cluster·Launch Template·ASG·Capacity Provider 적용·Runtime ON 배치 검증 완료, `m6i.xlarge`, 현재 ASG `0/0/0`, EC2·Container Instance 0, `awsvpcTrunking=enabled`, Container Insights `disabled`
- Application Runtime: Digest 고정 Task Definition·ECS Service·Cloud Map 8개, Gateway Target Group 2개와 최소 권한 IAM/Log 유지; 현재 Service·Task 0, Public ALB·Valkey·`origin`·Runtime Alarm 0이며 동일 OFF 입력 `No changes`
- Frontend Hosting: 독립 Private S3 6개, Member/Admin CloudFront 2개와 배포 IAM Apply·AWS 계약·`No changes` 검증 완료; 첫 전체 배포 6/6과 정적 curl 6/6 HTTP 200
- Runtime Watchdog: 15분 주기 Lambda·DynamoDB 상태 전환·Heartbeat/오류 Alarm 적용 완료, Baseline·Alarm→SNS·`No changes` 검증 완료
- Budget: 실제 알림 이메일을 입력한 경우에만 활성화

`dev`, `staging`, `prod` 환경은 이번 Foundation 범위에 포함하지 않는다.
