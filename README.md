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