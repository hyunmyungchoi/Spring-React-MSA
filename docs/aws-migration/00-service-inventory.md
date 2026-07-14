# AWS Migration Service Inventory

Root domain: `hyuncloudlab.com`

Recommended public endpoints:

- Member web: `https://app.hyuncloudlab.com`
- Admin web: `https://admin.hyuncloudlab.com`

## Service Inventory

| Service | Port | PostgreSQL | Redis | Kafka | Public | Evidence |
|---|---:|---|---|---|---|---|
| `spring-member-gateway` | 8080 | No | No | No | Yes | Gateway/WebFlux only |
| `spring-admin-gateway` | 8090 | No | No | No | Yes | Gateway/WebFlux only |
| `spring-member-bff-service` | 8079 | Yes | Yes | Optional | No | JPA, PostgreSQL, Redis session, Kafka dependencies |
| `spring-admin-bff-service` | 8087 | No | Yes | No | No | Redis session dependency, no JPA/PostgreSQL dependency |
| `spring-security-authorization-server` | 9000 | No | Yes | No | No | Redis session dependency |
| `spring-user-service` | 8081 | Yes | No | No | No | JPA/PostgreSQL dependency; Redis properties are currently injected but no Redis starter is present |
| `spring-member-community-service` | 8083 | No | No | No | No | Resource server only; no JPA/PostgreSQL/Redis dependency is present |
| `spring-member-stock-service` | 8084 | Yes | Yes | No | No | JPA/PostgreSQL, Redis, Toss API dependencies |

## Request Flow

- Member: browser → `spring-member-gateway` → `spring-member-bff-service` → `spring-user-service`, `spring-member-community-service`, `spring-member-stock-service`, `spring-security-authorization-server`.
- Admin: browser → `spring-admin-gateway` → `spring-admin-bff-service` → `spring-user-service`, `spring-security-authorization-server`.
- OAuth: BFF services start authorization through the public gateway origin, then call token/userinfo/JWK endpoints through gateway or the authorization server service URL.

## General Environment Variables

- Common: `SPRING_PROFILES_ACTIVE`, `JAVA_TOOL_OPTIONS`
- Database: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_SQL_INIT_MODE`
- Redis: `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`
- OAuth/public URLs: `AUTH_SERVER_ISSUER`, `USER_FRONTEND_LOGIN_URI`, `ADMIN_FRONTEND_LOGIN_URI`, `BFF_FRONTEND_REDIRECT_URI`, `BFF_OAUTH2_AUTHORIZATION_URI`, `BFF_OAUTH2_REDIRECT_URI`, `BFF_OAUTH2_END_SESSION_URI`, `BFF_OAUTH2_LOGOUT_URI`, `ADMIN_BFF_FRONTEND_REDIRECT_URI`, `ADMIN_BFF_OAUTH2_AUTHORIZATION_URI`, `ADMIN_BFF_OAUTH2_REDIRECT_URI`, `ADMIN_BFF_OAUTH2_END_SESSION_URI`, `ADMIN_BFF_OAUTH2_LOGOUT_URI`
- Gateway routing/CORS: `GATEWAY_CORS_ALLOWED_ORIGIN`, `ADMIN_GATEWAY_CORS_ALLOWED_ORIGIN`, `GATEWAY_BFF_URI`, `ADMIN_GATEWAY_ADMIN_BFF_URI`
- Kafka optional switch: `APP_KAFKA_ENABLED`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`

## Secret Environment Variables

- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_PASSWORD`
- `SPRING_MSA_INTERNAL_API_TOKEN`
- `BFF_CLIENT_SECRET`
- `BFF_CLIENT_SECRET_HASH`
- `ADMIN_BFF_CLIENT_SECRET`
- `ADMIN_BFF_CLIENT_SECRET_HASH`
- `TOSS_API_CLIENT_ID`
- `TOSS_API_CLIENT_SECRET`

## Environment Separation

- Local Docker Compose values live in `infra/docker/.env.local`; only `infra/docker/.env.example` is tracked.
- Local Kubernetes manifests live in `infra/k8s/spring-msa` and intentionally use `localtest.me`.
- AWS ECS values will be injected through ECS Task Definition environment variables plus SSM Parameter Store or Secrets Manager.
- Java code and `application-prod.yml` must not contain concrete AWS public URLs.
