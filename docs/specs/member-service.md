# 회원 서비스 스펙

## 범위

회원 가입, 사용자 정보, presence, 커뮤니티를 Member Gateway와 Member BFF를 통해 제공하는 계약을 정의한다. 채팅과 주식은 별도 스펙을 따른다.

## 회원 가입

`POST /bff/registration/member`는 공개 endpoint이며 다음 data를 받는다.

| 필드 | 제약 |
| --- | --- |
| `loginId` | 필수, 최대 50자, unique |
| `email` | 필수, email 형식, unique, 소문자 정규화 |
| `password` | 필수, 현재 4~100자 |
| `username` | 필수, 최대 100자 |
| `phoneNumber` | 선택, 최대 30자 |
| `whatsappNumber` | 선택, 최대 30자 |

Member BFF는 요청에 `ROLE_USER`만 부여해 User Service `POST /internal/users`를 호출한다. User Service는 BCrypt로 password를 저장한다. 성공은 HTTP 201과 `userId`, `loginId`, `email`, `username`, `enabled`, `roles`를 반환한다.

충돌은 로그인 ID 또는 email 중복이며 HTTP 409다. 최소 4자 규칙은 이 프로젝트의 Learning 편의를 위해 유지하기로 결정했다. 이는 운영 권장 보안 기준이 아니라 명시적으로 수용한 위험이며, 향후 실제 운영 환경을 분리할 때는 별도 비밀번호 정책과 추가 인증 수단을 다시 결정해야 한다.

## 사용자 endpoint

| Method/Path | 설명 |
| --- | --- |
| `GET /bff/auth/me` | BFF session 사용자와 인증 상태 |
| `GET /bff/user/me` | User Service JWT claim 기반 사용자 정보 |
| `POST /bff/auth/heartbeat` | online presence TTL 갱신 |
| `POST /bff/auth/logout` | presence logout + session 종료 |

`/user/me` 호출 시 BFF는 서버 측 access token을 `Authorization: Bearer`로 User Service `/api/user/me`에 전달한다.

## Presence

로그인 성공과 heartbeat는 `spring:presence:member-bff:sessions:{sessionId}` key를 갱신한다. 기본 TTL은 45초다. 로그인/로그아웃은 Redis Stream `spring:presence:member-bff:events`에 기록하고 최대 약 1,000개로 trim한다.

heartbeat 응답 data:

- `online`
- `heartbeatAt`
- `expiresAt`
- `ttlSeconds`

SPA는 TTL보다 짧은 간격으로 heartbeat를 보내야 한다. tab 종료나 네트워크 단절로 logout이 호출되지 않아도 TTL 만료 후 offline으로 판단한다.

## 커뮤니티 API

| Method/Path | 설명 | 응답 |
| --- | --- | --- |
| `GET /bff/community/me` | Resource Server claim 확인 | 사용자 map |
| `GET /bff/community/posts` | 게시물 목록 | 게시물 배열 |
| `POST /bff/community/posts` | 게시물 생성 | 생성 게시물 |
| `PUT /bff/community/posts/{postId}` | 게시물 수정 | 수정 게시물 |
| `DELETE /bff/community/posts/{postId}` | 게시물 삭제 | 200 envelope |

게시물은 `id`, `title`, `content`, `author`, `createdAt`, `updatedAt`을 가진다. author는 JWT authentication name(`sub`)에서 정한다.

**현재 제약:** Community Service는 `ConcurrentHashMap`에 게시물을 보관한다. 재기동·재배포 시 데이터가 사라지고 replicas 간 데이터가 일치하지 않는다. update/delete 작성자 검증과 request validation도 없다. 운영 전 PostgreSQL 영속화, 소유권 검사, pagination, title/content 검증이 필요하다.

## 오류와 권한

- 미인증 보호 endpoint: 401
- 존재하지 않는 게시물: 404
- User/Community Service 연결 실패: BFF가 502/503 계열 공통 envelope로 변환
- User Service 내부 token 누락: 401

## 데이터 모델

User Service `users`는 사용자 기본 정보·enabled·timestamp를, `user_roles`는 역할 집합을 가진다. login ID와 email은 unique다. 사용자 수정, 비활성화, 비밀번호 변경 API는 현재 제공하지 않는다.

## 수용 기준

- 회원 가입은 항상 `ROLE_USER`를 포함하고 `ROLE_ADMIN`을 스스로 요청할 수 없다.
- 이메일은 대소문자를 달리해 중복 생성되지 않는다.
- heartbeat 중단 후 TTL이 지나면 Admin BFF에서 offline으로 보인다.
- BFF가 downstream 오류 code와 field errors를 보존한다.
