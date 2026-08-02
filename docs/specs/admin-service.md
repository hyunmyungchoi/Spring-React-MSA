# 관리자 서비스 명세

## 인증과 권한

Admin BFF는 `admin-bff` OAuth2 client를 사용한다. 로그인 완료 후 principal에 `ROLE_ADMIN`이 없으면 세션을 종료하고 관리자 화면 접근을 거부한다.

## 최초 관리자

공개 `POST /admin-bff/registration/admin` 엔드포인트와 관리자 가입 UI는 존재하지 않는다. 최초 관리자는 User Service의 `AdminBootstrapMain`을 일회성 Job 또는 ECS Task로 실행한다.

필수 입력은 다음과 같다.

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
ADMIN_BOOTSTRAP_LOGIN_ID
ADMIN_BOOTSTRAP_EMAIL
ADMIN_BOOTSTRAP_PASSWORD
ADMIN_BOOTSTRAP_USERNAME
ADMIN_BOOTSTRAP_AUDIT_ACTOR
ADMIN_BOOTSTRAP_REQUEST_ID
```

작업은 PostgreSQL advisory lock과 serializable transaction을 사용한다. 관리자가 없을 때만 `ROLE_USER`, `ROLE_ADMIN`을 함께 생성하고, 동일 입력 재실행은 `already_present`를 반환한다. 다른 관리자나 충돌 계정이 있으면 실패한다.

## 관리자 API

| Method/Path | 권한 | 설명 |
| --- | --- | --- |
| `GET /admin-bff/user/me` | Admin session | 현재 관리자 |
| `GET /admin-bff/user/admin/users` | `ROLE_ADMIN` | 사용자 목록 |
| `GET /admin-bff/user/admin/users/{userId}` | `ROLE_ADMIN` | 사용자 상세 |
| `GET /admin-bff/sessions/member` | `ROLE_ADMIN` | 회원 세션과 presence |
| `GET /admin-bff/sessions/member/events` | `ROLE_ADMIN` | 최근 로그인/로그아웃 이벤트 |

일반 내부 사용자 생성 API는 `ROLE_USER`만 허용하며 관리자를 만들 수 없다.
