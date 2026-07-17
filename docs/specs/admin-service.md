# 관리자 서비스 스펙

## 범위

관리자 가입·로그인, 사용자 조회, 회원 세션과 presence 조회 계약을 정의한다. 외부 prefix는 `/admin-bff`다.

## 인증과 권한

관리자 로그인은 `admin-bff` OAuth2 client를 사용한다. OAuth2 callback 성공 후 Admin BFF는 principal의 `ROLE_ADMIN`을 검사한다. 역할이 없으면 새 BFF session을 종료하고 프런트 `/auth?error=admin_role_required`로 이동한다.

모든 관리자 기능 endpoint는 인증 session을 요구한다. User Service의 `/api/user/admin/**`도 JWT `ROLE_ADMIN`을 독립적으로 확인해 BFF 우회 또는 설정 오류를 방어한다.

## 관리자 가입

`POST /admin-bff/registration/admin` 요청 필드는 `loginId`, `email`, `password`, `username`, 선택 `phoneNumber`다. Member 가입과 같은 길이·email 제약을 사용하고, Admin BFF가 `ROLE_USER`, `ROLE_ADMIN`을 부여해 User Service 내부 API를 호출한다.

**운영 차단 조건:** 현재 이 endpoint는 공개다. 공개 인터넷에 노출된 상태로 운영할 수 없다. 다음 중 하나가 적용되기 전에는 Ingress 또는 security config에서 비활성화한다.

- 최초 관리자 bootstrap 이후 endpoint 제거
- 일회성 초대 token
- 기존 관리자 승인 workflow
- 사설 관리 네트워크 제한과 감사 로그

## 사용자 조회

| Method/Path | 권한 | 설명 |
| --- | --- | --- |
| `GET /admin-bff/user/me` | Admin session | downstream JWT 현재 사용자 |
| `GET /admin-bff/user/admin/users` | `ROLE_ADMIN` | 전체 사용자 목록 |
| `GET /admin-bff/user/admin/users/{userId}` | `ROLE_ADMIN` | 사용자 상세 |

사용자 응답은 `userId`, `loginId`, `email`, `username`, `enabled`, `roles`를 포함한다. 현재 pagination, 검색, 정렬, 수정/비활성화 endpoint는 없다.

## 회원 세션 조회

| Method/Path | 설명 |
| --- | --- |
| `GET /admin-bff/sessions/member` | Member BFF Spring Session과 online TTL 결합 목록 |
| `GET /admin-bff/sessions/member/events` | 최근 LOGIN/LOGOUT presence stream |

세션 응답에는 사용자 정보, session 생성/최근 접근/만료 시각, online 여부, 마지막 heartbeat, online TTL이 포함된다. 만료된 session과 사용자 식별 정보가 없는 session은 제외하고 최근 접근 순으로 정렬한다.

Presence event는 `streamId`, `eventType`, `sessionFingerprint`, 사용자 식별자, roles, `occurredAt`을 가진다. 기본 최근 50개를 역순으로 읽는다.

## 민감 정보 정책

현재 세션 목록은 원본 `sessionId`를 반환한다. 이는 관리 UI·브라우저·로그에 복제될 수 있으므로 목표 계약에서는 session fingerprint 또는 마스킹된 값만 반환해야 한다. 원본 session ID가 필요한 강제 logout은 별도 POST endpoint와 감사 로그로 캡슐화한다.

## 로그 화면

Admin frontend에 `/manage/logs` entry가 있지만 별도 Admin BFF 로그 조회 API는 현재 없다. 실제 로그 탐색은 Grafana/Loki가 담당한다. 향후 UI에서 링크나 제한된 query proxy를 제공할 수 있지만 임의 LogQL과 민감 로그 노출을 방지해야 한다.

## 오류 계약

- 익명: 401 또는 `/auth/me`의 `authenticated=false`
- 관리자 역할 없음: 403 `ADMIN_ROLE_REQUIRED`
- 사용자 없음: 404
- User Service 연결 실패: 502/503 공통 envelope
- Redis session/presence 조회 실패: 5xx; 빈 목록으로 숨기지 않고 운영 오류로 관찰

## 수용 기준

- `ROLE_USER`만 가진 사용자는 관리자 로그인과 사용자 관리 API 모두 접근할 수 없다.
- Member와 Admin session cookie가 서로 대체되지 않는다.
- 세션 조회는 Redis `KEYS`가 아닌 cursor SCAN을 사용한다.
- 공개 관리자 가입이 production에서 차단됐음을 배포 전 검증한다.
