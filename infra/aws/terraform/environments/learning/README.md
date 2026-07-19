# Learning Environment

이 디렉터리는 `spring-react-msa`의 단일 AWS 학습 환경을 설명한다. 실제 Terraform 실행은 상위 `infra/aws/terraform` 디렉터리에서 수행한다.

- Region: `ap-northeast-2`
- VPC: `10.20.0.0/16`
- Availability Zones: 계정에서 사용 가능한 첫 2개 AZ
- State: S3 Remote Backend와 S3 Native Lockfile
- Outbound: Private App 단일 NAT·고정 EIP·S3 Gateway Endpoint 적용, Private Data 외부 경로 없음
- Data Layer: PostgreSQL 16.14 `db.t4g.micro` Single-AZ RDS와 Secret Container 7개 적용, DB Secret 3개 초기화 및 Role·Schema Bootstrap·Runtime ON 검증 완료, 현재 RDS `stopped`
- ECS Compute: Cluster·Launch Template·ASG·Capacity Provider 적용·Runtime ON 배치 검증 완료, `m6i.xlarge`, 현재 ASG `0/0/0`, EC2 0과 `awsvpcTrunking=enabled`
- Application Runtime: Digest 고정 Task Definition·ECS Service·Cloud Map 8개, Gateway Target Group 2개와 최소 권한 IAM/Log 유지; Runtime ON Health·Digest·Cloud Map 8/8와 curl 6/6 검증 후 현재 Service 8개 `0/0/0`, Public ALB·Valkey 삭제
- Frontend Hosting: 독립 Private S3 6개, Member/Admin CloudFront 2개와 배포 IAM Apply·AWS 계약·`No changes` 검증 완료; Bucket 6개는 비어 있고 첫 배포 대기
- Budget: 실제 알림 이메일을 입력한 경우에만 활성화

`dev`, `staging`, `prod` 환경은 이번 Foundation 범위에 포함하지 않는다.
