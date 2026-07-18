# AWS Migration Service Inventory

> 문서 상태: 현재 저장소 기준 인벤토리
>
> 기준일: 2026-07-18
>
> AWS 적용 상태: ECS Compute Foundation 적용·ASG `0/0/0`, ECS workload 미구현

이 문서는 AWS에서 달라지는 배치와 환경 변수만 관리한다. 서비스 책임과 현재 요청 흐름의 기준 문서는 [MSA 구성](../architecture/msa-structure.md), [인증 흐름](../architecture/authentication-flow.md), [회원 서비스 스펙](../specs/member-service.md), [관리자 서비스 스펙](../specs/admin-service.md)이다.

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
| `spring-user-service` | 8081 | Yes | No | No | No | JPA/PostgreSQL dependency; Redis dependency and runtime injection are not present |
| `spring-member-community-service` | 8083 | No | No | No | No | Resource server only; no JPA/PostgreSQL/Redis dependency is present |
| `spring-member-stock-service` | 8084 | Yes | Yes | No | No | JPA/PostgreSQL, Redis, Toss API dependencies |

## Request Flow

- Member: browser → `spring-member-gateway` → `spring-member-bff-service` → `spring-user-service`, `spring-member-community-service`, `spring-member-stock-service`, `spring-security-authorization-server`.
- Admin: browser → `spring-admin-gateway` → `spring-admin-bff-service` → `spring-user-service`, `spring-security-authorization-server`.
- OAuth: BFF services start authorization through the public gateway origin, then call token/userinfo/JWK endpoints through gateway or the authorization server service URL.

## General Environment Variables

- Common: `SPRING_PROFILES_ACTIVE`, `JAVA_TOOL_OPTIONS`
- Database: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `SPRING_SQL_INIT_MODE`, `SPRING_FLYWAY_ENABLED`, `SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA`, `SPRING_FLYWAY_DEFAULT_SCHEMA`, `SPRING_FLYWAY_SCHEMAS`
- Redis: `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`
- OAuth client identity: `BFF_CLIENT_ID`, `ADMIN_BFF_CLIENT_ID`
- OAuth/public URLs: `AUTH_SERVER_ISSUER`, `USER_FRONTEND_LOGIN_URI`, `ADMIN_FRONTEND_LOGIN_URI`, `BFF_FRONTEND_REDIRECT_URI`, `BFF_OAUTH2_AUTHORIZATION_URI`, `BFF_OAUTH2_TOKEN_URI`, `BFF_OAUTH2_USERINFO_URI`, `BFF_OAUTH2_JWK_SET_URI`, `BFF_OAUTH2_REDIRECT_URI`, `BFF_POST_LOGOUT_REDIRECT_URI`, `BFF_OAUTH2_END_SESSION_URI`, `BFF_OAUTH2_LOGOUT_URI`, `ADMIN_BFF_FRONTEND_REDIRECT_URI`, `ADMIN_BFF_OAUTH2_AUTHORIZATION_URI`, `ADMIN_BFF_OAUTH2_TOKEN_URI`, `ADMIN_BFF_OAUTH2_USERINFO_URI`, `ADMIN_BFF_OAUTH2_JWK_SET_URI`, `ADMIN_BFF_OAUTH2_REDIRECT_URI`, `ADMIN_BFF_POST_LOGOUT_REDIRECT_URI`, `ADMIN_BFF_POST_LOGOUT_LOGIN_REDIRECT_URI`, `ADMIN_BFF_OAUTH2_END_SESSION_URI`, `ADMIN_BFF_OAUTH2_LOGOUT_URI`
- Internal service URLs: `USER_SERVICE_BASE_URL`, `BFF_API_USER_API_BASE_URL`, `BFF_API_USER_INTERNAL_BASE_URL`, `BFF_API_COMMUNITY_API_BASE_URL`, `BFF_API_STOCK_API_BASE_URL`, `ADMIN_BFF_API_USER_API_BASE_URL`, `ADMIN_BFF_API_USER_INTERNAL_BASE_URL`
- Resource Server JWT: `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`
- Gateway routing/CORS: `PUBLIC_ORIGIN`, `GATEWAY_CORS_ALLOWED_ORIGIN`, `ADMIN_GATEWAY_CORS_ALLOWED_ORIGIN`, `GATEWAY_BFF_URI`, `GATEWAY_USER_SERVICE_URI`, `GATEWAY_COMMUNITY_SERVICE_URI`, `GATEWAY_STOCK_SERVICE_URI`, `GATEWAY_AUTHORIZATION_SERVER_URI`, `ADMIN_GATEWAY_ADMIN_BFF_URI`, `ADMIN_GATEWAY_AUTHORIZATION_SERVER_URI`
- Kafka optional switch: `APP_KAFKA_ENABLED`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- External API: `TOSS_API_BASE_URL`, `TOSS_API_CLIENT_ID`

## Secret Environment Variables

- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_PASSWORD`
- `SPRING_MSA_INTERNAL_API_TOKEN`
- `BFF_CLIENT_SECRET`
- `BFF_CLIENT_SECRET_HASH`
- `ADMIN_BFF_CLIENT_SECRET`
- `ADMIN_BFF_CLIENT_SECRET_HASH`
- `TOSS_API_CLIENT_SECRET`

## Environment Separation

- Local Docker Compose values live in `infra/docker/.env.local`; only `infra/docker/.env.example` is tracked.
- Git에서 제외되는 서비스별 `application-local.yml`도 실제 자격 증명을 직접 기록하지 않고 환경 변수 placeholder만 사용한다.
- Local Kubernetes manifests live in `infra/k8s/spring-msa` and intentionally use `localtest.me`.
- AWS ECS의 비밀이 아닌 값은 Task Definition 환경 변수 또는 SSM Parameter Store `String`, 비밀값은 Secrets Manager로 주입한다. SSM SecureString은 사용하지 않는다.
- Java code and `application-prod.yml` must not contain concrete AWS public URLs.
- Kubernetes↔AWS DR은 Learning 적용 범위에서 제외했다. 기존 [재해 복구 아키텍처](../architecture/disaster-recovery.md)는 후속 운영 환경 참고 설계다.
