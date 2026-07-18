# Spring React MSA

<img width="1672" height="941" alt="Spring React MSA" src="https://github.com/user-attachments/assets/82cc9f08-7028-4f07-9f5c-5f412788f2e5" />

## 개발자가 아닌 탐구자의 MSA 기록

이 프로젝트는 단순히 서비스를 여러 개로 쪼갠 결과물이 아니다.
인증 서버, 게이트웨이, BFF, 도메인 서비스, 프론트엔드, Kubernetes가
각자의 자리에서 서로를 밀어내고 다시 붙잡는 과정을 관찰한 기록에 가깝다.

처음에는 기능을 만드는 일이었다.
하지만 어느 순간부터 질문이 바뀌었다.

- 이 요청은 어디서 시작해서 어디서 끝나는가.
- 장애가 난다면 어느 경계에서 멈춰야 하는가.
- 비밀값은 어디까지 흘러가도 되는가.
- 캐시는 속도를 위한 장치인가, 외부 의존성을 견디기 위한 완충재인가.
- 화면은 데이터를 보여주는 곳인가, 시스템의 상태를 사용자에게 번역하는 곳인가.

이 저장소는 그 질문들에 대한 현재의 답이다.
완성된 정답이라기보다는, 계속 고쳐 쓰는 탐구 노트에 더 가깝다.

## 무엇을 만들었나

Spring Boot와 React로 구성한 MSA 기반 서비스다.
회원, 커뮤니티, 주식 관심 종목, 실시간에 가까운 시장 데이터 조회 흐름을
각 서비스의 책임으로 나누고, BFF와 Gateway를 통해 화면과 도메인 사이의 경계를 정리했다.

최근 작업한 핵심 수직 슬라이스는 `Stock Market Data`다.
토스증권 REST API를 통해 종목 정보와 현재가를 가져오고,
Redis 캐시와 stale fallback으로 외부 API 장애를 완충한다.
프론트엔드는 WebSocket이 아니라 2초 REST polling으로 시장 데이터를 갱신한다.

## 시스템 구성

```text
FrontEnd/apps/member
        |
        v
spring-member-gateway
        |
        v
spring-member-bff-service
        |
        v
spring-member-stock-service
        |
        +--> PostgreSQL: 관심 종목 저장
        +--> Redis: OAuth token, 시세 캐시, stale cache
        +--> Toss REST API: OAuth, 종목, 현재가, 캔들
```

주요 모듈은 다음과 같다.

| 영역 | 역할 |
| --- | --- |
| `spring-security-authorization-server` | OAuth2 인증 서버 |
| `spring-member-gateway` | 사용자 영역 Gateway |
| `spring-member-bff-service` | 화면에 맞춘 API 조합, 부분 실패 응답 |
| `spring-member-stock-service` | 관심 종목, 토스 API 연동, 캐시, 메트릭 |
| `spring-msa-common-web` | 공통 예외 응답과 웹 계층 유틸 |
| `FrontEnd/apps/member` | 회원/커뮤니티/주식 화면 |
| `infra/k8s/spring-msa` | 로컬 Kubernetes 배포 manifest |
| `infra/ci` | 변경 파일 기반 build matrix 선택 |

## Stock Market Data Slice

이 슬라이스에서 중요하게 본 것은 "시세를 보여준다"가 아니다.
외부 API를 시스템 안으로 들여올 때, 어디까지 믿고 어디서부터 방어할지를 정하는 일이었다.

구현된 흐름은 다음과 같다.

1. 사용자는 stock 화면에서 관심 종목과 조회 종목을 다룬다.
2. 화면은 `/bff/stock/market/workspace?symbols=...`를 호출한다.
3. BFF는 종목 정보, 현재가, 관심 종목을 독립적으로 조회한다.
4. 일부 downstream이 실패해도 가능한 데이터는 살리고 `failures`에 원인을 담는다.
5. stock-service는 Toss access token을 Redis에 캐시한다.
6. 현재가는 Redis에 2초간 공유 캐시된다.
7. Toss 장애 또는 rate limit 시 5분 이내 stale cache를 명시적으로 반환한다.

캐시 TTL은 의도를 드러내는 숫자로 고정했다.

| 데이터 | TTL |
| --- | --- |
| 현재가 fresh cache | 2초 |
| 현재가 stale fallback | 5분 |
| 종목 정보 | 24시간 |
| 캔들 | 30초 |
| 가격 refresh lock | 2초 |

2초 polling은 브라우저마다 외부 API를 때리자는 뜻이 아니다.
브라우저는 자주 물어보되, 서버는 Redis를 통해 같은 질문을 하나의 외부 요청으로 줄인다.
동시에 여러 요청이 cold cache를 만나도 per-symbol refresh lock으로 Toss 요청이 번지는 것을 막는다.

## Toss API Secret 사용 방식

토스 API 키는 코드에 없다.
Git에도 없다.
프론트엔드에도 없다.
로그에도 남기지 않는 것을 원칙으로 둔다.

stock-service가 요구하는 환경변수는 세 개다.

```text
TOSS_API_BASE_URL
TOSS_API_CLIENT_ID
TOSS_API_CLIENT_SECRET
```

Spring 설정은 이 값을 다음 설정으로 바인딩한다.

```yaml
toss:
  api:
    base-url: ${TOSS_API_BASE_URL}
    client-id: ${TOSS_API_CLIENT_ID}
    client-secret: ${TOSS_API_CLIENT_SECRET}
```

세 값 모두 실행 환경에서 명시적으로 주입하며 코드 기본값을 두지 않는다. 로컬 기본 주소가 필요하면 Git에서 제외된 `.env.local`에 `https://openapi.tossinvest.com`을 설정한다.

그리고 토스 가이드의 Client Credentials 흐름에 맞춰 토큰을 발급한다.

```text
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
client_id=<TOSS_API_CLIENT_ID>
client_secret=<TOSS_API_CLIENT_SECRET>
```

발급받은 access token은 Redis의 `toss:oauth:access-token`에 저장된다.
실제 market data API 호출에는 다음 헤더가 붙는다.

```http
Authorization: Bearer <access-token>
```

## 로컬 Kubernetes에서 Toss API 쓰기

로컬 Kubernetes에서는 ignored 파일인
`infra/k8s/spring-msa/02-secrets.local.yaml`에 실제 키를 넣는다.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: spring-msa-secret
  namespace: spring-msa
type: Opaque
stringData:
  TOSS_API_CLIENT_ID: "실제-client-id"
  TOSS_API_CLIENT_SECRET: "실제-client-secret"
```

`12-stock-service.yaml`은 이 Secret을 pod 환경변수로 주입한다.

```yaml
- name: TOSS_API_CLIENT_ID
  valueFrom:
    secretKeyRef:
      name: spring-msa-secret
      key: TOSS_API_CLIENT_ID
- name: TOSS_API_CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      name: spring-msa-secret
      key: TOSS_API_CLIENT_SECRET
```

적용은 이렇게 한다.

```powershell
kubectl apply -f infra\k8s\spring-msa\02-secrets.local.yaml
kubectl apply -f infra\k8s\spring-msa\01-configmap.yaml
kubectl apply -f infra\k8s\spring-msa\12-stock-service.yaml
kubectl rollout restart deployment/spring-member-stock-service -n spring-msa
kubectl rollout status deployment/spring-member-stock-service -n spring-msa
```

`infra/tossApi/` 같은 임시 키 보관 폴더는 사용하지 않는다.
그런 파일은 시스템이 읽지도 않고, 실수로 커밋될 위험만 만든다.

## 실행과 검증

백엔드 stock-service:

```powershell
cd BackEnd\spring-member-stock-service
.\gradlew.bat test
```

member BFF:

```powershell
cd BackEnd\spring-member-bff-service
.\gradlew.bat test
```

member frontend:

```powershell
cd FrontEnd\apps\member
pnpm.cmd run lint
pnpm.cmd run build:stock
```

CI build matrix:

```powershell
python -m unittest infra\ci\test_select_build_matrix.py
```

Stock load test:

```powershell
$env:PROMETHEUS_URL="http://prometheus.localtest.me"
k6 run infra\load-tests\stock-market-data.js
```

load test는 20명의 가상 사용자가 같은 5개 종목을 2초마다 조회한다.
목표는 브라우저 요청 수가 아니라 외부 Toss 요청 수가 Redis cache와 refresh lock으로
얼마나 억제되는지를 보는 것이다.

## 관찰한 경계들

### BFF는 단순 전달자가 아니다

BFF는 데이터를 모으는 곳이지만, 모든 실패를 한 덩어리로 뭉개는 곳은 아니다.
종목 정보가 실패해도 관심 종목 목록은 살아 있을 수 있다.
현재가가 실패해도 stale data는 사용자에게 의미가 있을 수 있다.
그래서 workspace 응답은 성공한 조각과 실패한 조각을 함께 담는다.

### 캐시는 빠르게 하기 위한 장치만은 아니다

캐시는 속도보다 먼저 완충재다.
외부 API가 느려지거나 rate limit을 반환할 때 시스템이 곧바로 무너지지 않게 한다.
fresh와 stale을 구분한 것도 같은 이유다.
오래된 데이터는 거짓말이 될 수 있지만, 오래됐다고 말하는 데이터는 판단의 재료가 된다.

### Secret은 기능이 아니라 경계다

API 키는 기능을 켜는 스위치처럼 보이지만,
실제로는 시스템의 경계를 가르는 물건이다.
서버 환경변수와 Kubernetes Secret 바깥으로 흘러나오면,
그 순간 코드가 아니라 운영의 문제가 된다.

## 아직 끝나지 않은 것

이 프로젝트는 완성품이라기보다 계속 움직이는 실험장이다.
다음 질문들이 남아 있다.

- 실제 운영 Prometheus/Grafana 대시보드를 어떤 단위로 나눌 것인가.
- BFF의 부분 실패 모델을 다른 도메인에도 같은 방식으로 확장할 것인가.
- stock-service의 캐시/락 정책을 트래픽 패턴에 맞춰 어떻게 조정할 것인가.
- GitOps 환경에서 Secret을 어떤 방식으로 외부화할 것인가.

답을 먼저 정해놓고 만든 프로젝트는 아니다.
질문이 생겼고, 그 질문을 피하지 않으려고 구조를 만들었다.
