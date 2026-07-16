# 로컬 개발 런북

## 사전 요구사항

- JDK 17
- Docker Desktop 및 Docker Compose
- Node 24.18.0
- Corepack과 pnpm 10.0.0
- PowerShell

Gradle은 각 프로젝트의 Wrapper 9.3.0을 사용한다. 시스템 Gradle을 별도 설치하지 않는다.

## 환경 파일

저장소 루트에서 다음을 실행한다.

```powershell
Copy-Item C:\Portfolio\infra\docker\.env.example C:\Portfolio\infra\docker\.env.local
```

`.env.local`에서 최소 다음 값을 설정한다.

- PostgreSQL password와 Spring datasource password
- `SPRING_MSA_INTERNAL_API_TOKEN`
- Member/Admin BFF client secret과 Authorization Server용 BCrypt hash
- Toss API client ID/secret
- origin, issuer, redirect URI가 실제 접속 주소와 일치하는지 확인

`.env.local`은 Git에 commit하지 않는다. 기본 Compose는 Kafka를 포함하지 않으므로 `APP_KAFKA_ENABLED=false`를 유지한다. Kafka 기능을 켜려면 접근 가능한 broker와 bootstrap servers를 별도로 준비한다.

## 전체 Docker Compose 실행

```powershell
Set-Location C:\Portfolio\infra\docker
docker compose --env-file .env.local config --quiet
docker compose --env-file .env.local up -d --build
docker compose --env-file .env.local ps
```

서비스가 모두 healthy가 된 뒤 접속한다.

- Member: `http://localhost:5173`
- Admin: `http://localhost:5176`
- Member Gateway: `http://localhost:8080`
- Admin Gateway: `http://localhost:8090`

로그 확인:

```powershell
docker compose --env-file .env.local logs -f spring-member-bff-service
docker compose --env-file .env.local logs -f spring-security-authorization-server
```

종료:

```powershell
docker compose --env-file .env.local down
```

데이터까지 삭제하는 `down -v`는 PostgreSQL volume을 지우므로 의도한 초기화일 때만 사용한다.

## Backend 직접 실행

PostgreSQL과 Redis만 먼저 실행할 수 있다.

```powershell
Set-Location C:\Portfolio\infra\docker
docker compose --env-file .env.local up -d postgres redis
```

그다음 각 서비스 디렉터리에서 필요한 환경 변수를 주입하고 실행한다.

```powershell
Set-Location C:\Portfolio\BackEnd\spring-user-service
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

`application-local.yml`도 환경 변수 placeholder를 사용한다. 서비스별 `application-local.example.yml`을 참고하되 secret을 source file에 기록하지 않는다.

Backend 전체 테스트:

```powershell
Get-ChildItem C:\Portfolio\BackEnd -Directory | Where-Object {
    Test-Path (Join-Path $_.FullName "gradlew.bat")
} | ForEach-Object {
    Push-Location $_.FullName
    try { .\gradlew.bat test --no-daemon }
    finally { Pop-Location }
}
```

## Frontend 직접 실행

```powershell
Set-Location C:\Portfolio\FrontEnd
corepack enable
corepack install
pnpm --version
pnpm install --frozen-lockfile
pnpm --filter member dev
```

별도 터미널에서 Admin을 실행한다.

```powershell
Set-Location C:\Portfolio\FrontEnd
pnpm --filter admin dev
```

Vite proxy는 Member Gateway 8080, Admin Gateway 8090을 바라본다. Gateway/BFF/Auth/User Service가 실행되지 않으면 로그인은 동작하지 않는다.

검증:

```powershell
pnpm --filter member run lint
pnpm --filter member run build:all
pnpm --filter admin run lint
pnpm --filter admin run build:all
```

## 빠른 상태 점검

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:9000/actuator/health
Invoke-RestMethod http://localhost:8079/actuator/health
Invoke-RestMethod http://localhost:8087/actuator/health
```

문제가 생기면 [common-errors.md](common-errors.md)를 확인한다.
