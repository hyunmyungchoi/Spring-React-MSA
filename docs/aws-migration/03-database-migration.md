# Database Migration Readiness

> 문서 상태: RDS Terraform·DB Bootstrap·Build Once/Promote·Flyway V1 실제 실행 및 검증 완료
>
> 기준일: 2026-07-19
>
> AWS 적용 상태: DB Secret 3개와 Role·Schema Bootstrap, Digest 고정 Migration Task 3개, 실제 Flyway V1·Runtime ON 사후 검증 완료; 현재 RDS `stopped`, 자동 재시작 예정 2026-07-26 05:15:28 KST, 재계획 `No changes`

테이블 소유권과 현재 제약은 [시스템 개요](../architecture/overview.md), [회원 서비스 스펙](../specs/member-service.md), [주식 서비스 스펙](../specs/stock-service.md), [실시간 채팅 스펙](../specs/realtime-chat.md)을 기준으로 한다.

## Current Schema Creation

- `spring-user-service` has `BackEnd/spring-user-service/src/main/resources/schema.sql`.
- That file contains both schema creation and local seed data.
- `spring-member-bff-service` uses JPA/PostgreSQL for chat persistence.
- `spring-member-stock-service` uses JPA/PostgreSQL for watchlist persistence.
- 세 데이터 소유 서비스에 Flyway가 도입됐고, 기존 `schema.sql`은 로컬 실행과 Seed Data 용도로만 유지한다.

## AWS Learning 결정

- PostgreSQL 16 RDS Instance 1개와 `spring_msa` Database 1개를 사용한다.
- RDS Instance는 비용 절감을 위해 공유하되 서비스별 Schema와 DB 사용자로 데이터 소유권을 분리한다. Schema 자체는 Bootstrap 관리 계정이 유지하고 서비스 Role에는 자기 Schema의 `USAGE`, `CREATE`만 부여하며, 각 서비스가 생성한 Table은 해당 Role이 소유한다.
- Schema 변경 도구는 Liquibase가 아니라 Flyway SQL Migration을 사용한다.
- RDS Master 계정은 Bootstrap과 관리에만 사용하고 Application은 최소 권한 서비스 계정을 사용한다.
- Runtime OFF 때 RDS를 삭제하지 않고 정지한다.
- Automated Backup 7일, PITR와 의도적인 삭제 전 Final Snapshot을 적용한다.

| Service | Schema | 현재 소유 Table | 접근 원칙 |
|---|---|---|---|
| `spring-user-service` | `user_service` | `users`, `user_roles` | 자기 Schema만 접근 |
| `spring-member-bff-service` | `member_bff` | `chat_rooms`, `chat_messages` | 자기 Schema만 접근 |
| `spring-member-stock-service` | `stock_service` | `stock_watch_items` | 자기 Schema만 접근 |
| `spring-member-community-service` | 생성하지 않음 | 현재 메모리 저장 | 영속화 구현 후 결정 |

서비스는 다른 Schema의 Table을 직접 조회하지 않는다. 각 서비스는 독립된 Migration 경로와 `flyway_schema_history`를 유지한다.

## AWS Runtime Policy

AWS Learning의 ECS Application은 Spring `prod` Profile에서 다음 값을 사용한다. 여기서 `prod`는 설정 Profile 이름이며 이 Learning 환경을 실제 운영 환경이라고 부르는 의미가 아니다.

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.sql.init.mode=never
```

`ddl-auto=validate`는 Hibernate가 AWS Learning Schema를 변경하지 못하게 하고, `spring.sql.init.mode=never`는 시작 SQL이나 Seed Data가 AWS Learning 데이터를 변경하지 못하게 한다.

Flyway는 Application 시작 시 각 Service가 경쟁적으로 실행하는 방식보다 일회성 ECS Migration Task로 먼저 실행한다. Migration이 성공하고 Schema Version이 확인된 후 Application Service를 배포한다. 테스트 계정과 Seed Data는 AWS Learning Migration에 포함하지 않는다.

현재 Docker와 Kubernetes Application 실행에서는 `SPRING_FLYWAY_ENABLED=false`를 명시했다. ECS 일회성 Task는 전체 Spring Context를 시작하지 않고 같은 Application Image의 서비스별 `FlywayMigrationMain`을 직접 실행한다. 일반 Application Task는 계속 Flyway 자동 실행을 끈다.

## Implemented Migration Boundary

다음 Flyway V1 Migration과 PostgreSQL 16 Testcontainers 검증을 추가했다.

| Service | Migration | 검증 내용 |
|---|---|---|
| `spring-user-service` | `V1__create_user_service_tables.sql` | `users`, `user_roles`, 독립 History 생성과 Seed 계정 0건 |
| `spring-member-bff-service` | `V1__create_member_bff_tables.sql` | `chat_rooms`, `chat_messages`, Index와 독립 History 생성 |
| `spring-member-stock-service` | `V1__create_stock_service_tables.sql` | `stock_watch_items`, Unique Constraint와 독립 History 생성 |

Migration SQL에는 Schema 생성문을 넣지 않는다. Bootstrap 단계가 Schema와 최소 권한 DB 사용자를 먼저 만들고, 각 Flyway 실행은 자신의 `default-schema`와 `schemas`만 지정한다.

`modules/database-tasks`에는 다음 실행 기반을 구현했다.

- RDS Managed Master Secret을 Bootstrap Task에만 주입해 세 DB Role과 Schema를 반복 가능하게 생성·동기화한다.
- 서비스 Secret은 `db_username`, `db_password` JSON Key만 참조하며 실제 값은 Terraform 밖에서 최초 생성한다.
- Bootstrap과 Migration Task는 Private App Subnet, Public IP 없음, TLS 필수, 읽기 전용 Root Filesystem을 사용한다.
- Task Execution Role은 필요한 Secret ARN, Log Group, 해당 ECR Repository 읽기만 허용한다.
- Flyway Source를 GHCR에서 서비스별 한 번만 Build하고 동일 OCI Digest로 ECR에 Promote한 뒤, `database_migration_images` 세 Key를 모두 Digest Reference로 고정한다.

검토한 `tfplan-database-tasks-foundation`을 SHA-256 승인 후 Apply해 CloudWatch Log Group, Bootstrap Task Execution Role과 최소 권한 Inline Policy, ECS Task Definition 등 4개를 추가했다. 기존 리소스 변경·삭제는 없었고 AWS에서 Task Definition `ACTIVE`, Log 보존 7일, Secret 읽기 전용 권한을 확인했다. Foundation Apply 시점에는 RDS와 ECS 실행 용량이 OFF였고 세 Application Secret도 Version 0개였다.

별도 SHA-256 승인으로 User Service, Member BFF, Stock Service Secret에 최초 Version을 생성했다. 각 Secret은 승인된 `db_username`과 AWS가 생성한 40자 `db_password`만 가지며 화면·Git·Terraform State에는 값을 남기지 않았다. 세 Secret 모두 `AWSCURRENT` 1개를 확인했고 초기화 스크립트를 재실행했을 때 기존 정상 Version을 덮어쓰지 않고 모두 Skip했다.

짧은 Runtime ON에서 Bootstrap을 실제 실행했다. 최초 Revision 1은 일반 PostgreSQL SUPERUSER를 전제로 한 `NOSUPERUSER` 설정과 Schema 소유자 전환 때문에 RDS 제한 계정에서 실패했다. RDS 호환 방식으로 Role의 LOGIN·비밀번호만 동기화하고 Schema는 Bootstrap 관리 계정이 소유하도록 수정한 Revision 2는 Exit Code `0`으로 완료됐다.

Bootstrap 직후 읽기 전용 검증 Task도 Exit Code `0`으로 끝났으며 실제 RDS에서 안전한 Role 3개, Schema 3개, 자기 Schema 권한 조합 3개, 교차 Schema 권한 0개, Application Table 0개를 확인했다. 따라서 Role·Schema Bootstrap, Private App에서 Private Data RDS로 이어지는 Security Group 경계와 재실행 가능성을 먼저 검증했다.

Source SHA `f0c88e32b883c391dcf993dfbf40839312de0f39`의 User Service, Member BFF, Stock Service Image를 GHCR에서 한 번만 Build하고 ECR에 재빌드 없이 Promote했다. GHCR과 ECR의 최상위 OCI Digest가 서비스별로 같음을 검증한 뒤, 승인한 저장 Plan으로 Migration Log Group·최소 권한 Execution Role/Policy·Digest 고정 Task Definition을 서비스별 3개씩 총 12개 추가했다.

짧은 Runtime ON에서 User Service → Member BFF → Stock Service 순서로 Flyway V1 Task를 실행했고 모두 Exit Code `0`으로 완료됐다. 별도 읽기 전용 검증 Task로 `flyway_schema_history` 3개, 성공 V1 이력 3개, Application Table 5개, 올바른 Table 소유자 5개, 실패 Migration 0개, 교차 Schema 권한 0개와 Seed/Application Row 0건을 실제 RDS에서 확인했다.

검증 후 승인된 OFF Plan을 Apply해 ECS ASG를 `0/0/0`으로 내리고 RDS를 정지했다. EC2·ECS Container Instance·실행/대기 Task는 0개, RDS는 `stopped`, Remote State는 107개 주소이며 재계획은 `No changes`다. AWS가 표시한 자동 재시작 예정 시각은 2026-07-25 22:34:06 KST이다.

RDS Terraform은 서울 리전에서 지원을 확인한 PostgreSQL `16.14`, `db.t4g.micro`, Single-AZ, 암호화된 고정 20 GiB `gp3`, Private Data Subnet 2개와 Data Security Group을 사용한다. RDS Master 비밀번호는 Terraform 값으로 만들지 않고 RDS Managed Master Secret을 사용한다.

검토한 저장 Plan으로 Terraform 리소스 10개를 Apply했고 Remote State 81개 주소와 재계획 `No changes`를 확인했다. RDS의 Private 접근, 암호화, Backup 7일, 삭제 보호와 Managed Master Secret `active`를 검증했으며 Application Secret 7개는 Version 0개인 빈 Container 상태다. 첫 Backup 완료와 `LatestRestorableTime`은 확인했지만 실제 PITR Restore 훈련은 아직 수행하지 않았다. 검증 뒤 RDS는 비용 통제를 위해 정지했다.

## Verified Local DB Snapshot

Docker Compose PostgreSQL was inspected with `psql` after local services started. The local DB contained:

- `users`
- `user_roles`
- `chat_rooms`
- `chat_messages`
- `stock_watch_items`

`stock_watch_items` was created after Docker profile schema settings were connected to `SPRING_JPA_HIBERNATE_DDL_AUTO` and `SPRING_SQL_INIT_MODE` for `spring-member-stock-service`.

## Migration Ownership

| Service | Tables | Evidence |
|---|---|---|
| `spring-user-service` | `users`, `user_roles` | Existing `schema.sql`, JPA entity/repository |
| `spring-member-bff-service` | `chat_rooms`, `chat_messages` | JPA entities and verified local DB tables |
| `spring-member-stock-service` | `stock_watch_items` | JPA entity/repository and verified local DB table |

## Before AWS Application Deployment

- 완료: 현재 Source를 Build Once·Digest Promote하고 세 Flyway Migration Task Definition을 등록·실행했다.
- 완료: 서비스별 Table과 독립된 `flyway_schema_history`, 소유권과 권한 격리를 실제 RDS에서 검증했다.
- 남은 작업: Automated Backup을 사용해 별도 복원 RDS로 PITR Restore 훈련을 수행한다.

## Current Gap Alignment

- Community Service는 아직 메모리 저장 방식이므로 migration 대상 table이 없다.
- Chat Outbox는 [ADR-003](../decisions/ADR-003-kafka-outbox-chat.md)의 목표이며 현재 schema에는 없다.
- Flyway V1과 Backend Application Task Definition·Service 8개의 Runtime ON 검증까지 완료했지만 이후 Schema 변경을 위한 V2 이상 Migration은 아직 없다.
- Kubernetes↔AWS DR용 PostgreSQL 복제, promotion, write fencing과 failback 절차가 없다.

RDS를 도입할 때 단순 Schema 이관과 재해 복구 복제를 하나의 작업으로 취급하지 않는다. Learning에서는 Flyway와 Backup/Restore까지만 구현하고 Kubernetes↔AWS DR은 후속 학습 과제로 보류한다. 전체 Runtime 경계는 [AWS Learning Runtime 결정](07-learning-runtime-design.md)을 따른다.

RDS Apply 이후의 시작·정지와 Backup/PITR 확인은 [AWS Learning RDS 운영·복구 Runbook](../runbooks/aws-rds-learning.md)을, Role/Schema와 Flyway 실행은 [AWS DB Bootstrap 및 Flyway Runbook](../runbooks/aws-database-bootstrap-and-flyway.md)을 따른다.
