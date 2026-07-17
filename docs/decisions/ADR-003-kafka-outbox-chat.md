# ADR-003: 채팅 도메인 이벤트는 Kafka와 Transactional Outbox로 전달한다

- 상태: 승인
- 결정일: 2026-07-17
- 구현 상태: 부분 적용 — Kafka/DLT는 존재하고 Outbox는 미구현

## 배경

채팅 메시지는 먼저 PostgreSQL에 영속화되고, 알림·분석 같은 후속 처리는 Kafka event로 확장하려 한다. DB commit과 Kafka send는 서로 다른 시스템이므로 단순 이중 쓰기는 한쪽만 성공할 수 있다.

현재 코드는 `ChatMessageSavedEvent`를 transaction commit 후 수신해 `KafkaTemplate.send`를 비동기로 호출한다. 이 방식은 rollback된 메시지를 발행하지 않는 장점이 있지만, commit 직후 프로세스 종료나 broker 장애가 발생하면 event가 유실된다.

## 결정

최종 구조는 Transactional Outbox를 사용한다.

1. `chat_messages`와 `chat_outbox` row를 같은 DB transaction에 저장한다.
2. relay worker가 `PENDING` row를 batch로 claim한다.
3. `roomId`를 Kafka key로 `spring.chat.message.created`에 전송한다.
4. broker ack를 받은 row를 `PUBLISHED`로 변경한다.
5. 실패 row는 retry count, next-attempt, last-error를 기록한다.
6. consumer는 `eventId`를 기준으로 멱등 처리한다.
7. 처리 실패 event는 `spring.chat.message.created.DLT`로 보낸다.

## 전달 보장

- DB에서 Outbox까지: atomic
- Outbox에서 Kafka까지: at-least-once
- Kafka partition 내 순서: 같은 `roomId` key 범위에서 보장
- Consumer 효과: event ID deduplication으로 effectively-once를 목표
- WebSocket fan-out: Redis pub/sub 기반 best-effort, 누락은 history 조회로 복구

## 결과

### 장점

- DB에 저장된 채팅 event의 Kafka 유실을 재시도로 복구할 수 있다.
- 미발행 적체량과 oldest age를 측정할 수 있다.
- Kafka 장애가 WebSocket 저장 transaction을 직접 실패시키지 않는다.

### 비용

- outbox schema, relay scheduling/locking, 보존·정리 정책이 필요하다.
- 중복 발행이 가능하므로 모든 consumer가 멱등해야 한다.
- relay 장애와 적체에 대한 metric/alert/runbook이 추가된다.

## 구현 완료 조건

- Outbox entity/repository/migration과 relay가 존재한다.
- Kafka send 실패 후 재기동해도 미발행 event가 전송된다.
- 같은 event를 두 번 전달해도 notification/analytics 결과가 중복되지 않는다.
- `pending count`, `oldest pending age`, `publish failure` metric이 Grafana에서 보인다.
- 현재 `AFTER_COMMIT` 직접 publisher를 제거하거나 Outbox relay로 대체한다.

완료 전까지 문서와 운영 지표에서 현재 발행 경로를 Outbox라고 부르지 않는다.
