# 부하 테스트

## 원칙

- 운영 credential과 실제 개인정보를 사용하지 않는다.
- 테스트 대상 환경과 최대 부하를 사전 합의한다.
- 시작 전 baseline metric, 종료 후 데이터 정리와 결과를 기록한다.
- 단순 처리량뿐 아니라 오류, p95/p99, 외부 API 호출량, cache/Kafka/DB 상태를 함께 본다.

## Stock market workspace

스크립트: `infra/load-tests/stock-market-data.js`

기본 시나리오:

- 20 VUs
- 2분
- 5개 symbol
- 요청 간 2초 sleep
- 실패율 `<1%`
- p95 `<500ms`
- Prometheus에서 Toss 요청 증가량이 설정 상한 이하

필수 환경 변수:

```powershell
$env:STOCK_BFF_BASE_URL = "http://user.localtest.me"
$env:STOCK_SYMBOLS = "005930,000660,035420,051910,207940"
$env:BFF_COOKIE = "BFFSESSIONID=<test-session>"
$env:PROMETHEUS_URL = "http://localhost:9090"
$env:STOCK_TOSS_MAX_REQUESTS = "80"
Set-Location C:\Portfolio\infra\load-tests
k6 run .\stock-market-data.js
```

`PROMETHEUS_URL`이 없으면 현재 script의 Toss bounded threshold는 실패하도록 설계돼 있다. Prometheus를 port-forward할 경우 실제 URL을 사용한다.

관찰 metric:

- `http_req_failed`, `http_req_duration`
- `stock_toss_requests_total`
- `stock_cache_hits_total`
- `stock_cache_stale_served_total`
- `stock_toss_rate_limited_total`
- Redis CPU/memory와 Stock Service CPU/thread

## WebSocket 채팅

스크립트: `infra/load-tests/chat-ws-replica2.mjs`

의존성 설치:

```powershell
Set-Location C:\Portfolio\infra\load-tests
corepack prepare pnpm@10.0.0 --activate
pnpm install
```

실행:

```powershell
$env:CHAT_WS_URL = "ws://user.localtest.me/bff/chat/ws?roomId=global"
$env:CHAT_WS_ORIGIN = "http://user.localtest.me"
$env:BFF_COOKIE = "BFFSESSIONID=<test-session>"
$env:CONNECTIONS = "20"
$env:MESSAGES_PER_CONNECTION = "20"
$env:RATE_PER_SECOND = "20"
$env:CONNECT_TIMEOUT_MS = "10000"
pnpm run chat:ws
```

출력은 opened/failed/sent/received/uniqueReceived/duplicateReceives/errors와 p50/p95/p99를 제공한다.

### 현재 스크립트 주의

`received`는 `CONNECTED`, `HISTORY`, `PONG` 같은 제어 frame까지 증가시키며 종료 조건은 `uniqueReceived == totalMessages`를 강제하지 않는다. 따라서 현재 exit code만으로 메시지 완전 전달을 판정하면 안 된다.

개선 전 수동 판정:

- `opened == CONNECTIONS`
- `failed == 0`, `errors == 0`
- `sent == CONNECTIONS × MESSAGES_PER_CONNECTION`
- `uniqueReceived == sent`
- 각 메시지가 모든 연결에 broadcast되는 기대라면 chat frame 총수도 `sent × connections`에 부합하는지 별도 집계

스크립트 개선 항목:

- control frame과 test chat frame count 분리
- client message ID별 수신 connection set 기록
- 누락/중복을 exit code에 반영
- percentile을 최초 수신과 전체 fan-out 완료 latency로 분리
- dependency version exact pin과 lockfile 추가

## 단계별 부하

한 번에 큰 부하를 주지 않고 다음처럼 올린다.

1. Smoke: 2 connections / 10 messages
2. Baseline: 20 / 20
3. Medium: 100 / 50
4. Capacity: 500 이상, 환경 자원에 맞춰 승인
5. Soak: 낮은 rate로 30~60분

각 단계 사이에 pod restart, DB connection, Redis memory, Kafka lag가 정상으로 돌아오는지 확인한다.

## 장애 시나리오

- Member BFF 한 replica 재시작
- Redis 일시 중단: fan-out/cache/session 영향
- Kafka 일시 중단: DB 저장과 event 적체/유실 확인
- PostgreSQL 지연: WebSocket persist latency와 connection 영향
- Toss 429/503: stale cache 제공과 호출 상한

Outbox 구현 전 Kafka 장애 test에서는 DB message와 Kafka event가 불일치할 수 있음을 결과에 명시한다.

## 결과 기록

- Git SHA와 Kubernetes image SHA
- replica/resources와 테스트 데이터 크기
- 정확한 환경 변수에서 secret을 제거한 값
- RPS/connections/messages, duration
- p50/p95/p99, 오류/누락/중복
- DB/Redis/Kafka/CPU/memory graph
- 첫 병목과 다음 용량 가설
