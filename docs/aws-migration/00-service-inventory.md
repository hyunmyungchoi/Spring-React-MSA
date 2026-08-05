# AWS Migration Service Inventory

> Current status: local VM development is the active target. AWS resources and deployment history are reference-only and are not part of the current Kafka/Outbox work.

## Frontend deployment units

| Deployment unit | Workspace | Build command | Public path |
| --- | --- | --- | --- |
| `spring-member-web` | `member` | `pnpm --filter member build:prod` | `/` |
| `spring-member-community-web` | `@springmsa/member-community` | `pnpm --filter @springmsa/member-community build` | `/community/*` |
| `spring-member-stock-web` | `@springmsa/member-stock` | `pnpm --filter @springmsa/member-stock build` | `/stock/*` |
| `spring-admin-web` | `admin` | `pnpm --filter admin build:prod` | admin root |
| `spring-admin-users-web` | `@springmsa/admin-users` | `pnpm --filter @springmsa/admin-users build` | `/manage/users/*` |
| `spring-admin-logs-web` | `@springmsa/admin-logs` | `pnpm --filter @springmsa/admin-logs build` | `/manage/logs/*` |

The six units are independently buildable pnpm workspaces. They remain in one Git repository until separate repository names and remotes are assigned.

## Backend service inventory

| Service | Port | PostgreSQL | Redis | Kafka role | Public |
| --- | ---: | --- | --- | --- | --- |
| `spring-member-gateway` | 8080 | No | No | None | Yes |
| `spring-admin-gateway` | 8090 | No | No | None | Yes |
| `spring-member-bff-service` | 8082 | Yes | Yes | Consumer, retry, DLT | No |
| `spring-admin-bff-service` | 8087 | No | Yes | None | No |
| `spring-security-authorization-server` | 9000 | No | Yes | None | No |
| `spring-user-service` | 8081 | Yes | No | Outbox producer | No |
| `spring-member-community-service` | 8083 | Yes | No | Outbox producer | No |
| `spring-member-stock-service` | 8084 | Yes | Yes | Outbox producer | No |

## Request flow

- Member: browser -> member gateway -> member BFF -> user, community, stock, authorization services.
- Admin: browser -> admin gateway -> admin BFF -> user and authorization services.
- Domain events: user, community, and stock transactions -> service outbox -> Kafka -> member BFF idempotent consumer.
- Failed Kafka records are retried and then routed to the matching DLT.

## Local VM configuration

Local values are stored in the ignored `infra/vm/.env.local`; only `infra/vm/.env.example` is committed.

Important variable groups:

- Database: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- Redis: `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`
- Kafka: `APP_KAFKA_ENABLED`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_ADVERTISED_HOST`
- OAuth: `AUTH_SERVER_ISSUER`, BFF client IDs/secrets, redirect and endpoint variables
- Internal APIs: `SPRING_MSA_INTERNAL_API_TOKEN` and service base URLs
- Toss: `TOSS_API_BASE_URL`, `TOSS_API_CLIENT_ID`, `TOSS_API_CLIENT_SECRET`

Do not commit passwords, client secrets, tokens, or concrete production credentials.

## AWS boundary

AWS manifests remain under `infra/aws` for later work. Production secrets should be injected by AWS-managed secret stores, and Java/YAML source must not contain concrete credentials or account-specific public URLs.