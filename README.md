# Spring React MSA Platform

Spring Boot, Spring Security, Spring Authorization Server, React, BFF Pattern을 기반으로 구성한 MSA 아키텍처 프로젝트입니다.

이 프로젝트는 단순 CRUD 서비스가 아니라, 실제 서비스형 플랫폼에서 필요한 인증/인가, Gateway, BFF, Frontend 분리, Resource Server 분리 구조를 학습하고 구현하기 위한 프로젝트입니다.

---

## 1. Architecture Overview

```text
                 +-----------------------------+
                 | Authorization Server :9000  |
                 | - Login Session             |
                 | - OAuth2 / OIDC             |
                 | - SSO 중심                  |
                 +-------------+---------------+
                               ^
                               |
          +--------------------+--------------------+
          |                                         |
          | Authorization Code Flow                 | Authorization Code Flow
          |                                         |
+---------+----------+                    +---------+----------+
| Business BFF :8079 |                    | Admin BFF :8087    |
| BFFSESSIONID       |                    | ADMINSESSIONID     |
| ROLE_USER 영역     |                     | ROLE_ADMIN 영역      |
+---------+----------+                    +---------+----------+
          ^                                         ^
          |                                         |
+---------+----------+                    +---------+----------+
| Public Gateway    |                    | Admin Gateway     |
| :8080             |                    | :8090             |
+---------+----------+                    +---------+----------+
          ^                                         ^
          |                                         |
+---------+---------------------------+    +--------+----------+
| Shell / Community / Stock Frontend  |    | Admin Frontend    |
| :5173 / :5174 / :5175               |    | :5176             |
+-------------------------------------+    +-------------------+


---

## 2. Service Ports

### Business Area

| Component | Port |
|---|---:|
| Shell Portal Frontend | 5173 |
| Community Frontend | 5174 |
| Stock Frontend | 5175 |
| Public Gateway | 8080 |
| Business BFF | 8079 |
| User Service | 8081 |
| Community Service | 8083 |
| Stock Service | 8084 |

### Admin Area

| Component | Port |
|---|---:|
| Admin Frontend | 5176 |
| Admin Gateway | 8090 |
| Admin BFF | 8087 |

### Common

| Component | Port |
|---|---:|
| Authorization Server | 9000 |
| Redis | 16379 |
| PostgreSQL | 15432 |

---

## 3. Current Implementation Status

현재 구현된 기능은 다음과 같습니다.

- React Shell Frontend
- Public Gateway
- Business BFF
- Spring Authorization Server
- User Service
- Community Service
- OAuth2 Authorization Code Flow
- OIDC Login Flow
- BFF Pattern
- Redis 기반 Spring Session
- HttpOnly `BFFSESSIONID`
- `/bff/auth/me` 로그인 상태 확인
- `/bff/user/me` User Service 프록시
- `/bff/community/me` Community Service 프록시
- ID/PW 로그인
- Email OTP 로그인
- WhatsApp OTP 개발용 로그인
- OIDC RP-Initiated Logout
- Authorization Server SSO Session Logout

---

## 4. Authentication Flow

현재 인증 흐름은 BFF Pattern을 기준으로 구성되어 있습니다.

```text
React Shell Frontend
    ↓
Public Gateway
    ↓
Business BFF
    ↓
Authorization Server
    ↓
Login / OTP Verification
    ↓
Authorization Code
    ↓
Business BFF Callback
    ↓
Token Exchange
    ↓
BFF Redis Session에 Token 저장
    ↓
Browser에는 HttpOnly BFFSESSIONID만 저장


## 5. Logout Flow

로그아웃은 BFF 세션과 Authorization Server SSO 세션을 함께 종료하는 구조입니다.

React Shell Frontend
    ↓
GET /bff/auth/logout
    ↓
Business BFF Session Invalidate
    ↓
Authorization Server /connect/logout
    ↓
SSO Login Session Invalidate
    ↓
React /login Redirect

이를 통해 BFF 세션만 제거되는 문제가 아니라, Authorization Server에 남아 있는 SSO 로그인 세션까지 함께 종료합니다.



## 6. Business / Admin Separation Plan

최종 목표는 Business 영역과 Admin 영역을 Gateway / BFF / Frontend 기준으로 분리하는 것입니다.

Business

Business 영역은 일반 사용자 기능을 담당합니다.

Shell / Community / Stock Frontend
    ↓
Public Gateway
    ↓
Business BFF
    ↓
User / Community / Stock 사용자 API

Admin

Admin 영역은 관리자 기능을 담당합니다.

Admin Frontend
    ↓
Admin Gateway
    ↓
Admin BFF
    ↓
User / Community / Stock 관리자 API

Authorization Server는 Business와 Admin이 공통으로 사용합니다.

단, Business와 Admin은 서로 다른 BFF, Gateway, Session Cookie, Client 설정을 사용하여 접근 경로와 권한을 분리합니다.

## 7. API Separation Direction

도메인 서비스는 Business/Admin 용도로 복제하지 않고, 같은 서비스를 공유합니다.

대신 API 경로와 권한을 분리합니다.

User Service
- /api/user/**        → ROLE_USER
- /api/admin/user/**  → ROLE_ADMIN

Community Service
- /api/community/**        → ROLE_USER
- /api/admin/community/**  → ROLE_ADMIN

Stock Service
- /api/stock/**        → ROLE_USER
- /api/admin/stock/**  → ROLE_ADMIN


## 8. Security Direction

현재 개발 환경에서는 일부 Internal API가 로컬 테스트 편의를 위해 열려 있습니다.

운영 구조에서는 다음 방향으로 보완할 예정입니다.

Internal API 외부 노출 차단
Gateway 기준 접근 경로 제한
Business API / Admin API 권한 분리
ROLE_USER / ROLE_ADMIN 접근 제어
Business BFF / Admin BFF Session Cookie 분리
Client Secret 환경변수 분리
Service-to-Service 인증 추가
Docker / Kubernetes 환경 분리


## 9. Tech Stack
### Backend
- Java 17
- Spring Boot 4.x
- Spring Security 7.x
- Spring Authorization Server
- Spring Cloud Gateway
- Spring Session Redis
- PostgreSQL
- Redis
- Gradle
### Frontend
- React
- TypeScript
- Vite
- Redux Toolkit
- React Router



10. Next Steps

다음 구현 순서는 아래와 같습니다.

README 및 아키텍처 문서 정리
BFF 공통 프록시 클라이언트 정리
Stock Service 추가
Public Gateway에 Stock Service 라우팅 추가
Business BFF에 Stock API 프록시 추가
Admin Gateway 추가
Admin BFF 추가
Admin Frontend 추가
각 서비스에 관리자 API 추가
Business/Admin 권한 분리
Docker Compose 구성
GitHub Actions 기반 Build 검증