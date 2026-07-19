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

저장소 구현, mock 기반 Terraform 테스트 26개와 Runtime OFF Saved Plan `7 added, 0 changed, 0 destroyed` 검토는 완료했다. 명시적 Apply 승인, SNS Email 확인과 실알림 검증은 아직 남아 있다. 상세 절차는 [AWS 관측성 Foundation 런북](../runbooks/aws-observability.md)을 따른다.

### 2단계: Runtime 수명주기 관측

- Container Insights 비용을 확인한 뒤 활성화 여부를 결정한다.
- Backend 서비스별 Task Count, CPU, Memory Alarm 범위를 결정한다.
- Runtime ON 동안에만 존재하는 ALB Alarm의 생성·삭제 수명주기를 Terraform에 고정한다.
- Runtime ON/OFF 스케줄과 RDS 최대 정지 기간 자동 시작 감시를 연결한다.

### 3단계: 운영 초기화

- 최초 관리자 Bootstrap을 일회성·감사 가능한 절차로 구현한다.
- Bootstrap 뒤 관리자 가입 기능이 AWS `prod`에서 계속 비활성인지 확인한다.
- 전체 HTTPS·OAuth·Session·WebSocket curl Smoke와 Backup Restore 검증으로 마무리한다.
