# MSA 구성

## 백엔드

| 서비스 | 포트 | 책임 | 상태 저장소 |
| --- | ---: | --- | --- |
| `spring-member-gateway` | 8080 | 회원 트래픽 라우팅과 CORS | 없음 |
| `spring-admin-gateway` | 8090 | 관리자 트래픽 라우팅과 CORS | 없음 |
| `spring-security-authorization-server` | 9000 | OAuth2/OIDC/JWT 발급 | Redis Session |
| `spring-user-service` | 8081 | 사용자와 역할 | PostgreSQL `user_service` |
| `spring-member-community-service` | 8083 | 게시물 CRUD와 소유권 검증 | PostgreSQL `community_service` |
| `spring-member-stock-service` | 8084 | 관심 종목과 시세 | PostgreSQL `stock_service`, Redis |
| `spring-member-bff-service` | 8079 | 회원 세션, API 조합, 채팅 | PostgreSQL `member_bff`, Redis, Kafka |
| `spring-admin-bff-service` | 8087 | 관리자 세션과 사용자 조회 | Redis, User Service |

도메인 데이터는 프로세스 메모리에 저장하지 않는다. 재시작 후 유지되어야 하는 데이터는 PostgreSQL 또는 Redis에 저장한다.

## PostgreSQL 소유권

각 서비스는 자신의 스키마만 소유하고 Flyway 이력도 같은 스키마에 둔다.

```text
user_service       users, user_roles
community_service  community_posts
stock_service      stock_watch_items
member_bff         chat_rooms, chat_messages
```

커뮤니티 게시물은 JWT subject를 `owner_sub`로 저장한다. 목록 응답의 `ownedByCurrentUser`는 서버가 계산하며, 수정과 삭제 쿼리도 `post_id + owner_sub` 조건으로 제한한다.

## 관리자 생성

관리자 공개 가입 API와 프론트 가입 화면은 제공하지 않는다. 최초 관리자는 User Service 이미지의 `AdminBootstrapMain`을 일회성 작업으로 실행해 생성한다. 일반 내부 사용자 생성 API는 `ROLE_USER`만 허용한다.

## 비동기·캐시

| 저장소 | 용도 |
| --- | --- |
| Redis | Spring Session, presence, 채팅 pub/sub와 최근 메시지, 주식 캐시와 분산 잠금 |
| Kafka | 채팅 생성 이벤트와 DLT |
| Loki | Grafana Alloy가 수집한 Pod 로그 |
| Prometheus | Spring Actuator, Kubernetes, Kafka exporter 메트릭 |
