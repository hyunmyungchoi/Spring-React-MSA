# 인증 테스트

## 운영 제약

Authorization Server는 1 replica로 운영한다. Spring Session은 Redis지만 OAuth2 authorization과 JWK는 공유되지 않는다.

실제 검증 결과:

- 2 replicas: 간헐 실패 후 재기동 상태에서 8/8 oauth2_login_failed
- 1 replica: 동일 로그인 5/5 성공

2 replicas 전환 전 필요한 항목:

- 영속 OAuth2AuthorizationService
- 공유 OAuth2AuthorizationConsentService
- 고정 JWK key set
- replica 교차 token 교환 테스트
- key rotation 런북

## 자동 E2E

~~~powershell
powershell.exe -ExecutionPolicy Bypass -File infra\ci\k8s-live-smoke.ps1
~~~

검증 범위:

- Member/Admin password login과 OAuth
- auth/me와 CSRF
- Community CRUD
- Stock CRUD
- Toss market API
- Admin user/session/presence API
- 양쪽 logout
- 테스트 데이터 삭제

## 실패 진단

1. callback redirect query의 error 확인
2. Auth Server replica 수 확인
3. client ID, secret hash, redirect URI 확인
4. Redis와 Spring Session namespace 확인
5. Gateway prefix 확인
6. callback과 auth/me replica 비교

Cookie, password, OAuth code, token은 출력하지 않는다.
