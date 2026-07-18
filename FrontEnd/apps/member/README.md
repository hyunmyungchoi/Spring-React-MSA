# Member Frontend

회원용 React SPA다. 브라우저는 Access Token을 직접 저장하지 않고 Member BFF Session Cookie와 CSRF Token을 사용해 `/bff/**` API를 호출한다.

## Routes

| Path | 기능 |
|---|---|
| `/` | 서비스 선택 |
| `/auth` | 회원 가입·비밀번호/OAuth2 로그인 |
| `/chat` | Member BFF WebSocket 채팅 |
| `/community` | 커뮤니티 게시물 |
| `/stock` | 관심 종목과 시장 데이터 |

## Local Development

`FrontEnd` Workspace Root에서 실행한다.

```powershell
Set-Location C:\Portfolio\FrontEnd
pnpm install --frozen-lockfile
pnpm --filter member dev
```

Vite는 `http://localhost:5173`에서 실행되고 `/bff`, OAuth2/OIDC, Logout 요청과 WebSocket을 Member Gateway `http://localhost:8080`으로 Proxy한다.

## Build

```powershell
pnpm --filter member run lint
pnpm --filter member run build:all
```

`build:all`은 통합 Member Shell과 `community`, `stock` 전용 Entry를 만든다. Kubernetes는 각 결과를 별도 Nginx Image로 배포하고, AWS Learning 목표는 Member 정적 파일을 Private S3와 CloudFront로 제공하는 것이다.

## API 경계

- Member API Client: Axios, `withCredentials=true`
- CSRF Bootstrap: `GET /bff/auth/me`
- WebSocket: `/bff/chat/ws?roomId=global`
- 인증·API 계약: [`../../../docs/specs/authentication.md`](../../../docs/specs/authentication.md), [`../../../docs/specs/member-service.md`](../../../docs/specs/member-service.md)
