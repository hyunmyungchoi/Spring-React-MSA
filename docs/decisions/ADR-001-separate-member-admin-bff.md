# ADR-001: 회원 BFF와 관리자 BFF를 분리한다

- 상태: 승인
- 결정일: 2026-07-17
- 구현 상태: 적용됨

## 배경

회원과 관리자는 같은 User Service와 Authorization Server를 사용하지만 접근 권한, 세션 수명, CSRF 경계, 화면과 API 조합 책임이 다르다. 하나의 BFF에서 경로만 나누면 관리자 endpoint가 회원 배포와 같은 장애 반경에 놓이고, 쿠키·CORS·OAuth client 설정도 결합된다.

## 결정

회원용 `spring-member-bff-service`와 관리자용 `spring-admin-bff-service`를 독립 애플리케이션·이미지·Kubernetes Deployment로 유지한다. 각 BFF 앞에는 대응하는 Gateway를 둔다.

- Member BFF: `BFFSESSIONID`, member OAuth client, `/bff/**`
- Admin BFF: `ADMINSESSIONID`, admin OAuth client, `/admin-bff/**`
- CSRF cookie/header 이름과 Redis Spring Session namespace도 분리한다.
- Admin BFF 로그인 성공 시 `ROLE_ADMIN`을 추가로 검사한다.
- 공통 응답·오류 계약만 `spring-msa-common-web`으로 공유한다.

## 결과

### 장점

- 관리자 기능의 권한 경계와 배포 주기가 회원 기능에서 분리된다.
- 쿠키 이름과 session namespace 충돌을 피한다.
- 회원 채팅/주식 부하가 관리자 API에 직접 전파되는 범위를 줄인다.
- 관리자 전용 session 관찰 기능을 회원 BFF에 노출하지 않아도 된다.

### 비용

- OAuth2 client, 보안 설정, logout 처리, 공통 DTO 매핑의 중복이 생긴다.
- Gateway, Docker image, CI matrix, Kubernetes resource가 각각 필요하다.
- 공통 로직을 너무 이르게 라이브러리화하면 두 경계가 다시 결합될 수 있다.

## 운영 규칙

- 두 BFF의 세션 cookie와 CSRF 이름을 동일하게 만들지 않는다.
- Admin BFF endpoint는 Member Gateway에 라우팅하지 않는다.
- 공통 모듈은 transport/error contract에 한정하고 도메인 정책을 넣지 않는다.
- 관리자 가입·권한 변경 같은 고위험 기능은 별도 승인과 감사 로그를 갖춘다.

## 대안

1. 단일 BFF의 `/member`, `/admin` 경로 분리: 초기 코드는 적지만 보안·배포 경계가 약하다.
2. 프런트가 각 Resource Server를 직접 호출: access token이 브라우저에 노출되고 API 조합이 SPA로 이동한다.
3. 완전히 별도 인증 서버: 격리는 강하지만 현재 규모에서 사용자 저장소와 운영 비용이 과도하다.
