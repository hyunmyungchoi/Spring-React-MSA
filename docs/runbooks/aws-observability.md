# AWS 관측성 Foundation 운영

이 문서는 Learning AWS 환경의 비용이 적은 상시 관측성 기준선과 적용 절차를 정의한다. Kubernetes의 Prometheus·Grafana·Loki 운영과는 별도이며, AWS Runtime이 OFF여도 유지되는 RDS·비용 알림에 초점을 둔다.

## 현재 기준선

- Backend 8개, Database Bootstrap 1개, Flyway 3개의 CloudWatch Log Group 12개를 7일 보존한다.
- 월 USD 50 AWS Budget 1개와 실제 비용 USD 10, 30, 40, 50 알림을 Terraform으로 관리한다.
- Runtime은 OFF이고 RDS는 정지 상태다.
- ECS Container Insights, RDS Enhanced Monitoring, Performance Insights는 비용을 고려해 비활성이다.

## 추가할 영속 계약

`enable_observability_foundation = true`일 때 다음 7개 Terraform 리소스를 추가한다.

- 표준 SNS Operations Topic 1개
- SNS Topic Policy 1개
- Email Subscription 1개
- RDS CloudWatch Metric Alarm 3개
- RDS Event Subscription 1개

SNS Policy는 같은 계정의 지정 RDS 인스턴스와 이름 접두사가 일치하는 CloudWatch Alarm만 발행할 수 있게 제한한다. 실제 Email 주소는 Git에 넣지 않고 기존의 추적 제외 `terraform.tfvars`에만 둔다.

| Alarm | Metric/통계 | 조건 | 판정 |
| --- | --- | --- | --- |
| CPU 높음 | `CPUUtilization` Average | 80% 이상 | 5분 × 3/3 |
| 가용 메모리 부족 | `FreeableMemory` Minimum | 256 MiB 이하 | 5분 × 3/3 |
| 가용 스토리지 부족 | `FreeStorageSpace` Minimum | 5 GiB 이하 | 5분 × 3/3 |

RDS 정지 중에는 지표가 없으므로 세 Alarm 모두 `treat_missing_data = notBreaching`을 사용한다. RDS Event Subscription은 `availability`, `backup`, `failure`, `low storage`, `maintenance`, `notification` 범주를 구독하며, 최대 정지 기간 경과 뒤 자동 시작 같은 운영 이벤트도 수신 대상이다.

## 적용 전 검증

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

terraform fmt -check -recursive
terraform validate -no-color
terraform test -no-color
```

추적 제외 `terraform.tfvars`에는 다음 값을 둔다. Email 실제 값은 출력하거나 문서에 기록하지 않는다.

```hcl
enable_observability_foundation = true
```

Runtime OFF와 RDS 정지를 유지한 상태로 저장 Plan을 만든다.

```powershell
terraform plan -out=tfplan-observability-foundation-off
terraform show -no-color tfplan-observability-foundation-off
```

승인 전 확인 조건은 다음과 같다.

- 예상 추가 7개, 변경 0개, 삭제 0개
- Runtime `learning_runtime_enabled = false`
- NAT, ALB, Valkey, ECS Task, EC2 생성 없음
- RDS 시작·교체·삭제 없음
- 추가 대상이 SNS Topic/Policy/Email Subscription, RDS Alarm 3개, RDS Event Subscription으로 한정됨
- Plan 경로, 크기, SHA-256을 승인 문구와 함께 고정

## 첫 Apply 실패와 복구 결과

2026-07-20에 승인된 첫 Plan `tfplan-observability-foundation-off`, SHA-256 `9d0be211791325fcbf32ac1df2762cc66bab0bb970dc7f62fe094340ad507613`을 적용했다. SNS Operations Topic 1개는 생성됐지만 Topic Policy의 `SNS:*` wildcard가 SNS 리소스 정책 범위를 벗어나 AWS `InvalidParameter`로 거부됐다. Apply는 이 지점에서 중단됐고 나머지 6개는 생성되지 않았다.

교정 내용은 다음과 같다.

- 계정 관리자용 wildcard Topic Policy 문장을 제거했다.
- RDS Event와 CloudWatch Alarm 서비스 Principal에 `sns:Publish`만 허용했다.
- 정책의 모든 Action이 `sns:Publish`인지 검사하는 Terraform 계약 테스트를 추가했다.
- 추적 제외 운영 `terraform.tfvars`와 무관하게 전체 테스트가 격리되도록 비관측성 루트 테스트 입력을 보강했다.
- Terraform 정적 검증과 전체 mock 테스트 `26 passed, 0 failed`를 다시 확인했다.

첫 Plan은 부분 적용 state와 정책 코드가 달라졌으므로 폐기하며 다시 적용하지 않는다. 검증한 Runtime OFF 복구 Plan은 다음과 같다.

- 경로: `C:\Portfolio\infra\aws\terraform\tfplan-observability-foundation-off-policy-recovery`
- 크기: 190,225 bytes
- SHA-256: `fb8beee57f39b463268d11b0341953dc3340216c7c182076067ab21ae5546de8`
- 요약: `6 to add, 0 to change, 0 to destroy`
- 추가 유형: CloudWatch Metric Alarm 3, RDS Event Subscription 1, SNS Topic Policy 1, Email Subscription 1
- 정책 Action: `sns:Publish` 2개 문장만 사용
- Runtime 계약: ECS Service 8개 Desired 0, ASG `0/0/0`, ALB 0, Valkey 0
- Apply 전 실상태: Operations Topic 1, Subscription 0, RDS Alarm 0, RDS Event Subscription 0
- Apply 전 Runtime 실상태: ECS Task·Container Instance 0, ALB·Valkey 0, RDS `stopped`

2026-07-21에 승인된 SHA를 다시 대조한 뒤 복구 Plan을 적용했다. 최초 시도는 AWS 로그인 만료로 AWS 변경 전에 중단됐으며, 브라우저 재인증 후 같은 Plan을 적용했다.

- 적용 결과: `6 added, 0 changed, 0 destroyed`
- Terraform 관측성 state: 7개
- Topic Policy: `sns:Publish` 2개 문장, Principal은 CloudWatch와 RDS Event Service로 한정, Source ARN·Account 조건 유지
- Email Subscription: 1개, `Confirmed`
- CloudWatch RDS Alarm: 3개, 모두 `OK`
- RDS Event Subscription: `active`, Event Category 6개
- Runtime: ECS Service 8개 Desired/Running 0, Task·Container Instance 0, ASG `0/0/0`, ALB·Valkey 0
- RDS: `stopped`, 자동 재시작 예정 `2026-07-26 22:20:34 KST`
- 동일 Runtime OFF 입력 재계획: `No changes`
- SNS Email 전달 검증: Subscription 1개 `Confirmed`, `PendingConfirmation` 0개
- SNS 직접 발행 Smoke: AWS 발행 접수와 Gmail 실수신 성공, 최종 전달 지표 `Delivered 3`, `Failed 0`

적용한 Saved Plan은 재사용하지 않는다. 2026-07-21에 Email 확인과 직접 발행 실알림 검증까지 완료했으므로 관측성 Foundation 단계는 완료 상태다.

## 적용 직후 확인

Email Subscription은 Apply만으로 활성화되지 않는다. 받은 편지함에서 AWS SNS 확인 링크를 열어 상태를 `Confirmed`로 바꾼다. 확인 전에는 Alarm과 RDS Event가 Email로 전달되지 않는다.

```powershell
$region = "ap-northeast-2"
$prefix = "spring-react-msa-learning"

aws sns list-subscriptions-by-topic `
  --region $region `
  --topic-arn (terraform output -raw operations_sns_topic_arn)

aws cloudwatch describe-alarms `
  --region $region `
  --alarm-name-prefix "$prefix-rds-"

aws rds describe-event-subscriptions `
  --region $region `
  --subscription-name "$prefix-rds-events"
```

확인할 값은 Email Subscription ARN이 `PendingConfirmation`이 아닌지, RDS Alarm 3개의 Action이 활성인지, Event Subscription 상태가 `active`인지다. RDS가 정지 중이면 Alarm은 `INSUFFICIENT_DATA`일 수 있으며 정상이다.

2026-07-21 검증에서는 SNS 확인 메일이 Gmail 스팸함으로 분류됐다. 스팸함에서 구독을 확인한 뒤 AWS 실상태가 Subscription 1개 `Confirmed`, Pending 0개인지 재검사했다. 이어 제목 `[TEST] Spring-React-MSA AWS operations alert`로 Topic에 직접 발행했고 AWS 발행 접수, CloudWatch SNS 전달 3건·실패 0건, Gmail 실제 수신을 모두 확인했다. 확인 메일이 보이지 않으면 재구독을 반복하기 전에 `in:anywhere from:no-reply@sns.amazonaws.com`으로 전체 메일과 스팸함을 먼저 검색한다.

## Runtime 수명주기 관측 2A

`enable_runtime_observability = true`는 Runtime 관측 계약을 켜지만 `learning_runtime_enabled = false`인 동안에는 Container Insights와 Runtime Alarm을 만들지 않는다. Runtime ON Plan에서만 ECS Cluster의 일반 Container Insights를 `enabled`로 바꾸고 29개 Alarm을 만들며, OFF Plan에서는 Alarm을 삭제하고 Container Insights를 다시 `disabled`로 돌린다. Container Insights Enhanced 모드는 사용하지 않는다.

서비스 CPU·Memory는 기본 `AWS/ECS` 지표를 사용한다. `RunningTaskCount`는 `ECS/ContainerInsights` 지표이므로 일반 Container Insights를 Runtime ON 동안만 사용한다. AWS는 기본 ECS CPU·Memory 지표를 추가 비용 없이 제공하고 Container Insights 지표와 CloudWatch Alarm은 사용 시간에 비례해 과금한다. 실제 서울 리전 비용은 Plan 승인 전 [CloudWatch Pricing](https://aws.amazon.com/cloudwatch/pricing/)에서 다시 확인한다.

| 대상 | Alarm 수 | 조건 | Missing data |
| --- | ---: | --- | --- |
| ECS CPU | 서비스별 1개, 총 8개 | 80% 이상, 5분 주기 3/3 | `notBreaching` |
| ECS Memory | 서비스별 1개, 총 8개 | 85% 이상, 5분 주기 3/3 | `notBreaching` |
| ECS Running Task | 서비스별 1개, 총 8개 | 1개 미만, 1분 주기 3/5 | `breaching` |
| ALB 자체 5xx | 1개 | 5건 이상, 5분 주기 2/2 | `notBreaching` |
| Gateway Target 5xx | Target Group별 1개, 총 2개 | 5건 이상, 5분 주기 2/2 | `notBreaching` |
| Gateway Unhealthy Host | Target Group별 1개, 총 2개 | 1개 이상, 1분 주기 2/3 | `notBreaching` |

모든 Alarm은 기존 Operations SNS Topic에 `ALARM`과 `OK`를 모두 전달한다. Task Count는 시작 직후 지표가 아직 없을 때 경보가 날 수 있으므로 ECS Service 8개의 배포 안정화 뒤 모두 `OK`인지 확인한다.

Runtime OFF에서 기능 계약만 고정하는 Plan은 다음처럼 만든다.

```powershell
Set-Location C:\Portfolio\infra\aws\terraform

$env:TF_VAR_application_images = <현재 적용된 Task Definition 8개의 ECR digest JSON>
$env:TF_VAR_toss_api_client_id = <현재 적용된 Stock Client ID>

terraform plan `
  -var="enable_runtime_observability=true" `
  -var="enable_application_runtime_foundation=true" `
  -var="enable_frontend_hosting=true" `
  -var="enable_public_domain_routing=true" `
  -out=tfplan-observability-runtime-lifecycle-off

terraform show -no-color tfplan-observability-runtime-lifecycle-off
```

`terraform.tfvars`는 root mock 테스트 격리를 위해 Application·Frontend·Public Domain Flag와 Application Image Map을 상시 `true` 또는 실제 값으로 저장하지 않는다. 따라서 모든 운영 Plan은 위 네 Flag와 현재 적용된 이미지 8개, Stock Client ID를 명시적으로 보존해야 한다. 하나라도 빠져 Foundation 삭제나 Task Definition 교체가 나타나면 Plan을 적용하지 않고 폐기한다.

이 Plan의 기대 결과는 `learning_runtime_enabled=false`, Container Insights `disabled`, Runtime Alarm 0개이며 기존 Foundation을 삭제하거나 Runtime 유료 리소스를 시작하지 않는 것이다. Runtime ON 검증은 [AWS Application Runtime](aws-application-runtime.md)의 RDS 시작, 승인된 ON Saved Plan, curl Smoke 절차를 그대로 사용한다. ON Plan에는 일반 Container Insights 활성화와 Runtime Alarm 29개가 포함돼야 하고, OFF Plan에는 29개 삭제와 Container Insights 비활성화가 포함돼야 한다.

2026-07-21 적용 기록:

- 첫 사전 Plan은 기존 Application·Frontend·Public Domain Flag 누락으로 `2 add, 100 destroy` 후보가 나타났고 새 변수 검증에서 중단됐다. 적용하지 않았고 임시 파일을 삭제했다.
- 두 번째 사전 Plan은 Application Image Map 누락으로 중단됐으며, Windows 줄바꿈 때문에 ECS Launch Template `user_data`가 불필요하게 변경되는 문제를 발견했다. `join("\n", ...)`으로 LF를 고정하고 Source SHA `ed33b8323f8706c6dbd322404c55cbe9bfbd8582`에 반영했다.
- 세 번째 사전 Plan은 적용된 Stock Client ID 누락으로 Stock Task Definition 교체가 잡혀 폐기했다.
- 최종 Plan은 현재 적용된 ECR digest 8개와 Stock Client ID를 메모리 변수로 보존해 AWS 리소스 변경 0개, 출력 2개 추가로 수렴했다.
- Saved Plan 크기: `195,925 bytes`
- Saved Plan SHA-256: `90a5be1ca3f71e07e689be1b77045c6e2a8bd66995945748053c43c6a13fbba5`
- 적용 결과: `0 added, 0 changed, 0 destroyed`
- 적용 후 실상태: Container Insights `disabled`, Runtime Alarm 0, ECS Service 8개 Desired/Running/Pending 합계 `0/0/0`, ASG `0/0/0`, Instance·ALB·Valkey 0, RDS `stopped`
- 동일 Foundation·Image·Stock 입력 재계획: `No changes`

```powershell
aws ecs describe-clusters `
  --region ap-northeast-2 `
  --clusters (terraform output -raw ecs_cluster_name) `
  --include SETTINGS

aws cloudwatch describe-alarms `
  --region ap-northeast-2 `
  --alarm-name-prefix "spring-react-msa-learning-ecs-"

aws cloudwatch describe-alarms `
  --region ap-northeast-2 `
  --alarm-name-prefix "spring-react-msa-learning-alb-"
```

## 알림 대응

1. 알림의 Account, Region, Alarm 또는 RDS Event ID를 확인한다.
2. RDS 자동 시작 알림이면 [AWS Learning RDS 운영·복구](aws-rds-learning.md)에 따라 사용 목적을 확인하고 불필요하면 정상 정지 절차를 수행한다.
3. CPU·메모리 Alarm은 Runtime ON 시각, 연결 수, 느린 Query와 애플리케이션 오류 로그를 함께 확인한다.
4. Storage Alarm은 급하게 축소하거나 삭제하지 말고 증가 원인과 백업 상태를 확인한 뒤 증설 Plan을 별도 승인받는다.
5. 조치 뒤 Alarm이 `OK`로 돌아오고 Email 복구 알림이 도착하는지 확인한다.

## 이번 단계에서 보류하는 항목

- 자동 Runtime ON/OFF 스케줄
- RDS 최대 정지 기간의 자동 시작 전 사전 감시
- 최초 관리자 Bootstrap

자동 스케줄이 Terraform 소유 ECS·ALB·Valkey 상태를 직접 바꾸면 state drift가 생길 수 있다. 2B에서는 먼저 변경 없는 알림 전용 Watchdog으로 Runtime 장시간 ON과 RDS 자동 시작 임박을 감시하고, 자동 OFF는 별도 승인된 Terraform 실행 경로가 생긴 뒤 검토한다. 애플리케이션 HTTPS·OAuth·Session·WebSocket 검증은 [AWS Application Runtime](aws-application-runtime.md)의 curl Smoke 계약을 계속 사용한다.
