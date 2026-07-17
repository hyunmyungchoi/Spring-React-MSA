# 테스트 전략

## 목적

서비스 단위 정확성뿐 아니라 BFF session, OAuth2 redirect, Redis 공유 상태, Kafka event, 다중 replica WebSocket, GitOps 배포처럼 경계를 넘는 실패를 조기에 찾는다.

## 현재 자동화 기준선

- Backend test source: 16개 파일
- 집중 영역: Stock Service cache/Toss/watchlist/security, Member BFF stock 조합, Kafka disabled config
- 공통 web error parser와 Auth login authentication factory test
- CI build matrix Python unit test
- Frontend: lint와 TypeScript/Vite build, 자동 unit/component/E2E test 없음
- Docker: 선택 image build
- 부하: k6 stock script, Node WebSocket script

User Service, Community Service, Admin BFF, Gateway, 전체 OAuth2 flow, 채팅 persistence/pub-sub/Kafka의 자동 테스트가 상대적으로 부족하다.

## 테스트 피라미드

### 1. Unit test

빠르고 외부 시스템 없는 정책 검증:

- DTO validation과 normalization
- role/claim mapping
- 오류 code mapping
- Stock cache/stale/partial failure 판단
- chat room/content/limit validation
- Outbox state transition과 retry 계산
- CI change-to-service mapping

### 2. Slice/contract test

- MVC controller status/envelope/CSRF/security
- JPA repository unique/ownership/query
- Feign request header와 downstream 오류 parsing
- Kafka event JSON 호환성
- Frontend API envelope unwrap과 CSRF client

공통 계약은 producer와 consumer 양쪽에서 같은 fixture로 검증한다.

### 3. Integration test

Testcontainers 또는 CI service로 PostgreSQL, Redis, Kafka를 실제 실행한다.

- Spring Session 저장/조회/만료
- User 생성 unique와 BCrypt
- Stock refresh lock과 stale fallback
- Chat DB commit/cache/pub-sub
- Outbox relay와 Kafka/DLT
- Member/Admin BFF가 실제 token을 relay하는 흐름

H2만으로 PostgreSQL locking, index, JSON, migration 동작을 대표하지 않는다.

### 4. End-to-end test

브라우저 기반으로 다음 사용자 여정을 검증한다.

- 회원 가입 → 로그인 → `/auth/me` → logout
- 관리자 role 허용/거부
- 관심 종목 추가와 workspace partial failure
- 채팅 연결·전송·재연결·history
- Git SHA image 배포 후 smoke test

Playwright 도입을 권장하며 password/token/cookie는 CI Secret에서 공급한다.

### 5. 부하·장애 test

- Stock cache stampede와 Toss 호출 상한
- WebSocket connection/message latency와 replica fan-out
- Redis/Kafka/PostgreSQL 장애 주입
- rolling update 중 session과 connection 영향

## CI Gate

Pull Request 필수:

- Gradle test
- pnpm frozen install, lint, build:all
- CI matrix unit test
- 선택 Docker image build
- dependency lock/verification 위반 없음

추가 권장 gate:

- YAML/Helm/Kubernetes schema lint
- migration validation
- container vulnerability scan와 SBOM
- frontend unit/E2E smoke
- architecture/document link check

## 테스트 데이터

- `ROLE_USER`, `ROLE_ADMIN`, disabled user를 분리한다.
- 실제 개인정보·운영 credential을 사용하지 않는다.
- login ID/email은 test마다 unique하게 만든다.
- WebSocket message에는 추적 가능한 test ID를 넣되 secret은 넣지 않는다.
- 부하 test가 만든 DB/chat data의 정리 기준을 둔다.

## Flaky test 정책

- 실패 test를 단순 재실행해 성공으로 덮지 않는다.
- 시간 의존 코드는 injectable `Clock`을 사용한다.
- 비동기 test는 고정 sleep보다 deadline/polling을 사용한다.
- port, topic, Redis namespace를 test 실행별로 격리한다.
- flaky 원인과 owner를 기록하고 quarantine에는 만료일을 둔다.

## 완료 정의

기능 변경은 성공 경로뿐 아니라 인증 거부, validation, downstream 실패, retry/멱등, 관측 가능성을 검증해야 완료다. 배포 변경은 image build만이 아니라 manifest render/diff와 smoke test까지 포함한다.
