# Kafka and transactional Outbox

## Delivery model

The project uses at-least-once delivery:

1. A domain row and its `outbox_events` row are committed in the same PostgreSQL transaction.
2. The service relay locks pending rows with `FOR UPDATE SKIP LOCKED`.
3. The relay publishes a JSON event envelope to Kafka and records `published_at`.
4. Member BFF claims `event_id` in `processed_kafka_events` before writing a notification.
5. A repeated event is ignored because `event_id` is the idempotency key.
6. Consumer failures are retried and then recovered to the matching `.DLT` topic.

A producer crash after Kafka accepts a record but before PostgreSQL records `published_at` can produce a duplicate. This is expected for an Outbox relay and is why consumers must be idempotent.

## Event envelope

All new domain events use `MsaEventEnvelope<T>`:

```text
eventId, eventType, eventVersion, producer, occurredAt, payload
```

Current topics:

| Topic | Producer | Consumer |
| --- | --- | --- |
| `springmsa.community.post-created.v1` | Community service | Member BFF notification |
| `springmsa.user.registered.v1` | User service | Member BFF notification |
| `springmsa.stock.watchlist-item-added.v1` | Stock service | Member BFF notification |
| `spring.chat.message.created` | Member BFF | Existing chat notification and analytics consumers |

Each topic has a same-partition `<topic>.DLT` topic. A DLT is an operational quarantine, not an automatic success path; inspect and replay records only after the underlying handler or data issue is fixed.

## Database ownership

Each producer service owns its own `outbox_events` table. Member BFF owns `processed_kafka_events` and `member_notifications`. No service reads another service database.
