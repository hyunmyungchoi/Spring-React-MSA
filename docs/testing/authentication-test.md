# 인증 테스트

## 범위

Authorization Server, Member/Admin BFF, User Service, Redis Session, Gateway/Ingress를 통과하는 인증·권한·CSRF·logout을 검증한다.

## 사전 조건

- User Service, Authorization Server, Member/Admin BFF와 Gateway가 healthy
- Redis 사용 가능
- member와 admin OAuth client ID/secret/redirect URI가 일치
- `ROLE_USER` 사용자와 `ROLE_ADMIN` 사용자 준비
- browser origin이 CORS 허용 목록과 일치

실제 password, token, cookie를 test 결과나 문서에 기록하지 않는다.

## 필수 시나리오

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| A-01 | 회원 올바른 비밀번호 로그인 | Auth session 후 authorization code 완료, Member session 생성 |
| A-02 | 잘못된 비밀번호 | 401 `AUTH_INVALID_CREDENTIALS`, 사용자 존재 여부 비노출 |
| A-03 | disabled 사용자 | A-02와 같은 외부 응답 |
| A-04 | 회원 `/bff/auth/me` | authenticated user와 Member CSRF cookie |
| A-05 | 익명 `/bff/auth/me` | 200, `authenticated=false` |
| A-06 | Admin role 로그인 | Admin session 생성, frontend redirect |
| A-07 | ROLE_USER의 Admin 로그인 | session 정리, `admin_role_required` |
| A-08 | CSRF 없는 Member POST | 403 |
| A-09 | Admin CSRF를 Member 요청에 사용 | 403 |
| A-10 | access token 만료 + 유효 refresh | BFF가 갱신하고 downstream 성공 |
| A-11 | BFF logout만 실행 | BFF 보호 API 거부, Auth session 잔존 가능성 확인 |
| A-12 | authServerLogoutUrl까지 이동 | Auth/BFF 모두 logout, 재인증 필요 |
| A-13 | internal token 누락/오류 | User `/internal/**` 401 |
| A-14 | JWT 역할 변조/잘못된 issuer | Resource Server 401/403 |
| A-15 | 익명 관리자 가입 | AWS Learning Public Traffic 정책에서 거부되어야 함 |

A-15는 현재 코드에서는 허용되는 알려진 실패 항목이다. 운영 준비 gate에서 반드시 차단 상태로 전환한다.

## 브라우저 수동 확인

1. Member `/auth`에서 가입한다.
2. 로그인 요청 후 Network 탭에서 `/login/password` → `/oauth2/authorize` → BFF callback 순서를 확인한다.
3. Storage에서 `AUTHSESSIONID`, `BFFSESSIONID`, `MEMBER-XSRF-TOKEN` 존재를 확인한다.
4. localStorage/sessionStorage에 OAuth token이 없는지 확인한다.
5. `/bff/user/me`가 성공하는지 확인한다.
6. logout 응답의 `authServerLogoutUrl`로 이동한 뒤 다시 보호 페이지를 요청한다.

Admin도 `ADMINSESSIONID`, `ADMIN-XSRF-TOKEN`으로 반복한다. cookie 값을 screenshot이나 issue에 노출하지 않는다.

## 자동화 권장 구조

### Auth Server

- `PasswordLoginController`: validation, invalid credential, disabled user
- `LoginSessionService`: SavedRequest 있음/없음
- JWT customizer: access/id token claim과 roles
- logout redirect allow-list와 open redirect 거부

### BFF

- 익명/인증 `/auth/me`
- cookie CSRF repository 이름과 header
- OAuth success/failure handler redirect
- Admin role check
- authorized client access token relay와 refresh
- logout 시 Member presence 삭제/이벤트

### User Service

- internal token constant-time 검증의 성공/실패
- `/api/user/admin/**` ROLE_ADMIN 정책
- login ID/email unique와 정규화
- password가 평문으로 저장되지 않음

### E2E

Playwright context를 Member/Admin origin별로 분리한다. redirect와 cookie를 브라우저가 실제 처리하게 하고 access token을 test code에서 직접 주입하지 않는다.

## Redis 확인

값 자체를 출력하지 않고 key 존재와 TTL만 확인한다.

```powershell
kubectl exec deployment/redis -n spring-msa -- redis-cli --scan --pattern 'spring:session:*'
```

운영 환경에서는 위 command 권한을 제한하고 session content를 조회하지 않는다.

## 실패 진단 순서

1. Gateway route와 host/origin
2. client ID, redirect URI, issuer
3. Auth session cookie와 Redis
4. BFF session/authorized client
5. JWT JWK/roles
6. CSRF cookie/header
7. downstream internal token

자세한 증상별 조치는 [common errors](../runbooks/common-errors.md)를 따른다.
