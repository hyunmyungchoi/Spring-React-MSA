# 회원·관리자 BFF

## 분리 목적

두 BFF는 같은 OAuth2/OIDC 공급자를 사용하지만 사용자 경험, 권한, 세션 쿠키, CSRF 경계, 하위 API가 다르다. 배포 단위와 장애 반경을 분리하고 관리자 기능이 회원 경로에 섞이지 않도록 별도 애플리케이션으로 유지한다.

## 비교

| 항목 | Member BFF | Admin BFF |
| --- | --- | --- |
| 포트 | 8079 | 8087 |
| 외부 prefix | `/bff` | `/admin-bff` |
| 세션 쿠키 | `BFFSESSIONID` | `ADMINSESSIONID` |
| Redis namespace | `spring:session:bff` | `spring:session:admin-bff` |
| CSRF | `MEMBER-XSRF-TOKEN` | `ADMIN-XSRF-TOKEN` |
| OAuth registration | `member-bff` | `admin-bff` |
| 로그인 역할 검사 | 인증 사용자 | `ROLE_ADMIN` 필수 |
| 주요 downstream | User, Community, Stock | User |
| 추가 책임 | presence, 채팅, API 조합 | 회원 세션·presence 조회 |

## Member BFF 책임

- 회원 가입과 현재 사용자 조회
- 커뮤니티 API 프록시와 오류 envelope 변환
- 관심 종목과 시세 workspace 조합
- WebSocket 채팅, 채팅 이력, Redis fan-out
- 로그인/heartbeat/logout presence 관리
- OAuth2 access token 갱신과 downstream relay

주요 endpoint는 `/auth/me`, `/auth/logout`, `/auth/heartbeat`, `/registration/member`, `/user/me`, `/community/**`, `/stock/**`, `/chat/**`다. Gateway가 외부 `/bff/**` prefix를 제거하므로 BFF 내부 controller는 prefix 없는 경로를 사용한다.

Stock workspace는 관심 종목, 종목 정보, 가격을 조합한다. 일부 downstream 호출 실패는 `PartialFailure`로 표현해 화면 전체 실패를 줄인다.

## Admin BFF 책임

- 관리자 가입과 OAuth2 로그인
- 로그인 완료 시 `ROLE_ADMIN` 검사
- 현재 관리자 및 사용자 목록/상세 조회
- Redis의 Member BFF 세션 hash 스캔
- presence TTL key와 Redis Stream 이벤트 조회

관리자 BFF는 회원 세션의 원본 ID를 조회 응답에 포함한다. UI 노출과 로그 기록 시 세션 ID가 인증 자격 증명처럼 취급되지 않도록 마스킹 또는 fingerprint만 노출하는 개선이 필요하다. presence 이벤트는 이미 SHA-256 fingerprint를 사용한다.

## 토큰과 세션 경계

BFF session에는 OIDC principal과 authorized client 연결이 저장된다. Member BFF는 추가로 `userId`, `loginId`, `roles` 등 세션 메타데이터를 저장해 Admin BFF가 회원 세션을 조회할 수 있게 한다.

Admin BFF는 Member BFF namespace를 읽기만 하고 회원 세션을 수정하거나 강제 만료시키지 않는다. 강제 로그아웃 기능을 추가할 경우 Redis key 직접 삭제보다 Member BFF의 감사 가능한 관리 endpoint를 두는 편이 안전하다.

## 장애 처리

- 미인증: BFF 보호 endpoint는 401, `/auth/me`는 익명 payload를 반환한다.
- 관리자 역할 누락: `/auth/me`는 403 `ADMIN_ROLE_REQUIRED`; OAuth 로그인 성공 handler는 세션을 종료한다.
- downstream 4xx/5xx: 공통 `MsaResponse` 오류 구조로 매핑한다.
- access token 부재 또는 갱신 실패: 401로 처리하고 재로그인을 유도한다.
- Redis 장애: 세션 기반 요청과 presence/채팅 fan-out이 영향을 받는다.

## 현재 위험과 후속 작업

1. 공개 관리자 가입을 운영에서 차단한다.
2. Admin BFF가 반환하는 원본 회원 session ID를 마스킹한다.
3. Member BFF에 집중된 채팅 도메인이 커지면 별도 Chat Service 분리를 검토한다.
4. Redis SCAN 기반 전체 세션 조회에 pagination과 결과 제한을 추가한다.
5. BFF의 로그인·logout·권한 거부 통합 테스트를 보강한다.
