# 공통 오류 런북

## 빠른 분류

```powershell
docker compose --env-file C:\Portfolio\infra\docker\.env.local ps
kubectl get pods -A
kubectl get events -n spring-msa --sort-by=.lastTimestamp
```

로컬 Compose인지 Kubernetes인지 먼저 구분하고, 최초 실패 component의 로그를 본다.

## OAuth `redirect_uri` 불일치

증상:

- authorization request 400
- callback이 다른 host/port로 이동
- 로그인 후 무한 redirect

확인:

- Member: BFF registration redirect URI와 Auth Server client redirect URI가 동일한가
- Admin: `/admin-bff/login/oauth2/code/admin-bff` 경로가 양쪽에 동일한가
- 브라우저용 authorization URI와 container 내부 token/JWK URI를 혼동하지 않았는가
- issuer가 외부에서 관찰되는 Gateway origin과 일치하는가

## `invalid_client` 또는 token 교환 실패

- BFF의 plain client secret과 Auth Server의 BCrypt hash가 같은 secret 쌍인지 확인한다.
- client ID가 `bff-client`/`admin-bff-client`와 섞이지 않았는지 확인한다.
- secret 값을 URL encode하거나 따옴표까지 포함하지 않았는지 확인한다.
- secret 자체를 로그에 출력하지 않는다.

## 로그인은 성공하지만 401

- 브라우저에 `BFFSESSIONID` 또는 `ADMINSESSIONID`가 있는지 확인한다.
- fetch/axios가 credentials를 포함하는지 확인한다.
- Redis와 BFF의 session namespace를 확인한다.
- issuer/JWK URL과 Auth Server health를 확인한다.
- access token refresh 실패 로그를 확인한다.

## 403 CSRF

1. 먼저 해당 BFF `/auth/me`를 호출해 CSRF cookie를 발급받는다.
2. Member는 `MEMBER-XSRF-TOKEN` 값을 `X-MEMBER-XSRF-TOKEN`에 보낸다.
3. Admin은 `ADMIN-XSRF-TOKEN` 값을 `X-ADMIN-XSRF-TOKEN`에 보낸다.
4. cookie path, domain, SameSite, HTTP/HTTPS를 확인한다.

다른 BFF의 CSRF cookie/header를 섞어 쓰지 않는다.

## Admin `ADMIN_ROLE_REQUIRED`

JWT/`userinfo`의 `roles`에 정확히 `ROLE_ADMIN`이 있는지 확인한다. role을 DB에서 수정한 뒤 기존 Auth/BFF session을 계속 사용하면 이전 claim이 남을 수 있으므로 양쪽 로그아웃 후 다시 로그인한다.

## User Service 내부 API 401

- caller와 User Service의 `SPRING_MSA_INTERNAL_API_TOKEN`이 같은지 확인한다.
- header 이름이 `X-Internal-Token`인지 확인한다.
- `/internal/**`를 외부에 임시 공개해 우회하지 않는다.

## Compose service unhealthy

```powershell
docker compose --env-file .env.local logs <service>
docker compose --env-file .env.local exec <service> sh -c "command -v wget"
```

Backend healthcheck는 runtime image의 BusyBox `wget`으로 actuator readiness를 호출한다. wget이 없다면 base image digest 변경 여부를 확인하고 healthcheck 도구 정책을 다시 결정한다. port와 actuator path가 service 설정과 같은지도 확인한다.

## `ImagePullBackOff`

```powershell
kubectl describe pod <pod> -n spring-msa
kubectl get secret ghcr-secret -n spring-msa
```

- GHCR image SHA가 실제 존재하는지 확인한다.
- private package 인증과 `imagePullSecrets`를 확인한다.
- manifest image owner/name/tag 오타를 확인한다.

## Argo CD OutOfSync가 배포되지 않음

현재 자동 Sync가 꺼져 있으므로 정상이다. Diff를 검토한 뒤 수동 Sync한다. 자동 배포로 오해해 기다리지 않는다.

## Chat가 한 replica에서만 보임

- Redis 연결과 `spring:chat:broadcast` channel 설정을 확인한다.
- Member BFF replicas가 같은 Redis를 사용하는지 확인한다.
- subscriber deserialize 오류를 확인한다.
- Redis publish 실패 시 local fallback만 수행하므로 다른 replica 수신은 누락될 수 있다.

재연결 후 REST/HISTORY에 DB 저장 메시지가 보이면 영속성은 성공하고 fan-out만 실패한 것이다.

## Kafka event가 없음

- `APP_KAFKA_ENABLED=true`인지 확인한다. 기본은 false다.
- bootstrap servers와 `kafka` namespace service를 확인한다.
- topic `spring.chat.message.created`와 DLT를 확인한다.
- DB message가 있는데 event가 없으면 현재 Outbox 미구현에 따른 유실 가능성도 조사한다.

## 커뮤니티 글이 재시작 후 사라짐

현재 구현은 메모리 저장이므로 예상된 제약이다. 복구할 영속 데이터가 없다. 운영 사용 전 PostgreSQL repository와 migration을 구현한다.

## Toss 시세 429/503

- Redis cache와 refresh lock health를 먼저 확인한다.
- `stock.toss.rate_limited`, `stock.cache.stale_served` metric을 본다.
- 429를 즉시 반복 호출하지 않는다.
- stale data가 있으면 `dataStatus=STALE`로 반환되는지 확인한다.

## 비활성 또는 존재하지 않는 API가 500을 반환함

AWS Runtime ON Smoke에서 `ADMIN_BFF_REGISTRATION_ENABLED=false`와 조건부 Controller 비등록을 확인했지만, CSRF를 포함한 빈 `POST /admin-bff/registration/admin`은 404 대신 500을 반환했다. 공통 예외 처리기가 Spring의 `NoResourceFoundException`을 일반 `INTERNAL_SERVER_ERROR`로 변환하기 때문이다. 이는 관리자 가입 Controller가 활성화됐다는 의미가 아니며 유효한 가입 데이터는 보내지 않는다.

- 실제 Task Definition의 `ADMIN_BFF_REGISTRATION_ENABLED=false`를 먼저 확인한다.
- `AdminBffRegistrationControllerConditionTest`로 비활성 환경에서 Bean이 없는지 확인한다.
- 공통 Web 예외 처리기에 `NoResourceFoundException` 404 매핑을 추가하려면 Backend 테스트·Build Once·Digest Promote·새 Task Definition Plan을 별도 승인 단위로 수행한다.

## pnpm engine/version 오류

```powershell
node --version
corepack enable
Set-Location C:\Portfolio\FrontEnd
corepack install
pnpm --version
pnpm install --frozen-lockfile
```

Node는 24.18.0, pnpm은 10.0.0이어야 한다. 앱 하위에서 별도 lockfile을 만들지 않는다.

## Gradle dependency verification 실패

- Wrapper 9.3.0을 사용했는지 확인한다.
- artifact를 무조건 trust하거나 verification을 끄지 않는다.
- 의도한 dependency 변경이면 lockfile과 verification metadata를 별도 검토 후 갱신한다.
- 사내 proxy나 잘못된 JDK truststore가 artifact를 바꾸거나 TLS를 실패시키는지 확인한다.
