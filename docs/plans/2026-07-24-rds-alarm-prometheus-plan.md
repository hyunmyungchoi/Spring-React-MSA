# RDS Alarm·Member BFF Prometheus 교정 계획

- 작성일: 2026-07-24
- 상태: 코드·테스트, Member BFF Build Once·ECR Promote, Foundation OFF·Runtime ON/OFF 전체 Smoke·RDS 정지·Applied Plan/로그 정리·최종 감사 완료
- 적용 기준: Source Git `65c326496477953c05791fad73b05cf80d258445`, Terraform 적용 전 State serial 119·주소 249개
- 현재 AWS 상태: State serial 131·주소 251개, ECS/Task/Container Instance·ASG Instance·Public ALB·Valkey·`origin`·Runtime Alarm 0, RDS `stopped`, 영속 Alarm 8개 `OK`, Applied Plan·Runtime Apply 로그 잔여 0

## 1. 범위

Hikari Pool `5/1` 교정 뒤 재측정한 RDS 메모리 기준선으로 DB Class와 영속 Alarm 계약을 결정한다. 함께 발견된 Member BFF `/actuator/prometheus` HTTP 500의 원인을 확정하고 후속 구현·배포 범위를 고정한다.

이번 진단 단계에서는 애플리케이션·Terraform·AWS 리소스를 변경하지 않았다.

## 2. RDS 재측정 결과

측정 구간은 2026-07-24 01:19~01:48 KST이며 CloudWatch 1분 원자료를 사용했다.

| 지표 | Hikari 교정 전 기준 | Hikari `5/1` 재측정 |
| --- | ---: | ---: |
| `DatabaseConnections` | 평균 24.43, 최대 30 | 평균 3.87, 최소 3, 최대 6 |
| `FreeableMemory` | 평균 172.56 MiB, 최소 145.04 MiB | 평균 197.09 MiB, 최소 190.14 MiB |
| `SwapUsage` | 최대 0.98 MiB | 최대 0.45 MiB |
| `CPUUtilization` | 평균 5.68% | 평균 4.07%, 최대 7.40% |

Hikari 축소로 연결 수는 정상화됐다. 재측정 동안 가용 메모리는 기존 256 MiB Alarm 임계값 아래였지만 Swap과 CPU는 매우 낮았고 연결 고갈·OOM·재시작 증거는 없었다.

현재 FreeableMemory Alarm은 `Minimum < 256 MiB`, 5분, 3/3, `treat_missing_data=notBreaching`이다. Runtime ON에서는 30/30점이 임계값 아래여서 `ALARM`이었고 RDS 정지 뒤 지표 누락으로 `OK`에 복귀했다. Alarm 동작 자체가 실패한 것이 아니라 `db.t4g.micro` 기준선에 비해 임계값이 높다.

AWS에서 PostgreSQL의 `FreeableMemory`는 Linux `MemAvailable`, `SwapUsage`는 사용 중인 Swap 용량이다. 지속적으로 낮은 가용 메모리 또는 Swap 사용이 확인될 때 더 큰 Class를 검토한다.

- [Amazon RDS 지표 정의](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-metrics.html)
- [Amazon RDS 문제 해결](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_Troubleshooting.html)

## 3. DB Class 결정

서울 리전 PostgreSQL 16.14 Orderable Option과 AWS Price List를 대조했다.

| Class | vCPU | Memory | On-Demand | 730시간 환산 |
| --- | ---: | ---: | ---: | ---: |
| `db.t4g.micro` | 2 | 1 GiB | USD 0.025/h | USD 18.25 |
| `db.t4g.small` | 2 | 2 GiB | USD 0.051/h | USD 37.23 |

`db.t4g.small`은 메모리가 2배지만 DB Compute 비용도 약 2.04배이고 월 차이는 약 USD 18.98이다. 현재 실측은 Hikari 교정 효과가 분명하고 Swap·CPU·연결 수가 안정적이므로 이번 후속에서는 `db.t4g.micro`를 유지한다.

다음 중 하나가 확인되면 Class 변경을 별도 Plan으로 다시 결정한다.

- `FreeableMemory <= 128 MiB`가 5분 3/3으로 지속
- `SwapUsage >= 64 MiB`가 지속되거나 증가
- OOM, DB 재시작, 메모리 부족 Error 발생
- `DatabaseConnections <= 15`인데도 메모리 압력이 지속

DB Class 변경은 Alarm·Member BFF 교정과 섞지 않는다.

## 4. RDS 영속 Alarm 결정

현재 CPU·FreeableMemory·FreeStorage 3개에서 다음 5개 계약으로 교정한다.

| Alarm | Metric/통계 | 목표 조건 | 평가 |
| --- | --- | --- | --- |
| CPU 높음 | `CPUUtilization` Average | 80% 이상 | 5분 × 3/3 |
| 가용 메모리 부족 | `FreeableMemory` Minimum | 128 MiB 이하 | 5분 × 3/3 |
| Swap 사용 | `SwapUsage` Maximum | 64 MiB 이상 | 5분 × 3/3 |
| 연결 수 증가 | `DatabaseConnections` Maximum | 16 이상 | 5분 × 3/3 |
| 가용 스토리지 부족 | `FreeStorageSpace` Minimum | 5 GiB 이하 | 5분 × 3/3 |

모든 Alarm은 RDS 정지 중 지표 누락을 정상으로 처리하도록 `treat_missing_data=notBreaching`을 유지한다. Terraform 계약 테스트의 영속 RDS Alarm 개수는 3개에서 5개로 갱신하고 임계값을 명시적으로 검증한다.

## 5. Member BFF Prometheus 500 원인

Member BFF 설정은 `health`, `info`, `prometheus` 노출을 선언하지만 Runtime classpath와 Fat JAR에 `micrometer-registry-prometheus`가 없다.

확인 결과:

- Member BFF는 Spring Boot 4.0.6, Micrometer 1.16.5를 사용한다.
- `dependencyInsight`와 Fat JAR 검사에서 Prometheus Registry가 0개였다.
- 존재하지 않는 Actuator Endpoint 요청은 `NoResourceFoundException`으로 떨어진다.
- Member BFF의 공통 catch-all 예외 처리가 이를 404가 아닌 500으로 바꾸고 별도 Error Log도 남기지 않는다.
- 기존 Member BFF 테스트 8개는 통과하지만 `/actuator/prometheus` 계약 테스트가 없다.
- Backend 8개 중 Stock Service만 Prometheus Registry를 선언하며 나머지 7개는 같은 잠재 누락이 있다.

Spring Boot에서 Prometheus Endpoint를 만들려면 Prometheus Registry 의존성이 필요하다.

- [Micrometer Prometheus 구현](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Spring Boot Prometheus Actuator Endpoint](https://docs.spring.io/spring-boot/4.0/api/rest/actuator/prometheus.html)

## 6. Member BFF 후속 결정

이번 AWS 종결 범위는 실제 500이 관측된 Member BFF만 교정한다.

- Runtime 의존성에 `io.micrometer:micrometer-registry-prometheus` 추가
- Gradle Lock과 Verification Metadata 동시 갱신
- `/actuator/prometheus` HTTP 200, Prometheus Content-Type과 기본 Metric 확인 테스트 추가
- `NoResourceFoundException`을 Admin BFF와 같은 404로 변환하고 테스트 추가
- AWS Smoke에서 Prometheus 200과 Hikari Pending 0 확인
- 기존 공개 Route·Security·Actuator 노출 범위는 변경하지 않음

나머지 7개 서비스의 Registry, Kubernetes ServiceMonitor와 Prometheus Target 전수 표준화는 기존 관측성 계획의 별도 백로그로 유지한다. 이번 Member BFF Image 교정에 다른 7개 Image를 섞지 않는다.

## 7. 구현·배포 경계

다음 단계는 코드·테스트와 Runtime OFF Foundation까지만 수행한다.

1. RDS Alarm Terraform과 계약 테스트 교정
2. Member BFF 의존성·예외 처리·통합 테스트 교정
3. Gradle Lock·Verification Metadata 검증
4. Member BFF GHCR Build Once 후 동일 OCI Digest를 ECR로 Promote
5. Runtime OFF 입력으로 Foundation Saved Plan 생성

Foundation OFF Plan은 RDS Alarm 2개 추가·기존 FreeableMemory Alarm 1개 변경과 Member BFF Task Definition 교체·Desired 0 Service 참조 갱신만 포함해야 한다. 정확한 변경 개수와 교체 범위는 생성된 Saved Plan을 기준으로 다시 검토한다.

다음 항목이 포함되면 Plan을 적용하지 않는다.

- RDS Class·Engine·Storage 변경 또는 RDS 시작
- ECS/ASG 실행 용량 증가
- Public ALB·Valkey·Runtime Alarm 생성
- Member BFF 외 Image·Task Definition 교체
- Network·DNS·Frontend·Secret 변경

Foundation 적용 뒤 Runtime ON·전체 Smoke와 후속 Runtime OFF·RDS 정지는 각각 별도 Saved Plan과 승인을 사용한다.

다음 승인 문구:

`RDS Alarm 128MiB·Swap 64MiB·Connection 16 + Member BFF Prometheus 200/404 교정 구현·테스트 + Member BFF Build Once·ECR Promote + Foundation OFF Saved Plan 생성 승인`

## 8. 구현·테스트 결과

2026-07-25 승인 범위로 다음 변경을 구현했다.

- FreeableMemory 임계값 `256 MiB → 128 MiB`
- SwapUsage Maximum 64 MiB와 DatabaseConnections Maximum 16 Alarm 추가
- 영속 RDS Alarm 계약 `3 → 5`, 5분 3/3과 `treat_missing_data=notBreaching` 유지
- Member BFF Runtime에 Micrometer Prometheus Registry 1.16.5 추가
- Gradle Lock과 SHA-256 Verification Metadata에 Prometheus 1.4.3 전이 의존성 고정
- 실제 임의 포트 `/actuator/prometheus` HTTP 200·Prometheus Content-Type·Metric 노출 테스트 추가
- Member BFF `NoResourceFoundException` 404 응답과 회귀 테스트 추가

검증 결과:

- Member BFF `clean test bootJar`: `10 passed, 0 failed`
- Fat JAR: `micrometer-registry-prometheus-1.16.5.jar`와 Prometheus 1.4.3 구성요소 6개 포함
- `terraform fmt -check -recursive`: 통과
- `terraform validate -no-color`: 통과
- 전체 Terraform 계약 테스트: `38 passed, 0 failed`

구현·테스트 직후에는 아직 Member BFF Image를 게시·Promote하지 않았고 Terraform Saved Plan도 생성·적용하지 않았다. 당시 AWS 적용값은 기존 RDS Alarm 3개와 FreeableMemory 256 MiB였으며 Runtime과 RDS는 OFF 기준을 유지했다.

## 9. Build Once·ECR Promote·Foundation OFF Plan

2026-07-25 Source Commit `65c326496477953c05791fad73b05cf80d258445`를 기준으로 자동 Push Workflow가 Member BFF 하나만 선택했다.

- GHCR Build Once: Run `30110469700`, 성공
- GHCR 검사 결과: 기존 SHA Image 없음, 새 Image 1개 Build
- GHCR OCI Digest: `sha256:ee0d4cc6a4aa096ab4f48b4c45adf5d08f9f8f369100362df9b1d063cfdd7cc3`
- Kubernetes 자동 고정: Member BFF 매니페스트만 Bot Commit `23b0ac25bdf1fdd1cf4ef13b68e9c1588c3dc077`로 갱신
- ECR Promote: Run `30110888017`, `action=promoted`, 재빌드 없음
- ECR Source SHA Tag와 OCI Digest가 GHCR Digest와 일치

현재 ECS Task Definition 8개의 Image를 기준 입력으로 사용하고 Member BFF 항목만 검증된 ECR Digest로 교체했다. 기존 Flyway Migration Image Map은 변경하지 않았다. Restore 감사 Log·Frontend·Public Domain·Observability·Watchdog Foundation을 보존하고 `learning_runtime_enabled=false`로 Saved Plan을 생성했다.

검증된 Saved Plan:

- 파일: `tfplan-rds-alarm-member-bff-foundation-off`
- 크기: 227,865 bytes
- SHA-256: `147eb62ff0298e3fe0cb707bff32e7b432bcbb63cbf24f98cf13334549c73331`
- 생성 시각: `2026-07-25 02:06:58.675 KST`
- 운영 Gate 만료: `2026-07-25 03:36:58.675 KST`
- 생성 기준 State serial: 119
- 변경: `3 add, 2 change, 1 destroy`
- 생성: SwapUsage 64 MiB·DatabaseConnections 16 Alarm 2개, Member BFF Task Definition 새 Revision 1개
- 변경: FreeableMemory `256 MiB → 128 MiB` Alarm, Desired 0 Member BFF ECS Service의 Task Definition 참조
- 삭제: 이전 Member BFF Task Definition Revision 1개
- RDS Class·Engine·Storage·상태, 다른 7개 Image·Task Definition, Flyway Migration, ECS/ASG 용량, ALB·Valkey·Runtime Alarm, Network·DNS·Frontend·Secret 변경 0
- Ephemeral Redis Password는 Plan 변수 항목의 값이 `null`

Plan 생성 후에도 ECS Service 8개 `0/0/0`, ASG `0/0/0`, RDS `stopped`, State serial 119를 유지했다. 임시 JSON 변수 파일은 삭제했고 Binary Saved Plan만 Git Ignore 상태로 보존한다.

적용 승인 문구(완료):

`RDS Alarm·Member BFF Foundation OFF Plan 147eb62ff0298e3fe0cb707bff32e7b432bcbb63cbf24f98cf13334549c73331 적용 승인`

## 10. Foundation OFF Apply 결과

2026-07-25 `02:29:26.395 KST`에 승인된 Binary Saved Plan의 Hash·Gate·State serial 119·Git clean·변경 주소 5개·AWS Runtime OFF를 재검증한 뒤 적용했다.

- Apply 결과: `3 added, 2 changed, 1 destroyed`
- Terraform State: serial `119 → 120`, 주소 `249 → 251`
- Member BFF ECS Task Definition: revision `5`
- Member BFF Image: ECR Digest `sha256:ee0d4cc6a4aa096ab4f48b4c45adf5d08f9f8f369100362df9b1d063cfdd7cc3`
- Member BFF ECS Service: Desired/Running/Pending `0/0/0`
- 영속 RDS Alarm: `3 → 5`
- FreeableMemory: Minimum `<= 128 MiB`
- SwapUsage: Maximum `>= 64 MiB`
- DatabaseConnections: Maximum `>= 16`
- 공통 계약: 300초, Evaluation/Datapoints `3/3`, `notBreaching`, SNS Alarm/OK Action 각 1개
- 기존 CPU 80%·FreeStorage 5 GiB Alarm을 포함한 5개 모두 AWS 실제 계약과 일치하고 상태는 `OK`
- 최종 경계: ECS 8개·ASG `0`, RDS `stopped`, ALB·Valkey·Runtime Alarm `0`, Watchdog Alarm 3개, `ALARM` 상태 0
- 적용 당시 변수로 Terraform State 기준 `No changes`, 종료 코드 `0`

`No changes` 첫 출력 캡처가 PowerShell native stderr 처리로 중단되며 현재 실행이 만든 S3 잠금이 남았다. Terraform 프로세스 0과 State serial 120을 확인하고 해당 잠금 ID만 `force-unlock`한 뒤, 변수 타입별 인코딩과 `-lock=false` 읽기 전용 Plan으로 `No changes`를 확정했다. AWS 리소스와 State 추가 변경은 없었다.

Runtime ON 사전 점검 승인 문구(완료):

`RDS 시작 + RDS Alarm·Member BFF Runtime ON 사전 점검 + Saved Plan 생성 승인`

## 11. Runtime ON 사전 점검·Saved Plan

2026-07-25 Runtime OFF 기준 State serial 120·주소 251개와 Git clean을 확인하고 원본 RDS만 시작했다.

- 시작 요청: `02:52:12.309 KST`
- `available`: `03:00:48 KST`, 약 8분 36초
- RDS: PostgreSQL 16.14, `db.t4g.micro`, Single-AZ, Private, 20 GiB `gp3`, Backup 7일, 삭제 보호
- Monitoring Interval 0·Performance Insights OFF·Master Secret `active`
- 영속 RDS Alarm 5개 모두 계약 일치, `ALARM` 상태 0
- 서울 리전 RDS 현재가: `$0.025/시간`, 6시간 Compute `$0.15`

Runtime ON 기반 점검:

- Runtime Secret 7개: 삭제 예약 없음, `AWSCURRENT` 각 1개, 필수 JSON Key·비어 있지 않은 값 계약 통과, 값 비노출
- Application ECR Digest·ECS Task Definition Image 8/8 일치
- Member BFF revision 5와 ECR Digest `sha256:ee0d4cc6a4aa096ab4f48b4c45adf5d08f9f8f369100362df9b1d063cfdd7cc3`
- NAT `available`, Private App Subnet 2개·서로 다른 2 AZ·Public IP 자동 할당 OFF
- 정적 DNS 3개 존재, Runtime `origin` Record 부재
- CloudFront 2개 `Deployed`, 서울·버지니아 ACM `ISSUED`, SNS Email 구독 1개 확인

Saved Plan:

- 파일: `tfplan-rds-alarm-member-bff-runtime-on`
- 크기: 234,394 bytes
- SHA-256: `679fa01852e67ce8b13137a545eba220605c78984fe7e07c9b6824e00abc9d89`
- 생성 시각: `2026-07-25 03:03:05.037 KST`
- 운영 Gate 만료: `2026-07-25 04:33:05.037 KST`
- 생성 기준 State serial: 120
- 변경: `40 add, 10 change, 0 destroy`
- 생성 40개: Public ALB·HTTPS·Gateway Rule 2·`origin` 5개, Valkey 계열 6개, Runtime Alarm 29개
- 변경 10개: ECS Service 8개 Desired 1, ASG `1/1/2`, ECS Container Insights 활성
- Task Definition·Image·RDS·영속 RDS Alarm·Secret·Network·Frontend 변경과 삭제 0
- Redis Password: Source `ephemeral=true`·Sensitive, Plan 변수값 `null`, 실제 Secret 문자열 Plan 비포함
- Apply 때 Redis Password를 Secrets Manager에서 프로세스 메모리로 다시 주입해야 한다.

첫 후보는 `terraform.tfvars`의 Runtime OFF 값이 환경변수보다 우선해 `No changes`로 끝났고 Saved Plan을 만들지 않았다. Runtime 플래그를 CLI 우선순위로 고정한 두 번째 후보가 정상 `40/10/0`을 생성했다. PowerShell이 출력 파일 변수명을 문자 그대로 전달해 생성된 Binary는 Terraform 디렉터리 경계를 검증한 뒤 의도한 Git Ignore 파일명으로 이동했다. 두 경우 모두 AWS와 State 변경은 없었다.

현재 고정 시간당 비용은 RDS `$0.025` + EC2 `m6i.xlarge` `$0.236` + Valkey `cache.t4g.micro` `$0.0192` + ALB `$0.0225` = `$0.3027`, 6시간 `$1.8162`다. ALB LCU `$0.008/LCU-h`와 데이터 전송은 별도이고 NAT는 기존 Foundation 비용이라 증분에서 제외한다.

Plan 생성 후에도 State serial 120·주소 251개, ECS Service 8개 `0/0/0`, ASG `0/0/0`, ALB·Valkey·Runtime Alarm 0이다. RDS만 `available`이며 Binary Plan 외 Git 변경은 문서 기록뿐이다.

다음 승인 문구:

`RDS Alarm·Member BFF Runtime ON Plan 679fa01852e67ce8b13137a545eba220605c78984fe7e07c9b6824e00abc9d89 적용 + ECS 수렴 + HTTPS/OAuth/Session/WebSocket/REST/Prometheus 200·404/SNS Alarm curl Smoke 승인`

## 12. Runtime ON 적용·전체 Smoke

2026-07-25 승인 Gate 안에서 Saved Plan Hash, State serial 120, Git clean, RDS `available`, Runtime OFF와 변경 `40 add, 10 change, 0 destroy`를 다시 확인했다. Secrets Manager의 Redis Password는 Apply 자식 프로세스 메모리에만 Ephemeral 변수로 주입했고 Plan·로그·문서에는 기록하지 않았다.

- 적용 결과: `40 added, 10 changed, 0 destroyed`, stderr 0
- 적용 후 State: serial 125·주소 291개
- ASG: Min/Desired/Max `1/1/2`, Instance 1개 `InService`·`Healthy`
- ECS: Service 8개 `1/1/0`, Rollout 8/8 `COMPLETED`, Task·Container 8/8 `HEALTHY`
- Data/Edge: RDS·Valkey `available`, ALB `active`, Target 2/2 `healthy`, Cloud Map 8/8, `origin` DNS 정상
- Alarm: 영속 RDS 5/5와 Runtime 29/29 `OK`

공개 curl Smoke는 정적 6개와 Readiness·OIDC·BFF 6개가 모두 HTTP 200이었고 Root 308의 Path·Query 보존을 확인했다. 새 무작위 `ROLE_USER`의 Registration 201, Password Login·OAuth Authorization Code·인증 BFF Session·CSRF Heartbeat·보호 REST 200, 필수 Cookie 3종과 Logout 뒤 익명 Session을 검증했다. 임시 비밀번호와 Cookie 파일은 폐기했으며 Smoke 계정 1개는 감사 데이터로 남는다.

같은 인증 Session의 WebSocket은 `CONNECTED`, `HISTORY`, `PONG`, 자체 `CHAT_MESSAGE`를 모두 수신했고 식별 메시지의 REST History 영속성도 HTTP 200으로 확인했다. `/bff/actuator/prometheus`는 HTTP 200·Prometheus Content-Type·JVM Metric을 반환했고 `hikaricp_connections_pending` 최대값은 0이었다. 임의 미존재 Actuator 경로는 HTTP 404 `application/json`으로 catch-all 500 교정을 입증했다.

SNS Email 구독은 `Confirmed`이며 DatabaseConnections Alarm을 승인된 Smoke로 `ALARM → OK` 전환했다. 최근 1시간 지표는 `Published 45 → 47`, `Delivered 45 → 47`, `Failed 0 → 0`으로 실제 두 알림 전달을 확인했고 최종 전체 관련 Alarm은 `ALARM 0`, `INSUFFICIENT_DATA 0`이다. 승인 Saved Plan의 원래 입력과 새 Ephemeral Redis Password를 사용한 ON 재계획은 `DetailedExitCode 0`, `No changes`다.

다음 단계는 별도 승인으로 Runtime OFF 사전 점검과 비용 종료 Saved Plan을 생성하는 것이다.

## 13. Runtime OFF 사전 점검·Saved Plan

2026-07-25 Runtime ON 기준 Git HEAD와 origin/master `1194be816f2c0ab768999bb071a1143cea75fbf6`, Git clean, Terraform 프로세스 0, State serial 125·주소 291개를 확인했다. ECS 8개 `1/1/0`·Rollout 완료, ASG `1/1/2`·Healthy Instance 1개, RDS·Valkey `available`, ALB `active`, Target 2/2·Cloud Map 8/8, 관련 Alarm 37/37 정상이었다.

Application Image는 실행 Task Definition과 8/8 일치하며 모두 OCI Digest로 고정돼 있다. Runtime Secret 7개는 삭제 예약 0, Redis Secret은 AWSCURRENT Key 계약을 통과했고 SNS Email 구독은 `Confirmed`, `origin` A Alias와 DNS A 2개도 정상이다. Secret 실제 값은 출력·문서화하지 않았다.

Runtime ON Saved Plan의 고정 입력을 그대로 재사용하고 `learning_runtime_enabled=false`만 적용해 다음 Binary Saved Plan을 만들었다.

- 파일: `tfplan-rds-alarm-member-bff-runtime-off`
- 크기: 252,778 bytes
- SHA-256: `9a4ce0c1e778c874c115806d0135987559c289d50d804835588a35e60d7ff69f`
- 생성 시각: `2026-07-25 03:56:04.360 KST`
- 운영 Gate 만료: `2026-07-25 05:26:04.360 KST`
- 생성 기준 State: serial 125·주소 291개
- 변경: `0 add, 10 change, 40 destroy`, Replace 0

삭제 40개는 Runtime Alarm 29개, Valkey·Redis Host Parameter 6개, Public ALB·HTTPS Listener·Gateway Rule 2개·`origin` 5개뿐이다. 갱신 10개는 ECS Service 8개 `desired 1 → 0`, ASG min/max `1/2 → 0/0`과 ECS Container Insights `enabled → disabled`다. 계획 출력의 ASG Runtime Capacity는 `0/0/0`이다. ASG의 `desired_capacity` 리소스 속성은 Managed Scaling을 위해 코드에서 `ignore_changes`지만 max 0 적용 시 AWS Desired도 0으로 수렴해야 하며 Apply 후 실상태로 검증한다.

Task Definition·Image·Service Registry, RDS·영속 RDS Alarm 5개, SNS·Watchdog, Secret, Network/NAT, Frontend/CloudFront, ACM과 정적 DNS 변경은 0이다. Plan 변수의 Redis Password는 `null`이며 실제 값은 직렬화되지 않았다. 이 Plan은 RDS를 정지하지 않으므로 Runtime 종료 검증 뒤 별도 `stop-db-instance`가 필요하다.

OFF Apply 직후 RDS를 제외한 고정 비용 약 `$0.2777/시간`이 종료되고, 후속 RDS 정지로 Compute `$0.025/시간`이 추가 종료돼 현재 고정 비용 `$0.3027/시간`을 멈춘다. LCU·전송량·Alarm 사용량은 별도다. Plan 생성 후 State와 AWS는 그대로 ON이며 Git clean과 Binary Hash를 다시 확인했다.

다음 승인 문구:

`RDS Alarm·Member BFF Runtime OFF Plan 9a4ce0c1e778c874c115806d0135987559c289d50d804835588a35e60d7ff69f 적용 + ECS/ASG/ALB/Valkey/Runtime Alarm 종료 + 정적 curl Smoke·No changes + RDS 정지 승인`

## 14. Runtime OFF 적용·RDS 정지

2026-07-25 `04:07:34.094 KST`에 Plan Hash·Gate·`0/10/40`·State serial 125·주소 291개·Git clean·Runtime ON 8/8·RDS available을 재검증했다. 승인된 Binary Saved Plan만 단일 Terraform 프로세스로 적용했고 결과는 `0 added, 10 changed, 40 destroyed`, stderr 0이다.

- 적용 후 State: serial 131·주소 251개
- ECS Service 8개 `0/0/0`, Running Task 0, Active Container Instance 0
- Container Insights `disabled`
- ASG `0/0/0`, Instance 0
- Public ALB·Valkey·`origin`·Redis Host Parameter·Runtime Alarm·Target 등록 0
- Task Definition·ECR Repository 8개, Cloud Map Service 8개, Network/NAT, Frontend/CloudFront, Secret 보존
- 영속 RDS Alarm 5개·Watchdog Alarm 3개 총 8개 `OK`, SNS Email 구독 `Confirmed`

CloudFront 정적 Member/Admin 6개는 모두 HTTP 200·HTML이었고 Root는 Path·Query를 보존한 308이었다. Runtime이 제거된 Member/Admin API는 각각 502로 OFF 계약을 충족했다. 동일 OFF 입력 Terraform 재계획은 `DetailedExitCode 0`, `No changes`, State serial 131·주소 251개였다.

Runtime 0과 `No changes`를 확인한 뒤 원본 RDS 정지를 `2026-07-25 04:19:15.988 KST`에 요청했고 `04:26:10.668 KST` 조회에서 `stopped`를 확인했다. PostgreSQL 16.14 `db.t4g.micro`, 삭제 보호, Backup 7일을 유지하며 자동 재시작 예정은 `2026-08-01 04:25:37.967 KST`다.

적용된 Saved Plan Hash는 승인값과 일치하지만 State가 변경됐으므로 재사용하지 않는다. 다음 단계는 별도 승인으로 Applied Saved Plan과 임시 Apply 로그를 삭제하고 최종 AWS·Git·문서를 감사하는 것이다.

## 15. Applied Plan·임시 로그 정리와 최종 감사

삭제 전에 `tfplan.ecr`과 이번 교정의 Foundation OFF·Runtime ON·Runtime OFF Saved Plan 4개 경로·Hash를 다시 확인했다. `tfplan.ecr`의 생성 주소 18/18은 현재 State에 존재해 과거 적용 완료 Plan임을 확인했고, Runtime ON/OFF Apply stdout은 각각 `40/10/0`, `0/10/40`, stderr 두 파일은 0줄이었다.

Runtime OFF Saved Plan 입력으로 마지막 Terraform 재계획을 실행해 `DetailedExitCode 0`, `No changes`, State serial 131·주소 251개와 RDS `stopped`를 확인했다. 이후 승인된 절대 경로의 Applied Saved Plan 4개와 Runtime Apply stdout/stderr 로그 4개만 비재귀 삭제했다.

- 삭제 승인 대상: 8개
- 삭제 완료: 8개
- `tfplan*` 잔여: 0
- 이번 Runtime Apply 로그 잔여: 0
- Terraform 프로세스·Secret 환경변수·Git 변경: 0

2026-07-25 `04:40:23.976 KST` 최종 AWS 감사에서 ECS Service 8개 `0/0/0`, Running Task·Container Instance 0, Container Insights disabled, ASG `0/0/0`·Instance 0, ALB·Valkey·`origin`·Redis Host Parameter·Runtime Alarm 0을 다시 확인했다. RDS는 Private Single-AZ `stopped`, 삭제 보호와 Backup 7일을 유지하며 영속 Alarm 8/8 `OK`, SNS 구독 `Confirmed`다.

Task Definition·ECR Repository·Cloud Map Service는 각각 8개, Cloud Map 등록은 0, CloudFront는 2/2 `Deployed`다. 최종 curl은 정적 6개 200, Root 308 Path·Query 보존, Member/Admin API 502로 9/9 통과했다. Git HEAD와 origin/master가 일치하고 작업 폴더는 clean이었다.

이 계획의 AWS 실행 단계는 모두 완료됐다. RDS는 `2026-08-01 04:25:37.967 KST` 자동 재시작 예정이므로 그 전에 계속 OFF로 둘지 다음 학습 Runtime을 시작할지 판단한다.
