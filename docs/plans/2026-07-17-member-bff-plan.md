# Member BFF 개선 계획

- 작성일: 2026-07-17
- 대상: `spring-member-bff-service`, Member Gateway, member frontend
- 상태: 진행 전 계획

## 현재 기준선

이미 구현된 항목:

- OAuth2/OIDC BFF session과 refresh token 지원
- BFF 전용 session/CSRF cookie
- User, Community, Stock API access token relay
- 회원 가입 내부 API와 공통 오류 envelope
- login/heartbeat/logout presence
- 채팅 PostgreSQL·Redis pub/sub·cache·Kafka 예제 경로
- Stock workspace partial failure 조합
- Kubernetes Member BFF 2 replicas

## 목표

Member BFF를 회원 웹의 안정적인 보안·조합 경계로 유지하면서 채팅 도메인의 내구성, session/presence 운영성, downstream 장애 격리를 강화한다.

## 작업 1: 인증 경계 강화

- production cookie에 `Secure` 정책을 명시하고 HTTPS 환경에서 검증한다.
- CORS/redirect/issuer 설정을 환경별 contract test로 검사한다.
- `/auth/me`, OAuth callback, refresh, logout 통합 테스트를 추가한다.
- internal token을 Secret rotation 가능한 구조로 바꾸고 NetworkPolicy를 추가한다.
- 가입 비밀번호 최소 길이와 복잡도 정책을 강화한다.

완료 조건:

- access/refresh token이 브라우저에 노출되지 않는 E2E 증거가 있다.
- CSRF 누락, 다른 BFF cookie, 변조된 session이 모두 거부된다.
- logout 후 BFF/Auth Server session이 재사용되지 않는다.

## 작업 2: Downstream 복원력

- User/Community/Stock Feign timeout을 명시한다.
- idempotent GET에 한정한 retry 정책을 정의한다.
- service별 circuit breaker와 fallback 허용 범위를 결정한다.
- correlation/trace ID를 모든 BFF 오류 응답에 일관되게 포함한다.
- workspace component별 latency/error metric을 추가한다.

완료 조건:

- 한 downstream 지연이 BFF thread pool을 고갈시키지 않는다.
- Stock workspace partial failure가 UI와 metric 양쪽에 보인다.
- 가입/관심 종목 변경 같은 command는 모호한 자동 재시도를 하지 않는다.

## 작업 3: Presence와 세션

- heartbeat interval을 45초 TTL보다 충분히 짧게 고정한다.
- Redis 장애·재연결 시 online 판정 규칙을 문서화하고 테스트한다.
- session 생성/종료/만료 metric을 추가한다.
- Admin BFF와 공유하는 session metadata schema에 version을 부여한다.
- 원본 session ID가 외부 응답에 노출되지 않도록 관리 계약을 바꾼다.

## 작업 4: 도메인 분리 판단

채팅 message write, WebSocket, cache, Kafka relay가 계속 커지면 별도 Chat Service로 분리한다. 다음 중 두 가지 이상이 발생하면 분리 ADR을 작성한다.

- BFF와 채팅의 scaling 요구가 지속적으로 다름
- 채팅 배포가 인증/주식/커뮤니티 조합을 자주 위험에 빠뜨림
- 별도 데이터 소유·보존·moderation 요구가 생김
- WebSocket connection 수가 일반 HTTP 용량 계획을 지배함

## 검증

- Gradle unit/integration test
- 실제 Redis를 사용한 session/presence test
- WireMock 또는 mock server 기반 downstream timeout/error test
- 2 replicas WebSocket load test
- Kubernetes readiness/rolling update 중 session 유지 확인

## 위험

- Redis는 현재 단일 replica라 session/presence의 단일 장애점이다.
- Member BFF의 PostgreSQL schema migration 도구가 명확하지 않다.
- Kafka가 기본 설정에서 비활성일 수 있어 환경별 기능 차이를 놓칠 수 있다.
