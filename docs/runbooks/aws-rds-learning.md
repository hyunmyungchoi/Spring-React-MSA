# AWS Learning RDS 운영과 복구 Runbook

> 기준일: 2026-07-23
>
> 현재 상태: Terraform/Flyway, Admin Bootstrap, Backup Restore·Cleanup과 원본 Full Smoke 완료; Hikari `5/1` 재측정 Runtime ON 적용 뒤 원본 RDS `available`, ECS 8/8, State serial 113, 동일 ON 입력 `No changes`; 다음은 Runtime OFF·RDS 정지
>
> 범위: Learning 환경의 PostgreSQL 16 RDS 시작·정지, Backup/PITR 확인, Flyway 실패 대응

## 1. 안전 경계

- 대상 리전은 `ap-northeast-2` 하나다.
- DB Identifier는 `spring-react-msa-learning-postgres`다.
- RDS는 Public 접근을 허용하지 않고 Private Data Subnet과 Data Security Group만 사용한다.
- RDS Master Secret은 Bootstrap/관리 전용이며 Application Task에 주입하지 않는다.
- 실제 Secret Value는 Terraform, `.tfvars`, Plan, Output, 문서와 Git에 기록하지 않는다.
- RDS 삭제, Snapshot 삭제와 복원 테스트 DB 삭제는 각각 별도 Plan 또는 명시적 승인을 받는다.

## 2. 최초 Apply 기록

Terraform 운영 Runbook의 `tfplan-data-layer` Hash, `10 add / 0 change / 0 destroy`, 호출 IAM 주체와 서울 리전을 확인한 뒤 승인된 저장 Plan만 Apply했다. 적용 결과는 `10 added / 0 changed / 0 destroyed`였고 Remote State는 총 81개 주소가 됐다. 이 Plan은 이미 적용됐으므로 다시 실행하지 않는다.

Apply는 RDS를 즉시 실행 상태로 만들고 Secret/Storage 비용도 시작했다. 목록가 기준 Data Layer 상시 실행은 약 USD 24.07/월이며 현재 NAT/EIP와 함께 상시 실행하면 USD 50 Budget을 초과하므로 검증 후 RDS를 정지했다.

## 3. Apply 후 검증

PowerShell에서 다음 조회만 수행한다.

```powershell
$dbId = "spring-react-msa-learning-postgres"

aws rds describe-db-instances `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId `
  --query "DBInstances[0].{Status:DBInstanceStatus,Engine:Engine,Version:EngineVersion,Class:DBInstanceClass,Public:PubliclyAccessible,MultiAZ:MultiAZ,Encrypted:StorageEncrypted,Storage:AllocatedStorage,StorageType:StorageType,BackupDays:BackupRetentionPeriod,DeletionProtection:DeletionProtection,Endpoint:Endpoint.Address,MasterSecret:MasterUserSecret.SecretArn}"
```

다음을 모두 만족해야 한다.

- `Status=available`
- PostgreSQL `16.14`, `db.t4g.micro`, Single-AZ
- `Public=false`, `Encrypted=true`, `gp3=20 GiB`
- Backup 7일, 삭제 보호 활성
- Endpoint는 존재하지만 Internet에서 직접 연결되지 않음
- RDS Managed Master Secret ARN은 존재하되 Application Secret과 분리됨

Terraform 재계획도 `No changes`여야 한다. 차이가 있으면 DB 설정을 Console에서 수동 수정하지 말고 코드와 State 차이를 먼저 조사한다.

최초 Apply에서는 위 항목, Managed Master Secret `active`, Application Secret 7개의 Version 0개, 재계획 `No changes`를 모두 확인했다. 첫 Automated Backup 완료와 `LatestRestorableTime`도 확인했다. 2026-07-23 정지 상태의 사전 점검에서는 Instance 응답의 `EarliestRestorableTime`·`LatestRestorableTime`이 비어 있었지만 Automated Backup의 `RestoreWindow`가 존재함을 확인했다. 이후 실제 PITR로 `2026-07-23 11:02:47 KST` 시점의 별도 Private RDS를 복원하고 읽기 전용 검증한 뒤 즉시 정지했다.

## 4. Learning ON/OFF

RDS는 `enable_data_layer=false`로 OFF하지 않는다. 그 값은 영구 데이터 리소스 삭제 Plan을 만들고 `prevent_destroy`에 의해 차단된다. 일시적인 OFF는 RDS 정지 API를 사용한다.

### 정지

```powershell
$dbId = "spring-react-msa-learning-postgres"

aws rds stop-db-instance `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId

$deadline = (Get-Date).AddMinutes(15)
do {
  $status = aws rds describe-db-instances `
    --region ap-northeast-2 `
    --db-instance-identifier $dbId `
    --query "DBInstances[0].DBInstanceStatus" `
    --output text

  if ($status -eq "stopped") {
    break
  }

  Start-Sleep -Seconds 15
} while ((Get-Date) -lt $deadline)

if ($status -ne "stopped") {
  throw "RDS did not reach stopped within 15 minutes: $status"
}
```

### 시작

```powershell
$dbId = "spring-react-msa-learning-postgres"

aws rds start-db-instance `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId

aws rds wait db-instance-available `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId
```

RDS는 최대 7일 연속 정지 후 AWS가 자동 시작한다. `AutomaticRestartTime`과 Budget 알림을 확인하고, 학습하지 않는 기간에는 다시 정지한다. 정지 중에도 Storage, Backup과 Secrets Manager 비용은 남는다. NAT도 별도 OFF하지 않으면 현재 NAT/EIP 비용이 계속 발생한다.

## 5. Backup과 PITR 확인

실행 중인 RDS는 Instance 응답의 복원 시점을 확인하고, 정지 상태에서는 Automated Backup의 `RestoreWindow`를 함께 확인한다. 정지된 Instance의 직접 복원 시점 필드가 비어 있다는 이유만으로 복원 불가로 판정하지 않는다.

```powershell
$dbId = "spring-react-msa-learning-postgres"

aws rds describe-db-instances `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId `
  --query "DBInstances[0].{Status:DBInstanceStatus,Earliest:EarliestRestorableTime,Latest:LatestRestorableTime,BackupWindow:PreferredBackupWindow,BackupDays:BackupRetentionPeriod}"

aws rds describe-db-instance-automated-backups `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId `
  --query "DBInstanceAutomatedBackups[0].{Status:Status,RestoreWindow:RestoreWindow,Retention:BackupRetentionPeriod,Encrypted:Encrypted}"
```

2026-07-23 사전 점검 결과는 다음과 같다.

- 원본은 `stopped`, PostgreSQL `16.14`, `db.t4g.micro`, Single-AZ·Private·암호화, Backup 7일이다.
- Automated Backup `RestoreWindow`가 존재하며 복원 가능 구간은 약 115.2시간이었다. 최신 복원 시점 지연은 최초 점검 약 44.1분, Foundation Saved Plan 직전 재점검 약 102분이었다.
- 사용 가능한 Automated Backup 1개와 Snapshot 4개를 확인했다.
- 복원 대상 Identifier는 충돌하지 않았고 DB Instance Quota는 1/40이었다.
- 서울 리전에서 동일 Engine/Class를 주문할 수 있고 Private Data Subnet 2개가 서로 다른 AZ에서 모두 Active였다.

이 값은 점검 시점의 관측값이므로 Restore ON Saved Plan을 만들기 직전에 다시 확인한다.

2026-07-23 실제 Restore에서는 Apply 시작 시각 기준 최신 복원 시점 지연이 약 2시간 54분 14초였다. 복원 DB는 PostgreSQL `16.14`, `db.t4g.micro`, 암호화, Private, Single-AZ, 20 GiB gp3와 전용 Security Group 하나로 생성됐고 원본 RDS는 `stopped`를 유지했다.

## 6. Restore 훈련 원칙

복원은 원본 RDS를 덮어쓰지 않고 Terraform이 추적하는 새 Identifier로 수행한다. 상세 계약과 승인 Gate는 [AWS RDS Backup Restore 계획](../plans/2026-07-23-backup-restore-plan.md)을 따른다.

1. 가장 최근의 복원 가능 시각을 기록한다.
2. `modules/rds-restore-drill`의 별도 Identifier와 `restore_to_point_in_time`으로 PITR 복원을 요청한다.
3. 기존 DB Subnet Group은 재사용하되 복원 DB와 Validator에 각각 전용 Security Group을 연결하고 Public 접근을 차단한다.
4. 기존 Application·Data Security Group, Cloud Map, ALB, Route 53, SSM Runtime Parameter와 Application Secret에는 복원 DB를 연결하거나 게시하지 않는다.
5. Private App Subnet의 임시 Fargate Validator만 5432로 접근해 고정 SQL을 읽기 전용 Transaction에서 실행한다.
6. Validator는 Read-only Root, UID `70`, Linux Capability `ALL` 제거를 사용한다. Fargate가 지원하지 않는 `tmpfs`는 사용하지 않는다.
7. PostgreSQL PITR은 복원 시 새 RDS Managed Master Secret을 만들 수 없으므로 RDS 소유(`OwningService=rds`) 원본 Managed Master Secret을 읽기 전용으로 재사용한다. 새 Secret Version은 만들지 않는다.
8. Schema·Role·Flyway·Application Table 5개와 활성 관리자 1명만 검증하고 PII·행 내용·Secret은 출력하지 않는다.
9. 복원 성공 시간과 검증 결과를 관측값으로 기록한다. 측정 전에는 RTO/RPO 보장값을 쓰지 않는다.
10. 성공·실패와 관계없이 복원 DB를 먼저 정지하고, 임시 리소스 삭제는 별도 Cleanup Saved Plan 승인 뒤 실행한다.

PITR Restore API는 원본의 Backup Retention 7일을 복원본에 상속한다. Terraform은 실상태와 State의 불필요한 drift를 피하도록 복원 DB도 7일로 추적하며, Cleanup 시 `delete_automated_backups=true`와 Final Snapshot 생략 계약으로 임시 백업을 함께 제거한다.

추적되지 않는 RDS를 만드는 임의 CLI 복원은 실행하지 않는다. 2026-07-23 가격 조회 기준 동일 Class의 Instance와 20 GiB gp3를 2시간 사용할 때 DB·Storage 예상 비용은 약 USD 0.0572이며 Fargate·Log·Backup·전송·세금은 별도다. 목표 실행 시간은 90분 이내이고 실행 상태를 2시간 넘기지 않는다.

2026-07-23 실행 기록:

- 승인 Plan SHA-256 `77e48d5d8a37c5d5495b8478b68a0b8c8fd6de124495525a1022afbec5a2feb7`을 재검증하고 `11 added, 0 changed, 0 destroyed`로 적용했다.
- 적용 완료 후 같은 SHA-256을 다시 확인하고 Saved Plan 파일을 삭제했다.
- Restore Apply `13:57:00.557 KST`부터 Validator 성공 `14:25:35.801 KST`까지 관측 RTO는 약 28분 35초다.
- Fargate Validator는 Private Subnet·Public IP 없음으로 실행됐고 Exit Code `0`이었다.
- 결과는 Schema 3, Application Role 3, Application Table 5, Flyway V1 3, 실패 Migration 0, 활성 관리자 1이다.
- 결과 Fingerprint는 `9c0f611a1bcd68ab83d7dd918e0bef4264e6d005aa471612bfe767d61cac872e`이며 PII·행 내용·Secret은 출력하지 않았다.
- Validator 종료 23초 뒤 정지를 요청했고 복원 DB는 `14:34:21.436 KST`에 `stopped`가 됐다.
- 원본 RDS도 `stopped`, ECS 8개 Service는 `0/0/0`, 실행 Task는 0이다.
- 복원 DB의 Backup Retention은 원본에서 상속된 7일이며 Terraform State도 7일로 수렴했다.
- 감사 Log를 포함한 Restore Drill Terraform 주소는 12개다. 임시 11개 리소스는 별도 Cleanup Saved Plan 승인 전까지 유지한다.

## 7. Flyway 실패 대응

- 일회성 Migration Task가 실패하면 Application Service 배포를 중단한다.
- 이미 성공한 Flyway 행이나 DB 객체를 수동 삭제해 History를 맞추지 않는다.
- 비파괴 오류는 수정한 새 Version Migration으로 Forward Fix한다.
- 일부 DDL만 적용된 실패는 Transaction 적용 여부와 Flyway 상태를 확인한 뒤 복구 SQL을 새 Migration으로 작성한다.
- 데이터 손실 가능성이 있는 변경은 먼저 별도 복원 RDS에서 검증한다.
- 원본 복원이 필요한 경우 새 RDS로 복원·검증한 뒤 Endpoint 전환을 별도 승인한다. 원본을 즉시 삭제하지 않는다.

## 8. 현재 단계와 남은 작업

- 완료: Terraform Restore Drill Module·Validator·계약 테스트 구현, 전체 `38 passed, 0 failed`와 Validator `sh -n` 문법 검사 통과
- 완료: 감사 Foundation OFF Plan SHA-256 `6aae05fc1761745e1d217a1348ce74138f3be901369cde1111832cf73e4ae188` 적용, `1 added, 0 changed, 0 destroyed`
- 완료: 감사 Log `STANDARD`·보존 7일·태그, 원본 RDS `stopped`, 임시 Restore 리소스 0, 동일 입력 `No changes`, 적용 Plan 삭제
- 완료: RestoreWindow 약 115.2시간·최신 시점 지연 약 147.8분, Quota `1/40`, Identifier·Orderability·Subnet·Secret·NAT·Runtime OFF 재점검
- 완료: Restore ON Saved Plan 225,896 bytes, SHA-256 `77e48d5d8a37c5d5495b8478b68a0b8c8fd6de124495525a1022afbec5a2feb7`, `11 add, 0 change, 0 destroy`
- 완료: Apply 직전 만료 Gate·Hash·RestoreWindow·대상 Identifier·원본 OFF 재검증
- 완료: 실제 격리 PITR Restore, Fargate 읽기 전용 검증 Exit Code `0`, 관측 RTO 약 28분 35초·RPO 지연 약 2시간 54분 14초 기록
- 완료: 복원 DB 즉시 정지, 원본·복원 RDS `stopped`, ECS Service 8개 `0/0/0`, 실행 Task 0 확인
- 완료: Cleanup Saved Plan 242,611 bytes, SHA-256 `f01c5588a23810ae038f6e12128c9bd51181179e89616aa4553f4c95c22bc875`, `0 add, 0 change, 11 destroy` 검증
- 완료: 승인된 Cleanup Plan `0 added, 0 changed, 11 destroyed` 적용, 복원 RDS·SG·IAM·활성 Task Definition·Automated Backup 0 확인
- 완료: 감사 Log 보존 7일, State 249개·Restore Drill 주소 1개, 동일 Cleanup 입력 `No changes`, 적용 Plan 삭제
- 완료: 원본 RDS를 사용하는 최종 전체 Runtime Smoke 후 Runtime OFF·RDS 정지

## 9. FreeableMemory와 Hikari Pool 판정

2026-07-23 마지막 Runtime ON 구간에서 `FreeableMemory`는 평균 172.56MiB·최소 145.04MiB, `SwapUsage`는 최대 0.98MiB, CPU는 평균 5.68%였다. 세 DB 서비스가 시작된 뒤 `DatabaseConnections=30`이 고정됐으므로 즉시 Class를 올리거나 256MiB Alarm을 낮추기 전에 AWS Runtime의 Hikari Pool을 서비스별 `maximumPoolSize=5`, `minimumIdle=1`로 줄인다.

Pool Foundation은 RDS와 Runtime이 OFF인 상태에서 먼저 적용했다. 검증된 Foundation OFF Plan SHA-256 `56fabf74af8b2f50bf19ca5c1c6200246ddb9855715cc3257a3298d4940b3f87`을 정확히 `3 added, 3 changed, 3 destroyed`로 적용했고 State serial 108, 세 Task Definition Hikari `5/1`, ECS·ASG 0, RDS `stopped`, 동일 OFF 입력 `No changes`를 확인했다. 적용 Plan은 Hash 재검증 뒤 삭제했다.

재측정 Runtime ON Saved Plan SHA-256 `fa6a9c0d9c3facaa4611c4684e6f96bfcfad3a85f8580b0abbdf7a5a1c50e124`을 정확히 `40 added, 11 changed, 0 destroyed`로 적용했다. `01:19~01:48 KST` 30개 1분 지표에서 DatabaseConnections 평균 3.87·최대 6·안정 구간 3, FreeableMemory 평균 197.09MiB·최소 190.14MiB, Swap 최대 0.45MiB, CPU 평균 4.07%였다. 세 DB 서비스 Hikari 시작 완료와 timeout/pool 오류 0건, Stock Pending 0, 전체 인증·REST Smoke를 확인했다. State serial 113, 동일 ON 입력은 `No changes`다.

Pool 교정은 성공했지만 FreeableMemory 30/30점이 256MiB 아래여서 Alarm은 실제 `ALARM`이다. Alarm만 낮추지 않고 `db.t4g.small`과 기준선 기반 Alarm 재설계를 별도 결정으로 함께 검토한다. Member BFF `/actuator/prometheus`의 500 `INTERNAL_SERVER_ERROR`도 별도 진단 대상으로 남긴다. 먼저 별도 승인으로 Runtime OFF·RDS 정지를 완료하며, 상세 실측과 비용 Gate는 [RDS 메모리·연결 풀 교정 계획](../plans/2026-07-23-rds-memory-plan.md)을 따른다.
