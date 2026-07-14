# Database Migration Readiness

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
