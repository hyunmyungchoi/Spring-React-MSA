# 로컬 개발 런북

## 전제 조건

- JDK 17: D:\Project\SpringMSA\JDK
- Node.js 24.19.0
- Corepack과 pnpm
- Storage VM: 192.168.147.101
- Kafka VM: 192.168.147.131

Docker Desktop은 사용하지 않는다.

## 환경

~~~powershell
Copy-Item D:\Project\SpringMSA\infra\vm\.env.example D:\Project\SpringMSA\infra\vm\.env.local
~~~

~~~dotenv
VM_HOST=192.168.147.101
SPRING_KAFKA_BOOTSTRAP_SERVERS=192.168.147.131:9092
APP_KAFKA_ENABLED=true
~~~

.env.local은 Git에 올리지 않는다.

## Backend

IntelliJ Active profiles는 local,vm이다. Environment variables 칸에는 파일 경로가 아니라 NAME=value를 입력한다.

~~~powershell
Get-Content D:\Project\SpringMSA\infra\vm\.env.local |
  Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } |
  ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
  }
$env:JAVA_HOME = 'D:\Project\SpringMSA\JDK'
$env:SPRING_PROFILES_ACTIVE = 'local,vm'
Set-Location D:\Project\SpringMSA\BackEnd\spring-user-service
.\gradlew.bat bootRun
~~~

## Frontend

~~~powershell
Set-Location D:\Project\SpringMSA\FrontEnd
corepack enable
corepack pnpm install
corepack pnpm --filter member dev
corepack pnpm --filter admin dev
~~~

## Smoke

~~~powershell
powershell.exe -ExecutionPolicy Bypass -File infra\ci\live-local-smoke.ps1 -KafkaEnabled
powershell.exe -ExecutionPolicy Bypass -File infra\ci\k8s-live-smoke.ps1
~~~

첫 명령은 로컬 Gradle, 두 번째는 Kubernetes Ingress를 검증한다.
