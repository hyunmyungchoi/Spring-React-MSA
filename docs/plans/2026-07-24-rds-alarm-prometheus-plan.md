# RDS Alarm·Member BFF Prometheus 교정 계획

- 작성일: 2026-07-24
- 상태: 사전 진단·후속 결정 완료, 구현·AWS 적용 전
- 기준 상태: Git `2f18a384d79999f6513b7ee7ec1283aea764b4b4`, Terraform State serial 119·주소 249개
- AWS 상태: ECS Service 8개와 ASG 0, Public ALB·Valkey·Runtime Alarm 0, 원본 RDS `stopped`

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
