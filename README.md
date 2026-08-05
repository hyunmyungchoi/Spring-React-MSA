# Spring React MSA

Spring Boot 기반 MSA 백엔드와 React 프런트엔드를 하나의 메타 저장소에서 관리하는 사이드 프로젝트입니다.
각 서비스와 프런트 앱은 독립 Git 저장소이며, 이 저장소는 Git submodule로 정확한 조합을 고정합니다.

## 구성

### Backend

| 경로 | 역할 |
| --- | --- |
| `BackEnd/spring-user-service` | 사용자, 관리자 bootstrap, 사용자 도메인 Outbox |
| `BackEnd/spring-security-authorization-server` | OAuth2/OIDC 인증 서버 |
| `BackEnd/spring-member-gateway` | 회원 Gateway와 WebSocket route |
| `BackEnd/spring-member-bff-service` | 회원 BFF, 세션, 채팅, Kafka 소비와 알림 |
| `BackEnd/spring-member-community-service` | 커뮤니티 CRUD와 Outbox |
| `BackEnd/spring-member-stock-service` | 관심 종목, Toss API, Redis cache, Outbox |
| `BackEnd/spring-admin-gateway` | 관리자 Gateway |
| `BackEnd/spring-admin-bff-service` | 관리자 BFF |
| `BackEnd/spring-msa-common-web` | 공통 API 응답과 오류 계약 |
| `BackEnd/spring-msa-common-kafka` | 공통 Kafka 이벤트 계약 |

### Frontend

| 경로 | 역할 |
| --- | --- |
| `FrontEnd/apps/member` | 회원 shell, 인증, 채팅 |
| `FrontEnd/apps/member-community` | 회원 커뮤니티 앱 |
| `FrontEnd/apps/member-stock` | 회원 주식 앱 |
| `FrontEnd/apps/admin` | 관리자 shell |
| `FrontEnd/apps/admin-users` | 관리자 사용자 앱 |
| `FrontEnd/apps/admin-logs` | 관리자 로그 앱 |
| `FrontEnd/packages` | 공통 API 계약과 member/admin UI 패키지 |

## Clone

새 환경에서는 반드시 submodule까지 함께 받습니다.

```powershell
git clone --recurse-submodules https://github.com/hyunmyungchoi/Spring-React-MSA.git
cd Spring-React-MSA
```

이미 부모 저장소만 clone했다면 다음을 실행합니다.

```powershell
git submodule update --init --recursive
```

자식 저장소의 최신 `main`을 모두 반영하려면 다음을 사용합니다.

```powershell
git submodule update --remote --merge
git add .gitmodules BackEnd FrontEnd
git commit -m "chore: update submodule pointers"
```

자세한 작업 방식은 [멀티레포 운영 문서](docs/architecture/multi-repository.md)를 참고합니다.

## Local runtime

로컬 개발 인프라는 VirtualBox Ubuntu VM의 PostgreSQL, Redis, Kafka를 사용합니다.
Docker Desktop 기반 로컬 실행은 사용하지 않습니다.

1. `infra/vm/.env.local`에 로컬 자격증명과 VM 주소를 설정합니다.
2. IntelliJ Run Configuration의 Active profiles에 `local`을 입력합니다.
3. Environment variables에서 `infra/vm/.env.local`을 불러옵니다.
4. Authorization Server, Gateway, 각 WAS를 실행합니다.

Flyway는 서비스별 스키마를 사용합니다.

- `user_service`
- `community_service`
- `stock_service`
- `member_bff`

과거 마이그레이션 파일을 의도적으로 변경해 체크섬을 복구해야 할 때만 해당 서비스에서 다음을 실행합니다.

```powershell
.\gradlew.bat repairDatabase
.\gradlew.bat migrateDatabase
```

정상 개발에서는 적용된 마이그레이션을 수정하지 말고 새 버전 파일을 추가합니다.

## Kafka

User, Community, Stock, Chat 이벤트는 모두 transactional outbox를 사용합니다.
Member BFF 소비자는 중복 이벤트를 차단하고 알림을 기록합니다. 소비 실패는 재시도 후 DLT로 전달됩니다.

```powershell
powershell -ExecutionPolicy Bypass -File infra\ci\live-local-smoke.ps1 -KafkaEnabled
powershell -ExecutionPolicy Bypass -File infra\ci\kafka-dlt-smoke.ps1
```

## Tests

백엔드 서비스:

```powershell
cd BackEnd\spring-user-service
.\gradlew.bat test
```

프런트 전체:

```powershell
cd FrontEnd
corepack pnpm install
corepack pnpm -r test
corepack pnpm -r build
```

CI 스크립트:

```powershell
python -m unittest discover -s infra\ci -p "test_*.py"
```

## Packages

공통 계약은 GitHub Packages로 배포합니다.

- Maven: `com.springmsa:spring-msa-common-web:0.1.0`
- Maven: `com.springmsa:spring-msa-common-kafka:0.1.0`
- npm: `@hyunmyungchoi/api-contract@0.1.0`
- npm: `@hyunmyungchoi/member-common@0.1.0`
- npm: `@hyunmyungchoi/admin-common@0.1.0`

AWS 배포 자료는 보존되어 있지만 현재 로컬 VM 검증 범위에서는 제외합니다.
