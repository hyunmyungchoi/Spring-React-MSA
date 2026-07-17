# 실시간 채팅 스펙

## 연결

- URL: `ws(s)://{host}/bff/chat/ws?roomId={roomId}`
- 인증: 기존 Member BFF session cookie
- Origin: Member BFF의 허용 origin pattern과 일치해야 한다.
- 기본 room: query가 비어 있으면 `global`
- room ID: `[a-zA-Z0-9:_-]{1,80}`

미인증 연결은 policy violation으로 닫고, 잘못된 room은 bad data로 닫는다. 연결 성공 후 서버는 `CONNECTED`, `HISTORY`를 순서대로 보낸다.

## Client message

Ping:

```json
{ "type": "PING" }
```

Chat:

```json
{
  "type": "CHAT_MESSAGE",
  "content": "hello"
}
```

content는 trim 후 비어 있을 수 없고 최대 1,000자다. 지원하지 않는 type은 연결을 끊지 않고 `ERROR` frame을 반환한다.

## Server message

모든 frame은 다음 공통 필드를 가진다.

```json
{
  "type": "CHAT_MESSAGE",
  "roomId": "global",
  "message": null,
  "messages": [],
  "detail": null,
  "occurredAt": "2026-07-17T00:00:00Z"
}
```

| type | `message` | `messages` | 의미 |
| --- | --- | --- | --- |
| `CONNECTED` | null | empty | room 등록 완료 |
| `HISTORY` | null | 최근 목록 | 초기 이력 |
| `CHAT_MESSAGE` | 저장 메시지 | empty | 새 메시지 |
| `PONG` | null | empty | heartbeat 응답 |
| `ERROR` | null | empty | `detail`에 오류 |

저장 메시지는 `streamId`, `roomId`, `senderUserId`, `senderLoginId`, `senderName`, `content`, `sentAt`을 가진다. 현재 `streamId`는 PostgreSQL message ID 문자열이다.

## History REST API

`GET /bff/chat/rooms/{roomId}/messages?limit={n}`

- 인증과 BFF session 필요
- limit 생략/0/음수: 기본 50
- 최대: 200
- 반환 순서: 오래된 메시지에서 최신 메시지 순
- 응답: 공통 `MsaResponse<List<ChatMessageResponse>>`

## 저장과 전달

1. 서버가 room/content를 검증한다.
2. PostgreSQL transaction에서 room을 찾거나 생성하고 message를 저장한다.
3. commit 후 최근 Redis cache를 갱신한다.
4. `app.kafka.enabled=true`이면 Kafka 생성 event를 전송한다.
5. Redis pub/sub에 server frame을 publish한다.
6. 모든 BFF replica가 자기 local room sessions에 broadcast한다.

Redis publish 실패 시 현재 replica에만 local fallback한다. 다른 replica 연결은 해당 실시간 frame을 받지 못할 수 있지만 history로 복구할 수 있다.

## Kafka event

`ChatMessageCreatedEvent` 필드:

- `eventId`: `chat-message-created:{messageId}`
- `messageId`, `roomId`
- sender user/login/name
- `content`, `sentAt`, `occurredAt`

topic은 `spring.chat.message.created`, DLT는 `.DLT` suffix다. `roomId`를 key로 사용한다.

**현재 보장:** DB commit 후 best-effort Kafka send다. Outbox가 구현되기 전에는 DB/Kafka 원자성을 보장하지 않는다.

## Client 동작

- network 단절 시 지수 backoff로 재연결한다.
- 재연결 후 HISTORY 또는 REST history로 누락을 복구한다.
- 중복 `streamId`는 UI에서 한 번만 표시한다.
- PING/PONG timeout을 감지해 dead connection을 닫는다.
- 전송 실패 `ERROR`를 성공 메시지로 취급하지 않는다.

## 비기능 수용 기준

- 2개 Member BFF replica 사이에서 room message가 전달된다.
- room이 다른 client에 메시지가 섞이지 않는다.
- 동일 `streamId` 중복 표시가 없다.
- p95/p99 latency와 connection/message 실패율을 load test에서 기록한다.
- Kafka 장애 중에도 DB 저장과 Redis 실시간 경로의 기대 동작을 명시적으로 검증한다.
