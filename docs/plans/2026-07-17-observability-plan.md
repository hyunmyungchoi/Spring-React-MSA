# 관측성 개선 계획

- 작성일: 2026-07-17
- 대상: Spring MSA, Kafka, ingress-nginx, Kubernetes

## 현재 기준선

- Spring Actuator health/info/prometheus 노출
- kube-prometheus-stack 87.16.1
- Loki 18.5.0 monolithic + MinIO
- Promtail 6.17.1
- Grafana ingress와 Loki datasource
- Prometheus 7일, Loki 168시간 보존
- Kafka JMX exporter와 kafka-exporter ServiceMonitor
- Stock 외부 API/cache custom metric

현재 값은 local Kubernetes 학습 환경 기준이며 Grafana 기본 계정, 단일 replica, 작은 PVC를 사용한다.

## 1단계: Metric 수집 완성

- 모든 Spring Service에 Prometheus registry가 실제 포함됐는지 확인한다.
- `spring-msa` namespace ServiceMonitor를 서비스별로 정의한다.
- Gateway request rate/error/latency, BFF downstream latency를 추가한다.
- session/presence, WebSocket, outbox metric을 추가한다.
- label cardinality를 검토하고 user/session/message ID를 label로 쓰지 않는다.

완료 조건:

- Prometheus Targets에서 모든 의도한 service가 UP이다.
- 배포 SHA를 build/info metric 또는 pod label로 조회할 수 있다.

## 2단계: 구조화 로그와 추적성

- production log를 JSON으로 표준화한다.
- `traceId`, `requestId`, `service`, `version`, `environment`를 포함한다.
- password, token, cookie, session ID, chat content를 redact한다.
- Gateway가 correlation ID를 생성·전달하고 BFF/downstream 응답에도 보존한다.
- OpenTelemetry trace 도입 여부를 별도 ADR로 결정한다.

## 3단계: Dashboard

최소 dashboard:

1. 플랫폼: pod health/restart/CPU/memory/PVC
2. HTTP: gateway/BFF RPS, 4xx/5xx, p95/p99
3. 인증: login success/failure, admin role rejection, session count
4. Stock: Toss latency/error/rate limit, cache hit/stale
5. Chat: connections, persist/broadcast, Redis/Kafka/outbox
6. Kafka: broker health, consumer lag, DLT

Dashboard JSON은 Git에서 version 관리한다.

## 4단계: Alert

초기 alert 후보:

- service readiness 지속 실패
- 5xx 비율과 p95 latency 임계 초과
- pod crash loop/restart 급증
- PostgreSQL/Redis/Kafka unavailable
- Kafka consumer lag 또는 DLT 증가
- outbox oldest pending age 증가
- Toss rate limit 급증 및 stale cache 고갈
- PVC 사용량 임계 초과

각 alert에는 owner, severity, 확인 query, 대응 runbook link를 넣는다.

## 5단계: 운영 보안·보존

- Grafana `admin/admin`을 local 전용으로 제한한다.
- production credential은 Secret/SSO로 바꾼다.
- Loki/Prometheus PVC backup 또는 재생성 정책을 정한다.
- 환경별 retention과 개인정보 삭제 요구를 반영한다.
- Promtail chart의 장기 지원 여부와 대체 collector를 검토한다.

## 검증

- `install-observability.ps1`의 pinned chart로 신규 설치
- `check-observability.ps1`과 Prometheus target 검사
- 의도적 5xx/latency/Kafka lag로 alert fire/resolve 확인
- Grafana dashboard에서 배포 전후 SHA와 지표 비교
- 로그 redaction 자동 테스트

## AWS Learning 적용 단계

Kubernetes 관측성 계획과 별도로 AWS Learning 환경은 비용이 낮고 Runtime OFF에서도 유효한 기반부터 적용한다.

### 1단계: 영속 Foundation

- 기존 CloudWatch Log Group 12개와 7일 보존을 유지한다.
- 기존 월 USD 50 Budget과 실제 비용 USD 10/30/40/50 알림을 유지한다.
- SNS Operations Topic과 Email Subscription을 만든다.
- RDS CPU, Freeable Memory, Free Storage Alarm 3개를 만든다.
- RDS availability/backup/failure/low storage/maintenance/notification Event Subscription을 만든다.
- RDS 정지 중 지표 누락은 정상으로 취급한다.

첫 Runtime OFF Plan Apply는 SNS Topic 1개 생성 뒤 wildcard Topic Policy가 AWS에서 거부돼 부분 실패했다. 정책을 `sns:Publish` 전용으로 교정하고 전체 mock 테스트 `26 passed, 0 failed`와 부분 state 기준 복구 Plan을 검증한 뒤 `6 added, 0 changed, 0 destroyed`로 적용했다. 실상태는 Topic/Policy/Email Subscription/Event Subscription 각 1개, RDS Alarm 3개이며 재계획은 `No changes`다. 2026-07-21에 SNS Email Subscription이 `Confirmed`인지 확인하고 Topic 직접 발행, AWS 전달 지표 `Delivered 3`·`Failed 0`, Gmail 실제 수신까지 검증해 1단계를 완료했다. 상세 절차는 [AWS 관측성 Foundation 런북](../runbooks/aws-observability.md)을 따른다.

### 2단계: Runtime 수명주기 관측

- 2A에서 Enhanced가 아닌 일반 Container Insights를 Runtime ON 동안만 활성화한다.
- 2A에서 Backend 8개 서비스별 Task Count, CPU, Memory Alarm 24개를 ON 동안만 만든다.
- 2A에서 ALB 자체 5xx와 두 Gateway Target Group의 5xx·Unhealthy Host Alarm 5개를 ON 동안만 만든다.
- 2A Runtime OFF 계약은 Container Insights `disabled`, Runtime Alarm 0개이며 전체 Terraform mock 테스트 `28 passed, 0 failed`로 검증했다.
- 2B에서 Terraform state를 직접 변경하지 않는 Runtime 장시간 ON·RDS 자동 시작 임박 Watchdog을 먼저 연결한다.
- 자동 Runtime OFF는 승인된 Terraform 실행 경로가 생긴 뒤 별도 결정한다.

2A 코드는 `enable_runtime_observability`와 `learning_runtime_enabled`를 분리한다. 기능 Flag를 유지한 채 Runtime Flag만 ON/OFF하면 Container Insights와 29개 Alarm이 같은 Saved Plan 수명주기를 따른다. 실제 AWS 적용 전 Runtime OFF Saved Plan의 변경 범위와 SHA-256을 별도 승인받고, 이후 RDS 시작과 Runtime ON curl Smoke에서 Alarm 생성·상태·SNS 전달을 검증한다. 상세 절차는 [AWS 관측성 Foundation 런북](../runbooks/aws-observability.md)을 따른다.

2A Runtime OFF Saved Plan은 Source SHA `ed33b8323f8706c6dbd322404c55cbe9bfbd8582`에서 현재 적용된 Foundation Flag, ECR digest 8개와 Stock Client ID를 보존해 AWS 리소스 변경 0개로 수렴했다. 승인된 SHA-256 `90a5be1ca3f71e07e689be1b77045c6e2a8bd66995945748053c43c6a13fbba5`를 적용한 결과 `0 added, 0 changed, 0 destroyed`였고, Container Insights `disabled`, Runtime Alarm 0개, ECS·ASG·ALB·Valkey OFF, RDS `stopped`, 재계획 `No changes`를 확인했다.

2026-07-22에는 Source SHA `d2dc46b062be1deef7c0c4a55ff8a87a4c914579`의 Runtime ON Saved Plan SHA-256 `46c96d2b993afc8afe8c354ad93cf2c949cd0cee5d4973e5659629af3384ba45`를 승인 적용해 `40 added, 10 changed, 0 destroyed`로 완료했다. Container Insights `enabled`, Runtime Alarm 29개 `OK`, ECS·Container Health 8/8, ASG `1/1/2`, ALB Target 2/2, RDS·Valkey `available`을 확인했다. HTTPS curl 12/12, Registration·Password Login·OAuth·Session·CSRF·Logout과 공개 WebSocket 네 프레임·REST 영속성이 통과했다.

Alarm Smoke 중 Terraform은 `No changes`였지만 SNS Email Subscription 실상태가 `Deleted`인 불일치를 발견했다. 단일 구독만 교체하는 Recovery Plan SHA-256 `383b514ea0cba9eeac4572c5a909b493489e434cb722b5203203aaeff7eb930d`를 `1 added, 0 changed, 1 destroyed`로 적용하고 다시 확인했다. 최종 구독은 `Confirmed`, Alarm `ALARM → OK`, SNS `Published 2`·`Delivered 2`·`Failed 0`, 동일 ON 입력 재계획은 `No changes`다.

2A 비용 종료는 Source SHA `d4f923bedbf8375ae6ff9badbf7ab24c05c591d4`, Saved Plan SHA-256 `f44f5f7a22be792fdf17a1d0b5e7761ae57bd8ec12168c54a4b1c773698d89fd`를 승인 적용해 `0 added, 10 changed, 40 destroyed`로 완료했다. ECS Service·Task·Container Instance·ASG는 모두 0, Container Insights는 `disabled`, Runtime Alarm·ALB·Valkey·`origin`은 0이며 RDS는 별도 정지해 `stopped`다. 정적 curl 6/6 HTTP 200, Root 308, Runtime OFF API 502와 동일 OFF 입력 `No changes`를 확인했다. SNS Email Subscription `Confirmed`, RDS Alarm 3개, Task Definition·Image digest 8개, Frontend Bucket 6개·CloudFront 2개는 유지했고 적용 Plan은 삭제했다. 2A는 완료됐으며 다음은 2B Watchdog과 3단계 운영 초기화다.

2B 코드는 15분마다 실행되는 Python 3.12/ARM64 Lambda, EventBridge Schedule, 중복 알림 방지용 DynamoDB On-Demand Table, Heartbeat Custom Metric과 Watchdog 자체 Alarm 3개로 구현했다. Runtime 6시간 초과, RDS 자동 재시작 24시간 이내, Runtime OFF 중 RDS 실행을 감지하고 상태가 바뀔 때만 기존 SNS Topic으로 ALERT/RECOVERY를 발행한다. IAM은 RDS·ASG·EC2·ECS Describe만 허용하며 Runtime 시작·정지·용량 변경 권한은 없다. Python 단위 테스트 `7 passed`, 전체 Terraform mock 테스트 `30 passed, 0 failed`, `validate`를 통과했다. AWS 적용은 Runtime OFF와 현재 Foundation·Image digest 8개·Stock Client ID를 보존하는 별도 Saved Plan의 SHA-256 승인 뒤 진행한다.

### 3단계: 운영 초기화

- 최초 관리자 Bootstrap을 일회성·감사 가능한 절차로 구현한다.
- Bootstrap 뒤 관리자 가입 기능이 AWS `prod`에서 계속 비활성인지 확인한다.
- 전체 HTTPS·OAuth·Session·WebSocket curl Smoke와 Backup Restore 검증으로 마무리한다.
