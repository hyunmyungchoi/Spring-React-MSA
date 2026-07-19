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

## 현재 검증된 Saved Plan

2026-07-19에 Runtime OFF 상태로 다음 Plan을 생성·감사했다.

- 경로: `C:\Portfolio\infra\aws\terraform\tfplan-observability-foundation-off`
- 크기: 189,381 bytes
- SHA-256: `9d0be211791325fcbf32ac1df2762cc66bab0bb970dc7f62fe094340ad507613`
- 요약: `7 to add, 0 to change, 0 to destroy`
- 추가 유형: CloudWatch Metric Alarm 3, RDS Event Subscription 1, SNS Topic 1, Topic Policy 1, Email Subscription 1
- Runtime 계약: ECS Service 8개 Desired 0, ASG `0/0/0`, ALB 0, Valkey 0
- 실상태: ECS Task·Container Instance 0, ALB·Valkey 0, RDS `stopped`
- 관측성 실상태: Operations Topic 0, 사용자 정의 RDS Alarm 0, RDS Event Subscription 0

RDS 자동 재시작 예정 시각은 확인 당시 `2026-07-26 22:20:34 KST`다. Plan은 아직 Apply하지 않았다. 승인 문구는 다음과 같다.

`Observability Foundation OFF Plan 9d0be211791325fcbf32ac1df2762cc66bab0bb970dc7f62fe094340ad507613 적용 승인`

Plan 내용이나 SHA가 달라지면 기존 승인은 폐기하고 새 Plan을 검토한다. 명시적 승인 전에는 `terraform apply`를 실행하지 않는다.

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

## 알림 대응

1. 알림의 Account, Region, Alarm 또는 RDS Event ID를 확인한다.
2. RDS 자동 시작 알림이면 [AWS Learning RDS 운영·복구](aws-rds-learning.md)에 따라 사용 목적을 확인하고 불필요하면 정상 정지 절차를 수행한다.
3. CPU·메모리 Alarm은 Runtime ON 시각, 연결 수, 느린 Query와 애플리케이션 오류 로그를 함께 확인한다.
4. Storage Alarm은 급하게 축소하거나 삭제하지 말고 증가 원인과 백업 상태를 확인한 뒤 증설 Plan을 별도 승인받는다.
5. 조치 뒤 Alarm이 `OK`로 돌아오고 Email 복구 알림이 도착하는지 확인한다.

## 이번 단계에서 보류하는 항목

- ECS Container Insights
- Backend 8개 서비스별 CPU·메모리·Task Count Alarm
- Runtime ON일 때만 존재하는 ALB의 5xx·Target Health Alarm
- 자동 Runtime ON/OFF 스케줄
- 최초 관리자 Bootstrap

위 항목은 비용과 Runtime 수명주기를 함께 검토하는 다음 하위 단계에서 추가한다. 애플리케이션 HTTPS·OAuth·Session·WebSocket 검증은 [AWS Application Runtime](aws-application-runtime.md)의 curl Smoke 계약을 계속 사용한다.
