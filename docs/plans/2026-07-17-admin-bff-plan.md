# Admin BFF 개선 계획

- 작성일: 2026-07-17
- 대상: `spring-admin-bff-service`, Admin Gateway, admin frontend
- 우선순위: 운영 전 필수 보안 작업 포함

## 현재 기준선

- 독립 OAuth2 client/session/CSRF 경계
- OAuth 로그인 성공 시 `ROLE_ADMIN` 검사
- User Service의 관리자 API도 `ROLE_ADMIN` 재검사
- 사용자 목록·상세 조회
- Member BFF Redis session/presence 조회
- 관리자 전용 frontend entry와 Gateway/Ingress

## P0: 공개 관리자 가입 차단

현재 `POST /admin-bff/registration/admin`은 인증 없이 `ROLE_ADMIN` 사용자를 생성할 수 있다. production 배포 전에 다음 중 하나로 변경한다.

권장안:

1. 초기 bootstrap 관리자는 별도 운영 command 또는 일회성 job으로 생성한다.
2. 일반 runtime에서는 공개 registration endpoint를 비활성화한다.
3. 추가 관리자 생성은 기존 관리자 인증 + 재인증 + 감사 로그를 요구한다.

완료 조건:

- 익명 사용자가 관리자 계정을 만들 수 없다.
- 관리자 생성자, 대상, 시각, 결과가 감사 로그에 남는다.
- bootstrap credential이 repository나 image에 포함되지 않는다.

## P0: 세션 식별자 보호

- `/sessions/member` 응답에서 원본 `sessionId`를 제거하고 fingerprint/masked ID로 교체한다.
- UI와 로그에서 session token과 cookie를 마스킹한다.
- 강제 logout이 필요하면 raw key 직접 조작 대신 Member BFF 관리 API를 추가한다.
- 강제 logout은 관리자 ID, 대상 fingerprint, 사유를 감사한다.

## P1: 사용자 관리 기능

- pagination, loginId/email 검색, role/enabled filter
- 사용자 비활성화와 role 변경 command
- optimistic locking 또는 version 검증
- 자기 자신의 마지막 관리자 역할 제거 방지
- 민감 동작 재인증과 이중 확인

User Service가 사용자 aggregate의 유일한 writer가 되며 Admin BFF는 명령을 조합하고 정책을 적용한다.

## P1: 세션/presence 운영성

- Redis SCAN 결과 pagination과 최대 scan budget
- Redis 오류·부분 decode 실패 metric
- session/presence 데이터 schema version
- online/session count, login/logout rate dashboard
- presence stream 보존 길이와 감사 요구의 차이 정리

현재 Redis Stream 최대 약 1,000건은 감사 저장소가 아니다. 보안 감사가 필요하면 별도 durable sink로 전송한다.

## P2: 로그 화면

현재 `/manage/logs`는 실제 backend 조회 계약이 없다. 우선 Grafana/Loki deep link를 제공하고, 별도 query API가 필요할 때 다음을 제한한다.

- 허용 namespace/app/time range
- 최대 결과와 timeout
- 민감 필드 masking
- query 자체 감사
- 임의 LogQL 전달 금지

## 테스트

- 익명 관리자 가입 거부 E2E
- ROLE_USER 관리자 로그인 거부
- ROLE_ADMIN user list/detail 성공
- session fingerprint 외 raw session ID 미노출 검사
- Redis 대량 session scan 성능 테스트
- 권한 변경/비활성화 audit test

## 배포 순서

1. 공개 가입 차단과 bootstrap 절차 배포
2. session response contract v2와 frontend 동시 배포
3. 사용자 관리 command/API
4. dashboard와 alert
5. 로그 UI 연동
