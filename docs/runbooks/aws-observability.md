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

2026-07-24 기준 위 표는 현재 AWS 적용 계약이다. Hikari `5/1` 재측정 뒤 `db.t4g.micro`를 유지하면서 FreeableMemory 임계값을 128 MiB로 조정하고 SwapUsage 64 MiB·DatabaseConnections 16 Alarm을 추가해 영속 RDS Alarm을 5개로 만드는 후속 결정을 완료했다. 아직 Terraform과 AWS에는 적용하지 않았으므로 현재 3개를 목표 5개로 오인하지 않는다. 상세 구현·Saved Plan Gate는 [RDS Alarm·Member BFF Prometheus 교정 계획](../plans/2026-07-24-rds-alarm-prometheus-plan.md)을 따른다.

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

2026-07-22 Runtime ON 검증 기록:

- Source SHA `d2dc46b062be1deef7c0c4a55ff8a87a4c914579`에서 Saved Plan `tfplan-observability-runtime-lifecycle-on`, 202,653 bytes, SHA-256 `46c96d2b993afc8afe8c354ad93cf2c949cd0cee5d4973e5659629af3384ba45`를 만들었다.
- Plan 범위는 `40 create, 10 update, 0 destroy`다. Runtime 리소스 11개와 Alarm 29개를 만들고 ECS Service 8개, ASG와 ECS Cluster를 갱신했다. Task Definition·Image·RDS·Frontend·DNS Foundation 변경은 없었다.
- 승인된 Plan을 적용한 결과 `40 added, 10 changed, 0 destroyed`였다. RDS·Valkey는 `available`, ASG는 `1/1/2`, ECS Service·Task·Container Health는 8/8, ALB Target은 2/2 `healthy`, 일반 Container Insights는 `enabled`, Runtime Alarm 29개는 모두 `OK`로 수렴했다.
- 공개 HTTPS 정적·Readiness·OIDC·BFF curl 12/12 HTTP 200과 Root 308 Path·Query 보존을 확인했다. 새 무작위 ROLE_USER 1개로 Registration 201, Password Login·OAuth Authorization Code·인증 Session·CSRF Heartbeat·User API·Logout을 검증했고 응답에 원본 `sessionId`가 없었다.
- 같은 인증 Cookie로 공개 WebSocket에서 `CONNECTED`, `HISTORY`, `PONG`, 자체 `CHAT_MESSAGE`를 수신하고 REST History 영속성까지 확인했다. 임시 비밀번호와 Cookie 파일은 폐기했으며 이 계정은 후속 관리자 정리 대상에 추가한다.
- 첫 Alarm `ALARM → OK` 테스트는 SNS Action과 Topic Publish 2건이 성공했지만 Email Subscription 실상태가 Terraform 재계획의 `No changes`와 달리 `Deleted`여서 실제 전달되지 않았다.
- 강제 교체 Saved Plan `tfplan-observability-email-subscription-recovery`, 223,714 bytes, SHA-256 `383b514ea0cba9eeac4572c5a909b493489e434cb722b5203203aaeff7eb930d`는 `module.observability[0].aws_sns_topic_subscription.email` 한 개만 `delete+create`했다. 승인 적용 결과는 `1 added, 0 changed, 1 destroyed`다.
- 새 구독 확인 뒤 Email Subscription 1개 `Confirmed`, Runtime Alarm `ALARM → OK`, SNS Action 2회 성공, `Published 2`, `Delivered 2`, `Failed 0`을 확인했다.
- 동일 Runtime ON·Foundation·Image·Stock·Redis 입력 재계획은 `No changes`이며 Alarm 29개는 모두 `OK`다. 적용된 두 Saved Plan은 Hash 검증 후 삭제했다.
- 비용 종료 Saved Plan `tfplan-observability-runtime-lifecycle-off-after-smoke`, 218,898 bytes는 Source SHA `d4f923bedbf8375ae6ff9badbf7ab24c05c591d4`에서 만들었다. 승인된 SHA-256 `f44f5f7a22be792fdf17a1d0b5e7761ae57bd8ec12168c54a4b1c773698d89fd`를 재검증한 뒤 `0 added, 10 changed, 40 destroyed`로 적용했다.
- 변경 10개는 ECS Service 8개 `desired_count 1 → 0`, ASG `min/max 1/2 → 0/0`, 일반 Container Insights `enabled → disabled`다. 삭제 40개는 Runtime Alarm 29개, Public ALB·HTTPS Listener·Gateway Rule 2개·`origin` A Alias 5개, Valkey·Redis Host Parameter 6개다. 교체와 예상 밖 변경은 없었다.
- RDS·Task Definition·Image digest 8개·Frontend Bucket 6개·CloudFront 2개·SNS Topic과 확인된 Email Subscription·RDS Alarm 3개는 변경하지 않았다. Apply 뒤 ECS Service Desired/Running/Pending `0/0/0`, Task·Container Instance 0, ASG `0/0/0`, Runtime Alarm·ALB·Valkey·`origin` 0을 확인했다.
- 애플리케이션 종료 뒤 RDS를 별도로 정지해 `stopped`, Enhanced Monitoring `0`, Performance Insights `false`, Backup Retention 7일, 삭제 보호 `true`를 확인했다. 자동 재시작 예정은 2026-07-29 02:33:31 KST다.
- 정적 curl은 6/6 HTTP 200, Root는 308, Runtime OFF API는 502였고 동일 OFF 입력 재계획은 `No changes`였다. SNS Email Subscription 1개는 `Confirmed` 상태를 유지하며 적용된 Saved Plan은 Hash 재검증 후 삭제했다. 이로써 2A Runtime 관측성 수명주기 검증은 완료됐고 다음 단계는 2B 알림 전용 Watchdog이다.

2026-07-23 Post-Restore Full Smoke 관측 기록:

- 승인된 Runtime ON Plan을 `40 added, 10 changed, 0 destroyed`로 적용하고 Container Insights `enabled`, Runtime Alarm 29개 생성을 확인했다. ECS 8개 수렴 뒤 29/29가 `OK`였다.
- `spring-react-msa-learning-alb-load-balancer-5xx`를 합성 `OK → ALARM → OK`로 전환했다. Operations SNS는 `Published 2`, 확인된 Email 구독은 `Delivered 2`, `Failed 0`이었다.
- Watchdog Alarm 3개는 모두 `OK`다.
- 별도의 실제 RDS `FreeableMemoryLow` Alarm은 `ALARM`이다. 256MiB 임계값에 대한 `2026-07-23 17:44 KST` 최신 5분 Minimum은 153.2MiB였다. 합성 상태 전환으로 덮어쓰거나 임계값을 낮추지 않았으며, 최종 Runtime OFF 뒤 다음 ON 전에 DB Class·연결 수·메모리 설정과 Alarm 정책을 별도 검토한다.

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

## 2B 알림 전용 Runtime Watchdog

2B는 Terraform 소유 Runtime 리소스를 직접 변경하지 않는다. `enable_runtime_watchdog=true`일 때 다음 영속 감시 리소스만 만든다.

- Python 3.12 ARM64 Lambda 1개와 7일 CloudWatch Log Group. 계정 동시성 한도 10에서 AWS가 최소 미예약 동시성 10을 요구하므로 예약 동시성을 따로 설정하지 않고 계정 미예약 풀을 사용한다.
- `rate(15 minutes)` EventBridge Rule·Target·Lambda Permission
- 중복 알림 방지 상태 3개만 저장하는 DynamoDB `PAY_PER_REQUEST` Table 1개. 삭제 보호와 서버 측 암호화를 사용한다.
- `Heartbeat` Custom Metric과 Watchdog Heartbeat 누락·Lambda Error·EventBridge Failed Invocation Alarm 3개

Lambda가 판정하는 상태는 다음과 같다.

| 상태 | 기본 임계값 | 알림 |
| --- | --- | --- |
| Runtime 장시간 ON | 가장 오래 실행 중인 ECS EC2가 6시간 이상 | `runtime-on-too-long` |
| RDS 자동 재시작 임박 | `AutomaticRestartTime`까지 24시간 이하 | `rds-auto-restart-imminent` |
| Runtime OFF 중 RDS 실행 | ASG·ECS Desired가 0인데 RDS가 `stopped/stopping` 외 상태 | `rds-running-while-runtime-off` |

최초 정상 상태는 DynamoDB에만 기록하고 Email을 보내지 않는다. 이후 `inactive → active`는 ALERT, `active → inactive`는 RECOVERY를 한 번만 발행한다. SNS 발행이 실패하면 상태를 확정하지 않아 다음 실행에서 재시도한다. Heartbeat는 모든 점검과 상태 동기화가 성공한 뒤에만 기록한다.

Watchdog IAM의 Runtime 조회 권한은 `autoscaling:DescribeAutoScalingGroups`, `ec2:DescribeInstances`, `ecs:DescribeServices`, `rds:DescribeDBInstances`뿐이다. 별도로 자기 DynamoDB Item 읽기·쓰기, 기존 SNS Topic 발행, 지정 Custom Metric 기록, 지정 Log Group 쓰기만 허용한다. `rds:StartDBInstance`, `rds:StopDBInstance`, `ecs:UpdateService`, ASG 용량 변경과 ElastiCache 변경 권한은 없다.

코드 검증 기준은 Python 단위 테스트 `7 passed`, Terraform 전체 mock 테스트 `30 passed, 0 failed`, `terraform validate`다. Lambda Archive는 HashiCorp Archive Provider `2.8.0`으로 만들며 생성 ZIP은 Git에서 제외한다. Lambda·DynamoDB 요청량은 작지만 CloudWatch Custom Metric·Alarm·Log 등 상시 소액 과금 가능성이 있으므로 AWS 적용은 Saved Plan Hash를 별도 승인받는다.

적용 Plan은 Runtime OFF 상태에서 다음 입력을 모두 보존해 만든다.

```powershell
terraform plan `
  -var="enable_runtime_observability=true" `
  -var="enable_runtime_watchdog=true" `
  -var="enable_application_runtime_foundation=true" `
  -var="enable_frontend_hosting=true" `
  -var="enable_public_domain_routing=true" `
  -var="learning_runtime_enabled=false" `
  -out=tfplan-runtime-watchdog-off
```

현재 적용된 Application Image digest 8개와 Stock Client ID도 기존 Runtime Plan과 동일하게 환경 변수로 전달한다. Plan에 ECS Desired·ASG Capacity·ALB·Valkey·RDS·Task Definition·Image·Frontend·DNS·SNS Subscription 변경이 포함되면 적용하지 않는다.

Apply 뒤 Lambda를 한 번 직접 호출해 정상 OFF 상태에서 DynamoDB 상태 3개 `inactive`, Heartbeat 수신, Function Error 0과 SNS Email Subscription `Confirmed`를 확인한다. Watchdog Alarm은 평가가 수렴한 뒤 3개 모두 `OK`여야 한다. ALERT/RECOVERY 판정과 SNS 실패 재시도는 실제 Runtime을 켜지 않고 단위 테스트로 검증한다.

### 2026-07-23 적용·Smoke 기록

Source SHA `71616fea6647e3faa98b6af7a5d1a3837e6c273d`의 최초 Runtime OFF Plan SHA-256 `b1c71772b203ae9fd671af58940dc9a0ace49c978a60ca33856225ad51121d9b`는 `11 create, 0 update, 0 destroy`였으나 Lambda 생성 뒤 `reserved concurrency=1` 설정에서 중단됐다. 계정 동시성 한도가 10이고 AWS가 미예약 동시성 최소 10을 요구해 예약 1을 둘 수 없었던 것이 원인이다. Runtime·RDS 변경은 없었으며 Lambda는 정상 `Active`로 생성되고 Terraform state에 추적됐다.

예약 동시성을 제거하고 mock 계약에서 `null`을 확인하도록 교정한 Source SHA는 `2f9566bfee08504153a2775b11ac554ceffe53cc`다. `terraform validate`와 전체 mock 테스트 `30 passed, 0 failed`를 통과했고, 실패 흔적으로 남은 Lambda taint는 실제 AWS 구성 일치 확인 뒤 해제했다. Recovery Saved Plan은 213,517 bytes, SHA-256 `1ae5c62eced0fe89b718a8a005f555d3c2287c5fd792959abb5b6394d8dfcf35`이며 EventBridge Target과 Lambda Permission만 `2 create, 0 update, 0 destroy`로 포함했다. 승인 적용 결과도 `2 added, 0 changed, 0 destroyed`였고 두 Saved Plan은 재사용 방지를 위해 삭제했다.

Baseline Invoke는 HTTP 상태 200, Function Error 없음, Runtime inactive, ASG·ECS Desired·EC2 Instance 모두 0, RDS `stopped`, 세 이슈 false, 상태 전환 0이었다. DynamoDB Item 3개는 모두 inactive이고 Heartbeat `1.0`을 수신했다. 생성 직후 첫 30분 평가창 누락으로 Heartbeat Alarm이 ALARM이었던 상태는 검증된 Heartbeat를 근거로 승인된 합성 `OK` 복구를 수행했으며 CloudWatch의 SNS Action 성공, SNS `Delivered 1`을 확인했다. 최종 Watchdog Alarm 3개는 모두 `OK`, EventBridge는 `rate(15 minutes)`·Target 1개, SNS Email Subscription은 `Confirmed` 1개다. 동일 OFF 입력 재계획은 `No changes`이고 ASG `0/0/0`, ECS 8개 Desired/Running 합계 `0/0`, RDS `stopped`를 유지한다.

## 이번 단계에서 보류하는 항목

- 자동 Runtime ON/OFF 스케줄
- 최초 관리자 Bootstrap

자동 스케줄이 Terraform 소유 ECS·ALB·Valkey 상태를 직접 바꾸면 state drift가 생길 수 있다. 2B는 변경 없는 알림 전용 Watchdog으로 제한하고, 자동 OFF는 별도 승인된 Terraform 실행 경로가 생긴 뒤 검토한다. 애플리케이션 HTTPS·OAuth·Session·WebSocket 검증은 [AWS Application Runtime](aws-application-runtime.md)의 curl Smoke 계약을 계속 사용한다.
