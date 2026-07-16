# ADR-002: 브라우저 인증은 BFF 세션으로 처리한다

- 상태: 승인
- 결정일: 2026-07-17
- 구현 상태: 적용됨

## 배경

SPA가 OAuth2 access/refresh token을 직접 보관하면 XSS 영향이 커지고 토큰 갱신·폐기·하위 API 호출 책임이 브라우저에 분산된다. 이 프로젝트는 회원·관리자 웹이 모두 자체 BFF를 가지므로 토큰을 서버 측에 유지할 수 있다.

## 결정

Authorization Code + OIDC 흐름의 OAuth2 client 역할을 BFF가 맡는다. 브라우저는 HttpOnly BFF session cookie만 보내며 BFF가 서버 측 authorized client에서 access token을 얻어 Resource Server에 Bearer token으로 relay한다.

- Authorization Server 세션: `AUTHSESSIONID`
- Member BFF 세션: `BFFSESSIONID`
- Admin BFF 세션: `ADMINSESSIONID`
- 세션 저장소: Redis-backed Spring Session
- unsafe HTTP method: BFF별 cookie CSRF token 사용
- downstream JWT: Authorization Server issuer/JWK로 검증
- 내부 사용자 API: `X-Internal-Token` 공유 비밀 사용

## 결과

### 장점

- access/refresh token이 JavaScript 저장소에 노출되지 않는다.
- refresh token 갱신과 token relay를 BFF 한 곳에서 처리한다.
- 회원과 관리자 세션을 독립적으로 폐기할 수 있다.
- SPA는 `/auth/me` 결과와 CSRF token만 이해하면 된다.

### 비용

- Redis 가용성이 로그인 상태의 가용성에 직접 영향을 준다.
- 세션 cookie를 사용하므로 CSRF 방어와 정확한 CORS credential 설정이 필수다.
- Gateway, frontend origin, issuer, redirect URI가 정확히 일치해야 한다.
- BFF와 Authorization Server 양쪽 세션을 모두 종료해야 완전한 logout이 된다.

## 보안 규칙

- session cookie는 HttpOnly를 유지한다.
- 운영 HTTPS에서는 Secure cookie를 활성화한다.
- CORS에 wildcard origin과 credentials를 함께 사용하지 않는다.
- POST/PUT/PATCH/DELETE 요청은 BFF별 CSRF header를 검증한다.
- `/internal/**`를 Gateway/Ingress에 외부 노출하지 않고 NetworkPolicy를 추가한다.
- client secret, internal token, Redis/PostgreSQL/Toss credentials는 Secret으로 주입한다.

## 대안

1. SPA Authorization Code + PKCE: public client 표준에는 맞지만 브라우저 토큰 보관과 API 조합 문제가 남는다.
2. JWT를 browser cookie에 직접 저장: stateless하지만 강제 폐기와 refresh token 관리가 어렵다.
3. Gateway 단일 session: BFF별 사용자 경험과 권한 경계가 결합된다.
