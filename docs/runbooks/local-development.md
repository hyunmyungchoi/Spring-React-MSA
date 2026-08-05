# 로컬 개발 런북

## 사전 요구사항

- JDK 17
- PostgreSQL·Redis가 실행되는 Ubuntu Server VM
- Node 24.18.0
- Corepack과 pnpm 10.0.0
- PowerShell

Gradle은 각 프로젝트의 Wrapper 9.3.0을 사용한다. 시스템 Gradle을 별도 설치하지 않는다.

## 환경 파일

저장소 루트에서 다음을 실행한다.

```powershell
Copy-Item D:\Project\SpringMSA\infra\vm\.env.example D:\Project\SpringMSA\infra\vm\.env.local
```

`.env.local`에서 최소 다음 값을 설정한다.

- PostgreSQL password와 Spring datasource password
- `SPRING_MSA_INTERNAL_API_TOKEN`
- Member/Admin BFF client secret과 Authorization Server용 BCrypt hash
- Toss API client ID/secret
- origin, issuer, redirect URI가 실제 접속 주소와 일치하는지 확인

`.env.local`은 Git에 commit하지 않는다. 현재 VM에 Kafka가 없으므로 `APP_KAFKA_ENABLED=false`를 유지한다. Kafka 기능을 켜려면 접근 가능한 broker와 bootstrap servers를 별도로 준비한다.

## VM 데이터 계층 확인

```powershell
Test-NetConnection <VM_IP> -Port 5432
Test-NetConnection <VM_IP> -Port 6379
```

PostgreSQL과 Redis는 Ubuntu Server VM에서 직접 실행한다. VM 주소와 자격 증명은 `infra/vm/.env.local`에서 관리하고 애플리케이션은 `local,vm` 프로필로 실행한다.

Backend 서비스가 실행된 뒤 접속한다.

- Member: `http://localhost:5173`
- Admin: `http://localhost:5176`
- Member Gateway: `http://localhost:8080`
- Admin Gateway: `http://localhost:8090`

## Backend 직접 실행

`infra/vm/.env.local` 값을 현재 PowerShell 환경에 주입한 뒤 각 서비스 디렉터리에서 실행한다.

```powershell
Set-Location D:\Project\SpringMSA\BackEnd\spring-user-service
$env:SPRING_PROFILES_ACTIVE = "local,vm"
.\gradlew.bat bootRun
```

`application-local.yml`과 `application-vm.yml`은 함께 사용한다. secret을 source file에 기록하지 않는다.

Backend 전체 테스트:

```powershell
Get-ChildItem C:\Project\SpringMSA\BackEnd -Directory | Where-Object {
    Test-Path (Join-Path $_.FullName "gradlew.bat")
} | ForEach-Object {
    Push-Location $_.FullName
    try { .\gradlew.bat test --no-daemon }
    finally { Pop-Location }
}
```

## Frontend 직접 실행

```powershell
Set-Location D:\Project\SpringMSA\FrontEnd
corepack enable
corepack install
pnpm --version
pnpm install --frozen-lockfile
pnpm --filter member dev
```

별도 터미널에서 Admin을 실행한다.

```powershell
Set-Location D:\Project\SpringMSA\FrontEnd
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
