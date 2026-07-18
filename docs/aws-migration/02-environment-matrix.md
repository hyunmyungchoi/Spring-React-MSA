# Environment Matrix

> 문서 상태: 로컬·Kubernetes·AWS Learning 목표 설정 비교
>
> 기준일: 2026-07-18
>
> AWS 적용 상태: RDS/Secrets와 ECS Compute 적용·검증 완료, RDS 정지·ECS ASG `0/0/0`; ECS Task Definition과 ElastiCache 미적용

인증 URI와 쿠키/CSRF 계약의 기준은 [인증 스펙](../specs/authentication.md)이다. 이 표는 `application.yml`과 `application-prod.yml`에서 실제로 요구하는 환경별 주입 값을 관리하며 Secret의 실제 값은 기록하지 않는다. AWS Secret 경계는 [AWS Learning Runtime 결정](07-learning-runtime-design.md)을 따른다.

AWS root domain: `hyuncloudlab.com`

로컬 Kubernetes가 사용하는 `prod`는 Spring Profile 이름이다. 로컬 Kubernetes를 실제 운영 환경으로 의미하지 않으며, 이 프로젝트가 실제 AWS에 적용하는 대상은 Learning 환경뿐이다.

## Runtime과 Data

| 변수명 | 로컬 Docker | 로컬 Kubernetes | AWS Learning ECS | Secret 여부 |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` | `prod` | `prod` | No |
| `JAVA_TOOL_OPTIONS` | Container JVM 기본값 | Container JVM 기본값 | Task Definition JVM 기본값 | No |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/msa` | `jdbc:postgresql://postgres:5432/msa` | RDS `spring_msa` endpoint와 서비스별 `currentSchema` | No |
| `SPRING_DATASOURCE_USERNAME` | `.env.local` | ConfigMap | 서비스별 RDS 사용자 | No |
| `SPRING_DATASOURCE_PASSWORD` | `.env.local` | Kubernetes Secret | 서비스별 Secrets Manager JSON Key | Yes |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | `validate` | `validate` | No |
| `SPRING_SQL_INIT_MODE` | Local 초기화 정책 | `never` | `never` | No |
| `SPRING_FLYWAY_ENABLED` | `false` | `false` | Application Task `false`, 일회성 Migration Task만 `true` | No |
| `SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA` | 기본 `public` 사용 | `public` | 서비스별 `user_service`, `member_bff`, `stock_service` | No |
| `SPRING_FLYWAY_DEFAULT_SCHEMA` | Flyway 비활성 | `public`, Flyway 비활성 | Migration 대상 서비스 Schema | No |
| `SPRING_FLYWAY_SCHEMAS` | Flyway 비활성 | `public`, Flyway 비활성 | Migration 대상 서비스 Schema 하나 | No |
| `SPRING_DATA_REDIS_HOST` | `redis` | `redis` | ElastiCache endpoint | No |
| `SPRING_DATA_REDIS_PORT` | `6379` | `6379` | ElastiCache port | No |
| `SPRING_DATA_REDIS_PASSWORD` | `.env.local` if enabled | Kubernetes Secret | `/spring-react-msa/learning/shared/redis` | Yes |
| `APP_KAFKA_ENABLED` | `false` by default | `true` | 첫 AWS 전환은 `false` | No |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 사용 시 주소, 비활성 시 빈 문자열 주입 | `kafka.kafka.svc.cluster.local:9092` | 첫 전환에서 Kafka 비활성 시 빈 문자열 주입 | No |

## Public Origin과 Redirect

| 변수명 | 로컬 Docker | 로컬 Kubernetes | AWS Learning ECS |
|---|---|---|---|
| `AUTH_SERVER_ISSUER` | `http://spring-member-gateway:8080` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` |
| `USER_FRONTEND_LOGIN_URI` | `http://localhost:5173/auth` | `http://user.localtest.me/auth` | `https://app.hyuncloudlab.com/auth` |
| `ADMIN_FRONTEND_LOGIN_URI` | `http://localhost:5176/auth` | `http://admin.localtest.me/auth` | `https://admin.hyuncloudlab.com/auth` |
| `BFF_FRONTEND_REDIRECT_URI` | `http://localhost:5173` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` |
| `BFF_OAUTH2_AUTHORIZATION_URI` | `http://localhost:5173/oauth2/authorize` | `http://user.localtest.me/oauth2/authorize` | `https://app.hyuncloudlab.com/oauth2/authorize` |
| `BFF_OAUTH2_REDIRECT_URI` | `http://localhost:5173/bff/login/oauth2/code/member-bff` | `http://user.localtest.me/bff/login/oauth2/code/member-bff` | `https://app.hyuncloudlab.com/bff/login/oauth2/code/member-bff` |
| `BFF_POST_LOGOUT_REDIRECT_URI` | `http://localhost:5173/auth` | `http://user.localtest.me/auth` | `https://app.hyuncloudlab.com/auth` |
| `BFF_OAUTH2_END_SESSION_URI` | `http://localhost:5173/connect/logout` | `http://user.localtest.me/connect/logout` | `https://app.hyuncloudlab.com/connect/logout` |
| `BFF_OAUTH2_LOGOUT_URI` | `http://localhost:5173/logout` | `http://user.localtest.me/logout` | `https://app.hyuncloudlab.com/logout` |
| `ADMIN_BFF_FRONTEND_REDIRECT_URI` | `http://localhost:5176` | `http://admin.localtest.me` | `https://admin.hyuncloudlab.com` |
| `ADMIN_BFF_OAUTH2_AUTHORIZATION_URI` | `http://localhost:5176/oauth2/authorize` | `http://admin.localtest.me/oauth2/authorize` | `https://admin.hyuncloudlab.com/oauth2/authorize` |
| `ADMIN_BFF_OAUTH2_REDIRECT_URI` | `http://localhost:5176/admin-bff/login/oauth2/code/admin-bff` | `http://admin.localtest.me/admin-bff/login/oauth2/code/admin-bff` | `https://admin.hyuncloudlab.com/admin-bff/login/oauth2/code/admin-bff` |
| `ADMIN_BFF_POST_LOGOUT_REDIRECT_URI` | `http://localhost:5176` | `http://admin.localtest.me/auth` | `https://admin.hyuncloudlab.com/auth` |
| `ADMIN_BFF_POST_LOGOUT_LOGIN_REDIRECT_URI` | `http://localhost:5176/auth` | `http://admin.localtest.me/auth` | `https://admin.hyuncloudlab.com/auth` |
| `ADMIN_BFF_OAUTH2_END_SESSION_URI` | `http://localhost:5176/connect/logout` | `http://admin.localtest.me/connect/logout` | `https://admin.hyuncloudlab.com/connect/logout` |
| `ADMIN_BFF_OAUTH2_LOGOUT_URI` | `http://localhost:5176/logout` | `http://admin.localtest.me/logout` | `https://admin.hyuncloudlab.com/logout` |
| `PUBLIC_ORIGIN` | `localhost:5173` | 미사용 | 미사용 |
| `GATEWAY_CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` |
| `ADMIN_GATEWAY_CORS_ALLOWED_ORIGIN` | `http://localhost:5176` | `http://admin.localtest.me` | `https://admin.hyuncloudlab.com` |

## OAuth와 내부 Service URL

AWS의 `<service-discovery-name>`은 승인한 Service Connect 또는 Cloud Map 세부 설계에서 확정한다. 이 값이 확정되기 전에는 ECS Task Definition을 Apply하지 않는다.

| 변수명 | 로컬 Docker | 로컬 Kubernetes | AWS Learning ECS |
|---|---|---|---|
| `BFF_OAUTH2_TOKEN_URI` | `http://spring-member-gateway:8080/oauth2/token` | 동일 | `http://<member-gateway>:8080/oauth2/token` |
| `BFF_OAUTH2_USERINFO_URI` | `http://spring-member-gateway:8080/userinfo` | 동일 | `http://<member-gateway>:8080/userinfo` |
| `BFF_OAUTH2_JWK_SET_URI` | `http://spring-member-gateway:8080/oauth2/jwks` | `http://spring-security-authorization-server:9000/oauth2/jwks` | `http://<authorization-server>:9000/oauth2/jwks` |
| `ADMIN_BFF_OAUTH2_TOKEN_URI` | `http://spring-admin-gateway:8090/oauth2/token` | 동일 | `http://<admin-gateway>:8090/oauth2/token` |
| `ADMIN_BFF_OAUTH2_USERINFO_URI` | `http://spring-admin-gateway:8090/userinfo` | 동일 | `http://<admin-gateway>:8090/userinfo` |
| `ADMIN_BFF_OAUTH2_JWK_SET_URI` | `http://spring-admin-gateway:8090/oauth2/jwks` | `http://spring-security-authorization-server:9000/oauth2/jwks` | `http://<authorization-server>:9000/oauth2/jwks` |
| `USER_SERVICE_BASE_URL` | `http://spring-user-service:8081` | 동일 | `http://<user-service>:8081` |
| `BFF_API_USER_API_BASE_URL` | `http://spring-user-service:8081` | 동일 | `http://<user-service>:8081` |
| `BFF_API_USER_INTERNAL_BASE_URL` | `http://spring-user-service:8081` | 동일 | `http://<user-service>:8081` |
| `BFF_API_COMMUNITY_API_BASE_URL` | `http://spring-member-community-service:8083` | 동일 | `http://<community-service>:8083` |
| `BFF_API_STOCK_API_BASE_URL` | `http://spring-member-stock-service:8084` | 동일 | `http://<stock-service>:8084` |
| `ADMIN_BFF_API_USER_API_BASE_URL` | `http://spring-member-gateway:8080` | `http://spring-user-service:8081` | `http://<user-service>:8081` |
| `ADMIN_BFF_API_USER_INTERNAL_BASE_URL` | `http://spring-user-service:8081` | 동일 | `http://<user-service>:8081` |
| `GATEWAY_BFF_URI` | `http://spring-member-bff-service:8079` | 동일 | `http://<member-bff>:8079` |
| `GATEWAY_USER_SERVICE_URI` | `http://spring-user-service:8081` | 동일 | `http://<user-service>:8081` |
| `GATEWAY_COMMUNITY_SERVICE_URI` | `http://spring-member-community-service:8083` | 동일 | `http://<community-service>:8083` |
| `GATEWAY_STOCK_SERVICE_URI` | `http://spring-member-stock-service:8084` | 동일 | `http://<stock-service>:8084` |
| `GATEWAY_AUTHORIZATION_SERVER_URI` | `http://spring-security-authorization-server:9000` | 동일 | `http://<authorization-server>:9000` |
| `ADMIN_GATEWAY_ADMIN_BFF_URI` | `http://spring-admin-bff-service:8087` | 동일 | `http://<admin-bff>:8087` |
| `ADMIN_GATEWAY_AUTHORIZATION_SERVER_URI` | `http://spring-security-authorization-server:9000` | 동일 | `http://<authorization-server>:9000` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | `http://spring-member-gateway:8080` | `http://user.localtest.me` | `https://app.hyuncloudlab.com` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | `http://spring-security-authorization-server:9000/oauth2/jwks` | 동일 | `http://<authorization-server>:9000/oauth2/jwks` |
| `TOSS_API_BASE_URL` | `https://openapi.tossinvest.com` | 동일 | 동일 |

## Secret과 Client Identity

Client ID는 공개 식별자이므로 Secret이 아니지만 Client Secret과 Hash는 Secret이다.

| 변수명 | 로컬 Docker | 로컬 Kubernetes | AWS Learning ECS | Secret 여부 |
|---|---|---|---|---|
| `BFF_CLIENT_ID` | `.env.local` | Kubernetes Secret의 현재 구성 | ECS 일반 환경 변수 | No |
| `ADMIN_BFF_CLIENT_ID` | `.env.local` | Kubernetes Secret의 현재 구성 | ECS 일반 환경 변수 | No |
| `SPRING_MSA_INTERNAL_API_TOKEN` | `.env.local` | Kubernetes Secret | `/spring-react-msa/learning/shared/internal-api` | Yes |
| `BFF_CLIENT_SECRET` | `.env.local` | Kubernetes Secret | `/spring-react-msa/learning/member-bff` | Yes |
| `BFF_CLIENT_SECRET_HASH` | `.env.local` | Kubernetes Secret | `/spring-react-msa/learning/auth-server` | Yes |
| `ADMIN_BFF_CLIENT_SECRET` | `.env.local` | Kubernetes Secret | `/spring-react-msa/learning/admin-bff` | Yes |
| `ADMIN_BFF_CLIENT_SECRET_HASH` | `.env.local` | Kubernetes Secret | `/spring-react-msa/learning/auth-server` | Yes |
| `TOSS_API_CLIENT_ID` | `.env.local` | 현재 Kubernetes Secret, 후속 ConfigMap 이동 가능 | ECS 일반 환경 변수 | No |
| `TOSS_API_CLIENT_SECRET` | `.env.local` | Kubernetes Secret | `/spring-react-msa/learning/stock-service` | Yes |

AWS ECS Task Definitions are intentionally not part of this repository state yet. Flyway 환경 변수의 AWS 값은 승인된 계약이며 아직 Task Definition에 적용된 현재값은 아니다. `docker-compose-aws.yml`은 ECS 배포 정의로 만들지 않는다.

비밀이 아닌 값은 ECS 일반 환경 변수 또는 SSM Parameter Store `String`으로 관리한다. SSM SecureString은 사용하지 않는다. Terraform은 Secret Container와 ARN 참조만 관리하고 실제 Secret Value를 `.tf`, `.tfvars`, Output 또는 State에 넣지 않는다.

Kubernetes↔AWS DR은 Learning 적용 범위에서 제외했다. 기존 [재해 복구 아키텍처](../architecture/disaster-recovery.md)는 후속 운영 환경 참고 설계이며 현재 이 표의 두 환경을 동시에 Write 가능하게 만들지 않는다.
