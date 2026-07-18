# AWS Learning RDS 운영과 복구 Runbook

> 기준일: 2026-07-18
>
> 현재 상태: Terraform/Flyway 구현과 RDS/Secrets Apply 검증 완료, RDS는 비용 통제를 위해 정지
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

최초 Apply에서는 위 항목, Managed Master Secret `active`, Application Secret 7개의 Version 0개, 재계획 `No changes`를 모두 확인했다. 첫 Automated Backup 완료와 `LatestRestorableTime`도 확인했다. 실제 PITR Restore 훈련과 `EarliestRestorableTime` 검증은 RDS를 다시 시작한 후 수행한다.

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

최초 Automated Backup이 생성되기 전에는 PITR 가능 구간이 충분하지 않을 수 있다. 다음 값이 채워졌는지 확인한 뒤 복구 훈련을 예약한다.

```powershell
$dbId = "spring-react-msa-learning-postgres"

aws rds describe-db-instances `
  --region ap-northeast-2 `
  --db-instance-identifier $dbId `
  --query "DBInstances[0].{Status:DBInstanceStatus,Earliest:EarliestRestorableTime,Latest:LatestRestorableTime,BackupWindow:PreferredBackupWindow,BackupDays:BackupRetentionPeriod}"
```

`EarliestRestorableTime`과 `LatestRestorableTime`이 모두 존재해야 PITR 복원 훈련으로 넘어간다.

## 6. Restore 훈련 원칙

복원은 원본 RDS를 덮어쓰지 않고 새 Identifier로 수행한다.

1. 가장 최근의 복원 가능 시각을 기록한다.
2. `spring-react-msa-learning-postgres-restore-test` 같은 별도 Identifier로 PITR 복원을 요청한다.
3. 기존 DB Subnet Group과 Data Security Group을 사용하고 Public 접근은 계속 차단한다.
4. Private App 위치의 임시 검증 Task에서 Schema, Flyway History, 핵심 행 수를 읽기 전용으로 확인한다.
5. 복원 성공 시간과 검증 결과를 기록한다. 측정 전에는 RTO/RPO 보장값을 쓰지 않는다.
6. 임시 RDS 삭제와 Final Snapshot 처리 방식은 별도 명시적 승인을 받은 뒤 실행한다.

CLI 복원 명령은 시각, Subnet Group, Security Group과 삭제 정책이 확정된 훈련 Plan에서 생성한다. 이 Runbook의 예시를 그대로 실행해 추적되지 않는 RDS를 만들지 않는다.

## 7. Flyway 실패 대응

- 일회성 Migration Task가 실패하면 Application Service 배포를 중단한다.
- 이미 성공한 Flyway 행이나 DB 객체를 수동 삭제해 History를 맞추지 않는다.
- 비파괴 오류는 수정한 새 Version Migration으로 Forward Fix한다.
- 일부 DDL만 적용된 실패는 Transaction 적용 여부와 Flyway 상태를 확인한 뒤 복구 SQL을 새 Migration으로 작성한다.
- 데이터 손실 가능성이 있는 변경은 먼저 별도 복원 RDS에서 검증한다.
- 원본 복원이 필요한 경우 새 RDS로 복원·검증한 뒤 Endpoint 전환을 별도 승인한다. 원본을 즉시 삭제하지 않는다.

## 8. 아직 남은 구현

- Private App Subnet에서 실행하고 종료되는 서비스별 ECS Migration Task
- Migration 성공을 Application 배포 조건으로 사용하는 ECS 배포 Gate
- 실제 PITR Restore 훈련과 측정 결과
- RDS 자동 재시작 감시와 Learning OFF 자동화
