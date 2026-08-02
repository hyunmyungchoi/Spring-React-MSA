# AWS 최초 관리자 Bootstrap

## 원칙

- 관리자 공개 가입 API와 프론트 가입 UI는 없다.
- User Service의 `AdminBootstrapMain`을 Private ECS one-off Task로 실행한다.
- 입력 Secret은 임시로 만들고 성공 검증 후 삭제 예약한다.
- 실행 이미지는 ECR digest로 고정한다.

## 사전 조건

1. `user_service` Flyway migration이 성공했다.
2. User Service DB Secret에 `db_username`, `db_password`가 있다.
3. User Service image digest가 검증됐다.
4. Task가 RDS private subnet과 security group을 사용한다.

Bootstrap Secret JSON은 다음 키를 가진다.

```json
{
  "login_id": "admin",
  "email": "admin@example.com",
  "password": "20-to-72-byte-secret",
  "username": "Administrator"
}
```

`ADMIN_BOOTSTRAP_AUDIT_ACTOR`, `ADMIN_BOOTSTRAP_REQUEST_ID`는 `ecs run-task` container override로 전달한다. 비밀번호, 토큰, Cookie는 로그나 명령 기록에 출력하지 않는다.

## 실행과 검증

Terraform의 `enable_admin_bootstrap_foundation=true`와 digest 고정 `application_images["user-service"]`로 Task Definition을 만든다. Saved Plan의 삭제/교체 항목과 IAM Secret Resource를 검토한 뒤 승인된 Plan만 적용한다.

Task 실행 후 CloudWatch 로그에서 다음 중 하나를 확인한다.

```text
admin-bootstrap result=created
admin-bootstrap result=already_present
```

첫 실행은 `created`, 동일한 입력 재실행은 `already_present`여야 한다. 다른 관리자나 충돌 계정이 있으면 Task가 실패해야 한다. 이후 실제 관리자 Password Login, OAuth callback, `ROLE_ADMIN` session, logout을 검증한다.

## 정리

검증이 끝나면 Runtime OFF/RDS stop 상태에서 임시 Bootstrap Task/Role 제거 Plan을 적용한다. Bootstrap Secret은 7일 삭제 예약하고 CloudWatch 감사 로그와 생성된 관리자 계정은 보존한다.
