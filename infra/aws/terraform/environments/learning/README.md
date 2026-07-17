# Learning Environment

이 디렉터리는 `spring-react-msa`의 단일 AWS 학습 환경을 설명한다. 실제 Terraform 실행은 상위 `infra/aws/terraform` 디렉터리에서 수행한다.

- Region: `ap-northeast-2`
- VPC: `10.20.0.0/16`
- Availability Zones: 계정에서 사용 가능한 첫 2개 AZ
- State: 로컬 State
- Outbound: Private Subnet의 인터넷 송신 경로 없음
- Budget: 실제 알림 이메일을 입력한 경우에만 활성화

`dev`, `staging`, `prod` 환경은 이번 Foundation 범위에 포함하지 않는다.
