# Admin Frontend

관리자용 React SPA다. Admin BFF의 별도 Session Cookie와 CSRF Token을 사용하며 Member Session으로 관리자 기능에 접근하지 않는다.

## Routes

| Path | 기능 |
|---|---|
| `/` | 관리자 홈 |
| `/auth` | 관리자 로그인과 현재 구현의 가입 UI |
| `/manage/users` | 사용자·Member Session·Presence 조회 |
| `/manage/logs` | 로그 화면 Entry; 실제 Admin BFF Log API는 아직 없음 |

공개 관리자 가입은 현재 소스에 남아 있지만 AWS Learning Public Traffic에서는 차단한 뒤 최초 관리자를 Bootstrap해야 한다.

## Local Development

`FrontEnd` Workspace Root에서 실행한다.

```powershell
Set-Location C:\Portfolio\FrontEnd
pnpm install --frozen-lockfile
pnpm --filter admin dev
```

Vite는 `http://localhost:5176`에서 실행되고 `/admin-bff`와 OAuth2/OIDC 요청을 Admin Gateway `http://localhost:8090`으로 Proxy한다.

## Build

```powershell
pnpm --filter admin run lint
pnpm --filter admin run build:all
```

`build:all`은 통합 Admin Shell과 `users`, `logs` 전용 Entry를 만든다. Kubernetes는 각 결과를 별도 Nginx Image로 배포하고, AWS Learning 목표는 Admin 정적 파일을 Private S3와 CloudFront로 제공하는 것이다.

## API 경계

- Admin API Client: browser `fetch`, `credentials=include`
- CSRF Bootstrap: `GET /admin-bff/auth/me`
- 보호 기준: `ROLE_ADMIN`
- 계약: [`../../../docs/specs/authentication.md`](../../../docs/specs/authentication.md), [`../../../docs/specs/admin-service.md`](../../../docs/specs/admin-service.md)
