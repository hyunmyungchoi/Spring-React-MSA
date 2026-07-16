# 인증 스펙

## 범위

회원과 관리자 SPA의 가입, 비밀번호 로그인, OAuth2/OIDC authorization code, 세션 확인, CSRF, token relay, logout 계약을 정의한다.

## 공통 응답

BFF와 공통 오류 처리 적용 서비스는 다음 envelope를 사용한다.

```json
{
  "success": true,
  "code": "OK",
  "message": "OK",
  "status": 200,
  "data": {},
  "errors": []
}
```

검증 실패는 `success=false`, HTTP 상태와 같은 `status`, 안정적인 `code`, 사용자에게 표시 가능한 `message`, 필드 오류 목록을 반환한다.

## 공개 인증 endpoint

| 경계 | Method/Path | 인증 | 설명 |
| --- | --- | --- | --- |
| Auth Server | `POST /login/password` | 공개 | 비밀번호 검증 후 Auth session 생성 |
| Member Gateway | `GET /bff/oauth2/authorization/member-bff` | 공개 | 회원 OAuth2 로그인 시작 |
| Admin Gateway | `GET /admin-bff/oauth2/authorization/admin-bff` | 공개 | 관리자 OAuth2 로그인 시작 |
| Member BFF | `GET /bff/auth/me` | 공개 | 익명/인증 상태와 CSRF cookie 발행 |
| Admin BFF | `GET /admin-bff/auth/me` | 공개 | 익명/관리자 상태와 CSRF cookie 발행 |
| Member BFF | `POST /bff/auth/logout` | session + CSRF | BFF session/presence 종료 |
| Admin BFF | `POST /admin-bff/auth/logout` | session + CSRF | Admin BFF session 종료 |

## 비밀번호 로그인 요청

```json
{
  "loginId": "member01",
  "password": "secret"
}
```

두 필드는 공백일 수 없다. 성공 응답 data는 `authenticated`, `redirectUrl`, `user`를 가진다. 이전 authorization request가 Auth session에 저장돼 있으면 `redirectUrl`이 해당 URL이며, 없으면 null일 수 있어 SPA가 BFF OAuth2 시작 URL을 사용한다.

잘못된 사용자, 비밀번호, disabled 사용자는 외부에 원인을 구분하지 않고 401 `AUTH_INVALID_CREDENTIALS`로 응답해야 한다.

## `/auth/me` 계약

회원 응답 data:

```json
{
  "authenticated": true,
  "user": {
    "sub": "...",
    "name": "member",
    "username": "member",
    "userId": 1,
    "loginId": "member01",
    "email": "member@example.com",
    "roles": ["ROLE_USER"]
  }
}
```

익명은 `authenticated=false`, `user=null`이다. 관리자 응답은 같은 인증 상태에 `reason` 필드가 추가된다. 인증됐지만 `ROLE_ADMIN`이 없으면 HTTP 403, `ADMIN_ROLE_REQUIRED`, `authenticated=false`, `reason=ADMIN_ROLE_REQUIRED` 형태다.

## Cookie와 CSRF

| 경계 | Cookie | CSRF cookie/header |
| --- | --- | --- |
| Member BFF | `BFFSESSIONID` | `MEMBER-XSRF-TOKEN` / `X-MEMBER-XSRF-TOKEN` |
| Admin BFF | `ADMINSESSIONID` | `ADMIN-XSRF-TOKEN` / `X-ADMIN-XSRF-TOKEN` |
| Auth Server | `AUTHSESSIONID` | Spring Security login 정책 |

SPA 요청은 credentials를 포함해야 한다. BFF의 unsafe method는 먼저 `/auth/me`에서 받은 CSRF cookie 값을 대응 헤더로 보낸다. CSRF token이나 session cookie를 로그·URL·localStorage에 저장하지 않는다.

## JWT 계약

Authorization Server access/id token은 가능한 경우 다음 claim을 담는다.

- `sub`
- `user_id`
- `login_id`
- `email`
- `name`, `username`
- `roles`: `ROLE_USER`, `ROLE_ADMIN`

Resource Server는 issuer와 JWK set을 모두 환경 변수로 받는다. `roles`가 없으면 권한 목록은 비어 있으며 관리자 API 접근이 거부된다.

## 내부 인증

Auth Server와 두 BFF가 User Service `/internal/**`를 호출할 때 `X-Internal-Token`을 보낸다. 누락 또는 불일치는 401이다. token 값은 환경 변수/Secret으로만 공급하고 공개 Gateway route에 `/internal`을 추가하지 않는다.

## Logout 응답

BFF logout data는 `logout=success`, `authServerLogoutRequired=true`, `authServerLogoutUrl`을 반환한다. SPA는 마지막 URL로 top-level navigation해야 Auth Server session까지 종료된다.

## 보안 수용 기준

- 브라우저 network/storage에서 access/refresh token이 보이지 않는다.
- Member cookie로 Admin BFF 보호 endpoint에 접근할 수 없다.
- CSRF header 없는 상태 변경 요청은 거부된다.
- `ROLE_USER`만 가진 사용자는 Admin BFF 로그인 완료 및 User Admin API에서 거부된다.
- logout 후 BFF와 Auth Server 양쪽 세션으로 보호 resource를 이용할 수 없다.
