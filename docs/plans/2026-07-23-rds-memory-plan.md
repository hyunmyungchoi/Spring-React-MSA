# AWS RDS 메모리와 연결 풀 교정 계획

- 작성일: 2026-07-23
- 대상: AWS Learning PostgreSQL 16.14 `db.t4g.micro`
- 상태: Hikari 교정 코드·38/38 테스트·Commit/Push·Runtime OFF Foundation 적용·OFF 재계획 검증 완료
- 원칙: RDS와 Runtime을 켜지 않고 Foundation을 먼저 교정하며, 256MiB Alarm과 DB Class는 재측정 전 변경하지 않는다.

## 1. 문제

Post-Restore Full Smoke Runtime ON에서 RDS `FreeableMemoryLow` Alarm이 `OK → ALARM`으로 전환됐다. Alarm 계약은 `FreeableMemory Minimum ≤ 256MiB`, 5분 주기, 연속 `3/3`인 15분이며 누락 데이터는 `notBreaching`이다.

원본 RDS를 정지한 뒤 Alarm이 `ALARM → OK`로 돌아왔지만 이는 메모리가 회복된 결과가 아니다. 정지 후 CloudWatch 데이터가 누락되고 `notBreaching`이 적용된 결과이므로 다음 Runtime ON 전 원인을 교정해야 한다.

## 2. 2026-07-23 실측

분석 구간은 RDS 시작과 Runtime Full Smoke가 포함된 `16:45~18:35 KST`다.

| 지표 | 관측값 |
| --- | --- |
| FreeableMemory | 평균 172.56MiB, 최소 145.04MiB, 최대 230.54MiB |
| SwapUsage | 평균 0.52MiB, 최대 0.98MiB |
| CPUUtilization | 평균 5.68%, 최대 55.05% |
| DatabaseConnections | 평균 22.52, 최대 30 |
| FreeStorageSpace | 최소 17.06GiB |
| Read IOPS | 평균 1.33, 최대 11.83 |
| Write IOPS | 평균 2.50, 최대 18.27 |

Runtime Task가 DB에 연결하기 전 Connection은 0이었다. 세 DB 서비스가 기동한 뒤에는 30으로 고정됐고 FreeableMemory는 약 216MiB에서 145~169MiB로 내려갔다. CPU와 I/O는 낮고 Swap은 1MiB 미만이므로 현재 자료만으로 메모리 고갈이나 즉시 Class 상향을 판정하지 않는다.

## 3. 원인과 결정

`user-service`, `stock-service`, `member-bff`는 Hikari 설정을 지정하지 않았다. 기본 `maximumPoolSize=10`, `minimumIdle=maximumPoolSize` 계약과 실제 Connection 30이 일치한다. 짧은 Learning Smoke에서 서비스별 Idle Connection 10개는 과하다.

다음 순서로 교정한다.

1. AWS Runtime의 세 DB 서비스에만 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`를 설정한다.
2. 같은 서비스에 `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1`을 설정한다.
3. 로컬·Docker·Kubernetes 설정은 변경하지 않는다.
4. `db.t4g.micro`와 FreeableMemory 256MiB Alarm은 유지한다.
5. Runtime OFF에서 Task Definition만 교체한 뒤 별도 승인으로 짧은 Runtime ON 재측정을 수행한다.

## 4. 비용 대안

AWS Price List API의 2026-07-23 서울 리전 Single-AZ PostgreSQL On-Demand 조회값은 다음과 같다.

| Class | 메모리 | 시간당 | 730시간 |
| --- | ---: | ---: | ---: |
| `db.t4g.micro` | 1GiB | USD 0.025 | USD 18.25 |
| `db.t4g.small` | 2GiB | USD 0.051 | USD 37.23 |

`db.t4g.small`은 주문 가능하지만 시간당 USD 0.026, 730시간 기준 USD 18.98가 추가된다. Pool 교정은 추가 비용이 없으므로 먼저 적용하고 실측한다.

다음 Runtime ON에서도 15분 이상 FreeableMemory가 256MiB 이하이거나 Swap이 지속적으로 증가하면 `db.t4g.small` 전환을 별도 비용·변경 승인으로 검토한다. 연결 수가 15 이하이고 Swap이 낮은데 FreeableMemory만 안정적으로 256MiB 아래라면 Class와 Alarm 임계값을 함께 재평가하며 임의로 Alarm만 낮추지 않는다.

## 5. 구현과 테스트

- `modules/application-runtime`에 AWS 전용 Hikari Pool `5/1` 환경 변수를 추가했다.
- 세 DB 서비스에만 같은 계약이 들어가는 Terraform Assertion을 추가했다.
- `terraform fmt -check -recursive` 통과
- `terraform validate` 통과
- 전체 Terraform 계약 테스트 `38 passed, 0 failed`
- 기존 Cloud Map `failure_threshold` Provider deprecation 경고 외 오류 없음

## 6. 적용 Gate

첫 적용은 `learning_runtime_enabled=false`를 유지한다. 예상 변경은 세 DB 서비스 Task Definition 교체와 ECS Service의 새 Revision 참조뿐이다. RDS 시작, ASG 용량, ALB, Valkey, Runtime Alarm 생성은 허용하지 않는다.

Saved Plan은 다음을 만족해야 한다.

- 변경 서비스: `user-service`, `stock-service`, `member-bff`만
- 새 Task Definition 3개에 Hikari `5/1` 포함
- ECS Service 8개의 Desired Count `0` 유지
- RDS `stopped`, ASG `0/0/0` 유지
- RDS·Frontend·Secret·SNS·Restore 감사 Log 변경 0
- 실제 Secret, Account ID, Image Digest 문서 출력 금지

검증된 Saved Plan:

- 파일: `tfplan-rds-memory-hikari-foundation-off`
- 크기: 226,832 bytes
- SHA-256: `56fabf74af8b2f50bf19ca5c1c6200246ddb9855715cc3257a3298d4940b3f87`
- 생성 시각: `2026-07-23 23:47:21.312 KST`
- 운영 Gate 만료: `2026-07-24 01:17:21.312 KST`
- 생성 기준 State serial: 107
- 변경: `3 add, 3 change, 3 destroy`, Task Definition 교체 3개
- 교체 대상: `user-service`, `stock-service`, `member-bff`
- ECS Service 3개는 새 Revision을 참조하지만 Desired Count는 계속 0이다.
- 세 새 Task Definition 모두 Hikari `maximumPoolSize=5`, `minimumIdle=1` 계약을 포함한다.
- RDS·ASG·ALB·Valkey·Alarm·Frontend·Secret·SNS·Restore 감사 Log 변경 0
- RDS는 `stopped`, Runtime과 ASG는 0을 유지한다.

Apply 직전 Source Commit/Push, Plan SHA-256, State serial 107, 운영 Gate와 Runtime/RDS OFF 실상태를 다시 확인한다. State가 바뀌거나 Gate가 만료되면 이 Plan을 적용하지 않고 폐기·재생성한다.

승인 문구:

`RDS Hikari Pool 5/1 교정 Commit/Push + Foundation OFF Plan 56fabf74af8b2f50bf19ca5c1c6200246ddb9855715cc3257a3298d4940b3f87 적용 승인`

### 6.1 실행 결과

- 코드·테스트 Commit: `629f2fbd2b72771c316678e4943083c7c46f89fe`
- 계획 문서 Commit: `adb4d8a18b9da0d53e756dfc0b3ad14cc8eb08ea`
- 두 Commit을 `master`에 Push한 뒤 원격 HEAD 일치를 확인했다.
- Apply 직전 Plan SHA-256, State serial 107, 운영 Gate, ECS `0/0/0`, ASG `0/0/0`, RDS `stopped`를 재검증했다.
- 승인 Plan을 정확히 `3 added, 3 changed, 3 destroyed`로 적용했다. Destroy 3개는 이전 Task Definition Revision 등록 해제다.
- State serial은 108로 증가했다.
- `user-service:3`, `stock-service:3`, `member-bff:4`가 Hikari `maximumPoolSize=5`, `minimumIdle=1`을 포함하며 각 ECS Service가 해당 Revision을 참조한다.
- 나머지 다섯 Task Definition에는 두 Hikari 환경 변수가 없음을 확인했다.
- ECS Service 8개 `0/0/0`, ASG `0/0/0`, RDS `stopped`, Valkey·ALB Target Group·Runtime Alarm 0을 유지했다.
- 현재 Image 8개와 동일 OFF 입력의 재계획은 `exit 0`, `No changes`였다.
- 적용한 Saved Plan은 같은 SHA-256을 다시 확인한 뒤 삭제했다.

## 7. 다음 Runtime ON 판정

교정 Foundation 적용 후 별도 승인으로 Runtime을 켜고 최소 30분 동안 다음을 확인한다.

- DB Connection 정상 기준: Idle 구간 약 3, 상한 15
- 세 DB 서비스 Health와 전체 인증·REST Smoke 정상
- FreeableMemory 5분 Minimum 추이
- SwapUsage가 지속적으로 증가하지 않음
- Hikari Connection Timeout 또는 Pending Thread 0
- FreeableMemory Alarm 상태와 SNS 전달

검증 후 Runtime을 다시 OFF하고 RDS를 정지한다. 최종 Class 또는 Alarm 변경은 이 재측정 결과로만 결정한다.

## 8. Runtime ON 재측정 Saved Plan

2026-07-24 사전 점검에서 Git `master`와 원격 HEAD는 `c2a857a2c764be8fdc414bd2095ae49d0e25e714`로 일치했고 작업 폴더는 깨끗했다. Remote State serial은 108, 주소는 249개였다. ECS Service 8개 `0/0/0`, ASG `0/0/0`, RDS `stopped`, NAT `available`, Private App 기본 경로 `active`를 확인했다. RDS 자동 재시작 예정은 `2026-07-30 18:44:16.118 KST`다.

입력과 검증 결과:

- 활성 Task Definition과 Digest 고정 Image 8/8
- `user-service`, `stock-service`, `member-bff`에만 Hikari `5/1`
- Stock Client ID 존재 여부만 확인하고 값은 출력하지 않음
- Shared Redis Secret의 `AWSCURRENT`, `redis_password` Key, 32~128자 영숫자 계약 확인
- `terraform fmt -check -recursive`·`terraform validate` 통과
- 전체 Terraform 계약 테스트 `38 passed, 0 failed`
- RDS Alarm 3개 Action 활성, Operations SNS Email 구독 1개 `Confirmed`

`TF_VAR_learning_runtime_enabled=true`로 만든 첫 시도는 `terraform.tfvars`의 `false`보다 우선순위가 낮아 Runtime을 켜지 못했다. 해당 Plan은 최신 ECS AMI 갱신 1건만 포함한 `0/1/0`, SHA-256 `9e0d68e070dd34ff05bd44fe443ce1fbe030c07254e73f7452adcc7347fe0518`이었고 Apply 없이 삭제했다. State와 AWS는 변경되지 않았다. 최종 Plan은 CLI `-var=learning_runtime_enabled=true`로 우선순위를 고정했다.

검증된 Runtime ON Saved Plan:

- 파일: `tfplan-rds-hikari-runtime-on-remeasurement`
- 크기: 230,686 bytes
- SHA-256: `fa6a9c0d9c3facaa4611c4684e6f96bfcfad3a85f8580b0abbdf7a5a1c50e124`
- 생성 시각: `2026-07-24 00:41:56.329 KST`
- 운영 Gate 만료: `2026-07-24 02:11:56.329 KST`
- 생성 기준 State serial: 108
- 변경: `40 add, 11 change, 0 destroy`, 교체 0
- 생성: Runtime Alarm 29개, Valkey 관련 6개(ElastiCache 5개·Redis Host SSM Parameter 1개), Public ALB·HTTPS Listener·Host Rule 2개·`origin` Route 53 Record
- 갱신: ECS Service 8개 Desired `0 → 1`, ASG Min/Max `0/0 → 1/2`, Container Insights `disabled → enabled`, ECS Launch Template AMI 1건
- AMI: AWS 공식 ECS 최적화 AL2023 권장 `ami-0a3d67a807296fc6c`, `x86_64`, `available`
- RDS·Secret·Frontend·Task Definition·Restore 리소스 변경 0
- Redis Password는 Plan JSON에 직렬화되지 않았으며 Apply 때 같은 Ephemeral 입력을 다시 제공해야 함
- Plan 생성 뒤 State serial 108과 AWS OFF 상태 유지

2026-07-24 AWS Price List API 기준 시간당 고정비 추정은 NAT/EIP를 포함해 USD 0.3767이다. 이미 실행 중인 NAT/EIP USD 0.064를 제외한 Runtime ON 증분은 USD 0.3127/시간이고 명목 30분은 약 USD 0.1564다. EBS, ALB LCU, NAT 처리량, 데이터 전송, 서비스별 최소 또는 부분 시간 과금과 프로비저닝 시간은 별도다.

Apply 전에는 Plan Hash·State serial 108·Gate·Git 원격 HEAD·AWS OFF를 다시 확인하고 RDS를 `available`까지 시작한다. Apply 후 ASG `1/1/2`, ECS·Container·Cloud Map 8/8, ALB Target 2/2, Valkey와 Runtime Alarm을 확인한 시점부터 최소 30분을 측정한다. RDS 시작이나 Apply는 아직 수행하지 않았다.

다음 적용 승인 문구:

`Hikari 5/1 Runtime ON 사전 문서 Commit/Push + RDS 시작 + Plan fa6a9c0d9c3facaa4611c4684e6f96bfcfad3a85f8580b0abbdf7a5a1c50e124 적용 + 30분 RDS 지표 재측정 + HTTPS/OAuth/Session/WebSocket/REST curl·SNS Alarm Smoke 승인`
