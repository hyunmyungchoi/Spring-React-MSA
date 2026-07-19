# AWS Migration Service Inventory

> 문서 상태: 현재 저장소 기준 인벤토리
>
> 기준일: 2026-07-19
>
> AWS 적용 상태: Runtime ON에서 ASG `1/1/2`, Service 8개 `1/1/0`, RDS·Valkey·Public ALB와 Health·Digest·Cloud Map 8/8, curl Smoke 6/6 검증 완료; 현재 Runtime OFF로 Service/ASG 0, ALB·Valkey 삭제, RDS 정지. Frontend Hosting Foundation은 Apply·검증 완료, 첫 배포 대기

이 문서는 AWS에서 달라지는 배치와 환경 변수만 관리한다. 서비스 책임과 현재 요청 흐름의 기준 문서는 [MSA 구성](../architecture/msa-structure.md), [인증 흐름](../architecture/authentication-flow.md), [회원 서비스 스펙](../specs/member-service.md), [관리자 서비스 스펙](../specs/admin-service.md)이다.

Root domain: `hyuncloudlab.com`

Recommended public endpoints:

- Member web: `https://app.hyuncloudlab.com`
- Admin web: `https://admin.hyuncloudlab.com`

## Frontend Deployment Inventory

| 배포 단위 | Build | AWS 정적 Origin | 공개 경로 |
| --- | --- | --- | --- |
| `spring-member-web` | Member `build:prod` | Member 전용 Private S3 | Member 기본 경로 |
| `spring-community-web` | Member `build:community` | Community 전용 Private S3 | `/community/*` |
| `spring-stock-web` | Member `build:stock` | Stock 전용 Private S3 | `/stock/*` |
| `spring-admin-web` | Admin `build:prod` | Admin 전용 Private S3 | Admin 기본 경로 |
| `spring-admin-users-web` | Admin `build:users` | Admin Users 전용 Private S3 | `/manage/users/*` |
| `spring-admin-logs-web` | Admin `build:logs` | Admin Logs 전용 Private S3 | `/manage/logs/*` |

Member와 Admin은 각각 CloudFront Distribution 한 개를 공유하지만 Bucket과 배포 작업은 기능 단위로 분리한다. Stock 단독 배포는 `build:stock`, Stock Bucket 동기화와 `/stock`, `/stock/*` Invalidation만 수행한다. Root Domain Redirect, Custom Domain, TLS, API/OAuth/WebSocket ALB Origin은 다음 ACM·Route 53 단계에서 연결한다.

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
- Redis: `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_SSL_ENABLED`
- OAuth client identity: `BFF_CLIENT_ID`, `ADMIN_BFF_CLIENT_ID`
- OAuth/public URLs: `AUTH_SERVER_ISSUER`, `USER_FRONTEND_LOGIN_URI`, `ADMIN_FRONTEND_LOGIN_URI`, `BFF_FRONTEND_REDIRECT_URI`, `BFF_OAUTH2_AUTHORIZATION_URI`, `BFF_OAUTH2_TOKEN_URI`, `BFF_OAUTH2_USERINFO_URI`, `BFF_OAUTH2_JWK_SET_URI`, `BFF_OAUTH2_REDIRECT_URI`, `BFF_POST_LOGOUT_REDIRECT_URI`, `BFF_OAUTH2_END_SESSION_URI`, `BFF_OAUTH2_LOGOUT_URI`, `ADMIN_BFF_FRONTEND_REDIRECT_URI`, `ADMIN_BFF_OAUTH2_AUTHORIZATION_URI`, `ADMIN_BFF_OAUTH2_TOKEN_URI`, `ADMIN_BFF_OAUTH2_USERINFO_URI`, `ADMIN_BFF_OAUTH2_JWK_SET_URI`, `ADMIN_BFF_OAUTH2_REDIRECT_URI`, `ADMIN_BFF_POST_LOGOUT_REDIRECT_URI`, `ADMIN_BFF_POST_LOGOUT_LOGIN_REDIRECT_URI`, `ADMIN_BFF_OAUTH2_END_SESSION_URI`, `ADMIN_BFF_OAUTH2_LOGOUT_URI`
- Internal service URLs: `USER_SERVICE_BASE_URL`, `BFF_API_USER_API_BASE_URL`, `BFF_API_USER_INTERNAL_BASE_URL`, `BFF_API_COMMUNITY_API_BASE_URL`, `BFF_API_STOCK_API_BASE_URL`, `ADMIN_BFF_API_USER_API_BASE_URL`, `ADMIN_BFF_API_USER_INTERNAL_BASE_URL`
- Resource Server JWT: `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`
- Gateway routing/CORS: `PUBLIC_ORIGIN`, `GATEWAY_CORS_ALLOWED_ORIGIN`, `ADMIN_GATEWAY_CORS_ALLOWED_ORIGIN`, `GATEWAY_BFF_URI`, `GATEWAY_USER_SERVICE_URI`, `GATEWAY_COMMUNITY_SERVICE_URI`, `GATEWAY_STOCK_SERVICE_URI`, `GATEWAY_AUTHORIZATION_SERVER_URI`, `ADMIN_GATEWAY_ADMIN_BFF_URI`, `ADMIN_GATEWAY_AUTHORIZATION_SERVER_URI`
- Kafka optional switch: `APP_KAFKA_ENABLED`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- External API: `TOSS_API_BASE_URL`, `TOSS_API_CLIENT_ID`
- Admin 가입 경계: `ADMIN_BFF_REGISTRATION_ENABLED`

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
- AWS Application Task Definition은 OCI Digest로 고정하고 서비스별 최소 권한 Execution Role만 사용한다. Runtime OFF에서는 8개 ECS Service를 `desired_count=0`으로 유지한다.
- 내부 통신은 `awsvpcTrunking`을 활성화한 `awsvpc` Task와 Cloud Map Private DNS `learning.spring-react-msa.internal`을 사용한다. Application Foundation 적용 후 Cloud Map Service 8개의 ECS 관리형 custom health `failure_threshold=1`을 실제 AWS에서 확인했다.
- Java code and `application-prod.yml` must not contain concrete AWS public URLs.
- Kubernetes↔AWS DR은 Learning 적용 범위에서 제외했다. 기존 [재해 복구 아키텍처](../architecture/disaster-recovery.md)는 후속 운영 환경 참고 설계다.
