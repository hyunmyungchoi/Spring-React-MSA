# Spring MSA에 Kafka Transactional Outbox를 적용한 이유

> Spring Boot, PostgreSQL, Kafka, Transactional Outbox, Retry, DLT, Idempotency

## 시작하며

MSA에서 한 서비스의 상태 변화가 다른 서비스에도 전달돼야 하는 경우가 많다.

이 프로젝트에서는 다음 이벤트가 대표적이다.

- User Service에서 회원이 등록된다.
- Community Service에서 게시글이 생성된다.
- Stock Service에서 관심 종목이 추가된다.
- Member BFF는 이벤트를 소비해 사용자 알림과 처리 이력을 갱신한다.

처음 떠올리기 쉬운 구현은 DB 변경 후 Kafka에 메시지를 보내는 것이다.

```java
repository.save(entity);
kafkaTemplate.send(topic, event);
```

하지만 이 두 줄은 하나의 원자적 트랜잭션이 아니다.

## 이중 쓰기 문제

Stock Service에서 관심 종목을 저장한 뒤 이벤트를 발행한다고 가정해 보자.

```text
1. PostgreSQL에 watchlist INSERT
2. Kafka에 watchlist-item-added 발행
```

가능한 실패는 다음과 같다.

| 실패 시점 | 발생하는 불일치 |
|---|---|
| DB commit 후 Kafka 장애 | 관심 종목은 있지만 이벤트가 없음 |
| Kafka 발행 후 DB rollback | 존재하지 않는 데이터의 이벤트가 전달됨 |
| Kafka 응답 유실 후 재전송 | 같은 이벤트가 중복될 수 있음 |

Kafka Transaction과 PostgreSQL Transaction을 코드 한 줄로 묶는다고 이 문제가 자연스럽게 해결되지는 않는다. 서로 다른 시스템에 걸친 원자성을 보장하려면 분산 트랜잭션이 필요하고, 이는 복잡성과 결합도를 크게 높인다.

## Transactional Outbox의 핵심

Transactional Outbox는 비즈니스 데이터와 이벤트 발행 의도를 같은 DB 트랜잭션에 저장한다.

```text
Stock Service DB Transaction
|
+-- stock_service.watchlist INSERT
+-- stock_service.outbox_events INSERT
|
+-- COMMIT
```

두 INSERT가 같은 PostgreSQL transaction에 있으므로 둘 다 저장되거나 둘 다 rollback된다.

별도의 Outbox Relay가 아직 발행되지 않은 row를 읽어 Kafka로 전송한다.

```text
Outbox Relay
|
+-- published_at IS NULL row 조회
+-- Kafka publish
+-- 성공 시 published_at 갱신
+-- 실패 시 attempts와 last_error 갱신
```

예시 테이블은 다음처럼 설계할 수 있다.

```sql
CREATE TABLE outbox_events (
    id              uuid PRIMARY KEY,
    aggregate_type  varchar(100) NOT NULL,
    aggregate_id    varchar(100) NOT NULL,
    event_type      varchar(150) NOT NULL,
    topic           varchar(200) NOT NULL,
    payload         jsonb NOT NULL,
    occurred_at     timestamptz NOT NULL,
    published_at    timestamptz,
    attempts        integer NOT NULL DEFAULT 0,
    last_error      text
);
```

## Outbox가 exactly-once를 보장하지는 않는다

Relay가 Kafka 발행에는 성공했지만 `published_at`을 갱신하기 전에 종료될 수 있다. 재시작한 Relay는 같은 row를 다시 읽고 동일 이벤트를 재발행한다.

따라서 Outbox의 일반적인 전달 특성은 at-least-once다.

```text
DB row 저장: 한 번
Kafka 발행: 한 번 이상 가능
Consumer 처리: 멱등성으로 결과를 한 번처럼 유지
```

여기서 핵심은 소비자의 멱등성이다.

## Consumer 멱등성

모든 이벤트에는 고유한 `eventId`가 있어야 한다. 소비자는 처리한 ID를 별도 테이블에 기록한다.

```sql
CREATE TABLE processed_kafka_events (
    event_id       varchar(100) PRIMARY KEY,
    event_type     varchar(150) NOT NULL,
    processed_at   timestamptz NOT NULL DEFAULT now()
);
```

PostgreSQL에서는 다음과 같이 중복 삽입을 막을 수 있다.

```sql
INSERT INTO processed_kafka_events(event_id, event_type)
VALUES (:eventId, :eventType)
ON CONFLICT (event_id) DO NOTHING;
```

중요한 점은 이벤트 ID claim과 실제 도메인 데이터 변경이 같은 Consumer DB transaction에 있어야 한다는 것이다.

```text
Consumer local transaction
|
+-- processed_kafka_events INSERT
+-- notification INSERT
|
+-- COMMIT
```

처리 이력만 먼저 commit한 뒤 도메인 변경에 실패하면 재시도 시 이미 처리된 이벤트로 오인할 수 있다.

## Retry와 DLT

소비 실패가 모두 같은 성격은 아니다.

| 오류 | 처리 방향 |
|---|---|
| 일시적인 DB connection 오류 | backoff 후 재시도 |
| lock timeout | 제한된 횟수로 재시도 |
| 잘못된 JSON | 반복해도 해결되지 않으므로 DLT |
| 필수 필드 누락 | DLT 후 원인 분석 |
| 코드 결함 | 배포 수정 후 재처리 |

```text
Original Topic
  -> Consumer 실패
  -> Retry 1
  -> Retry 2
  -> Retry 3
  -> <original-topic>.DLT
```

DLT는 메시지를 버리는 쓰레기통이 아니다. 원본 payload, topic, partition, offset, exception, 실패 시간을 보존하고 운영자가 재처리 여부를 결정하는 격리 공간이다.

이번 프로젝트에서는 의도적으로 잘못된 Community 이벤트를 발행해 재시도 이후 `.DLT`로 전달되고 DLT consumer가 로그를 남기는 것까지 확인했다.

## 실제 프로젝트 적용 구조

현재 이벤트 흐름은 다음과 같다.

```text
User Service
  -> springmsa.user.registered.v1

Community Service
  -> springmsa.community.post-created.v1

Stock Service
  -> springmsa.stock.watchlist-item-added.v1

Member BFF
  -> 각 이벤트 소비
  -> processed_kafka_events 기록
  -> 알림 데이터 반영
```

각 원본 topic에는 대응하는 `.DLT` topic이 있다.

```text
springmsa.user.registered.v1.DLT
springmsa.community.post-created.v1.DLT
springmsa.stock.watchlist-item-added.v1.DLT
```

실동작 검증에서 Community와 Stock의 최신 Outbox row는 `attempts=1`, `last_error=null` 상태로 발행됐고 Member BFF 처리 이력에도 반영됐다.

## 서비스 간 DB 접근과 Outbox

Community 이벤트로 Stock 데이터가 바뀌어야 한다고 해서 Community DB 계정에 Stock 테이블 INSERT 권한을 주면 안 된다.

```text
잘못된 경계
Community Service -> Stock DB 직접 INSERT

권장 경계
Community Service -> Community Outbox -> Kafka
                                      -> Stock Consumer
                                      -> Stock 계정으로 Stock DB 변경
```

이렇게 해야 Stock Service가 자기 schema의 유일한 writer로 남는다. Stock 테이블 구조가 바뀌더라도 Community Service는 영향을 최소화할 수 있다.

## DDL과 DML도 구분해야 한다

서비스 실행 중 발생하는 INSERT와 UPDATE는 DML이다. DDL은 테이블과 인덱스 구조를 바꾸는 작업이다.

| 종류 | 예시 | 담당 |
|---|---|---|
| DDL | `CREATE`, `ALTER`, `DROP` | Flyway migration |
| DML | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | Runtime application |

Outbox는 런타임 DML의 일관성을 다루고 Flyway는 DDL 버전을 관리한다. 두 도구의 책임은 다르다.

## Axon을 도입하면 더 좋아질까

Axon Framework는 CQRS, Event Sourcing, Aggregate, Saga 구현을 지원한다. Transactional Outbox의 단순 상위 버전은 아니다.

| 현재 Outbox + Kafka | Axon 도입 |
|---|---|
| 기존 Spring 구조 유지 | Command/Event 모델 도입 |
| 이벤트를 필요한 위치에서 명시적으로 발행 | Aggregate 중심 설계 필요 |
| 인프라와 개념이 비교적 단순 | Axon Server 또는 저장 전략 필요 |
| 현재 요구사항에 충분 | 복잡한 Saga와 Event Sourcing에 장점 |

현재 사이드 프로젝트에서는 Outbox, 멱등성, Retry, DLT를 완성하는 것이 먼저다. 장기간 이어지는 주문-결제-재고 보상 흐름이 필요해질 때 Axon Saga를 별도 실험으로 비교하는 편이 낫다.

## 남은 과제

- Outbox Relay가 여러 replica일 때 row를 중복 claim하지 않도록 locking 전략 적용
- 오래된 published event 정리 정책
- DLT 알림과 운영자 재처리 도구
- schema evolution과 event version 관리
- Kafka consumer lag 모니터링
- Outbox 생성부터 Consumer 반영까지 end-to-end latency 측정

## 결론

Transactional Outbox는 모든 분산 트랜잭션을 해결하지 않는다. 대신 가장 위험한 틈인 "DB에는 저장됐지만 이벤트는 사라지는 상황"을 로컬 트랜잭션으로 줄인다.

그 대가로 중복 발행 가능성을 받아들이고 Consumer 멱등성, Retry, DLT, 운영 모니터링을 함께 설계해야 한다. Outbox 테이블 하나를 추가하는 것으로 끝나는 패턴이 아니라 전달 전 과정을 설계하는 패턴이다.

## 참고 자료

- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Spring for Apache Kafka](https://docs.spring.io/spring-kafka/reference/)
- [Axon Framework Documentation](https://docs.axoniq.io/)

