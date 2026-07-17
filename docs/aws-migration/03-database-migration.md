# Database Migration Readiness

> 문서 상태: RDS 전환 준비도와 미해결 gap
>
> 기준일: 2026-07-17
>
> AWS 적용 상태: RDS와 production migration 미구현

테이블 소유권과 현재 제약은 [시스템 개요](../architecture/overview.md), [회원 서비스 스펙](../specs/member-service.md), [주식 서비스 스펙](../specs/stock-service.md), [실시간 채팅 스펙](../specs/realtime-chat.md)을 기준으로 한다.

## Current Schema Creation

- `spring-user-service` has `BackEnd/spring-user-service/src/main/resources/schema.sql`.
- That file contains both schema creation and local seed data.
- `spring-member-bff-service` uses JPA/PostgreSQL for chat persistence.
- `spring-member-stock-service` uses JPA/PostgreSQL for watchlist persistence.
- Flyway and Liquibase are intentionally not introduced in this readiness pass.

## Production Policy

Production uses:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.sql.init.mode=never
```

`ddl-auto=validate` prevents Hibernate from changing production schemas. `spring.sql.init.mode=never` prevents startup SQL or seed data from mutating production data.

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

## Before AWS RDS

- Decide whether AWS RDS uses one shared schema or separate schemas/databases per service.
- Export a reviewed schema from the verified local database or reconstruct DDL from reviewed entities.
- Keep seed/test users out of production schema scripts.
- Apply schema manually or through a later migration-tool task before running services with `ddl-auto=validate`.
- Define backup and rollback procedure before the first RDS deployment.

## Current Gap Alignment

- Community Service는 아직 메모리 저장 방식이므로 migration 대상 table이 없다.
- Chat Outbox는 [ADR-003](../decisions/ADR-003-kafka-outbox-chat.md)의 목표이며 현재 schema에는 없다.
- Flyway/Liquibase와 versioned production migration pipeline이 없다.
- Kubernetes↔AWS DR용 PostgreSQL 복제, promotion, write fencing과 failback 절차가 없다.

RDS를 도입할 때 단순 schema 이관과 재해 복구 복제를 하나의 작업으로 취급하지 않는다. 먼저 versioned migration과 backup/restore를 확립한 뒤 [DR 계획](../plans/2026-07-17-k8s-aws-dr-plan.md)에 따라 복제와 승격을 검증한다.
