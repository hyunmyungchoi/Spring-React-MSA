# MSA 데이터베이스는 VM부터 나눠야 할까

> PostgreSQL, Database per Service, Schema, Role, Flyway, Least Privilege

## 시작하며

MSA를 구성하면 흔히 "서비스마다 데이터베이스를 분리해야 한다"는 말을 듣는다. 여기서 분리의 의미가 애매하다.

- 서비스마다 PostgreSQL 계정만 다르게 만들면 되는가?
- 한 PostgreSQL 안에서 schema를 나누면 되는가?
- 서비스마다 database를 만들어야 하는가?
- PostgreSQL instance나 VM까지 따로 만들어야 하는가?
- Kubernetes Postgres Operator를 바로 도입해야 하는가?

현재 프로젝트에는 PostgreSQL과 Redis를 실행하는 Storage VM 한 대가 있다. Windows 호스트 한 대에서 여러 VM을 실행하는 사이드 프로젝트라는 조건을 기준으로 현실적인 분리 순서를 정리했다.

## Database per Service의 핵심

Database per Service의 핵심은 반드시 물리 서버를 하나씩 배정하는 것이 아니다.

> 각 서비스가 자기 데이터의 소유권을 가지며 다른 서비스가 그 저장소를 직접 읽거나 쓰지 못하게 경계를 만드는 것이다.

Community Service가 Stock 테이블 구조를 알고 직접 INSERT할 수 있다면 VM을 따로 나눴더라도 서비스 경계는 약하다. 반대로 같은 PostgreSQL instance를 사용해도 계정과 권한이 분리되고 서비스 간 접근이 API나 이벤트로만 이뤄진다면 논리적인 소유권 경계를 만들 수 있다.

## 분리 수준 비교

| 단계 | 구성 | 논리적 격리 | 장애 격리 | 운영 비용 |
|---:|---|---:|---:|---:|
| 1 | 한 database, 서비스별 schema와 role | 중간 | 낮음 | 낮음 |
| 2 | 한 instance, 서비스별 database와 role | 중상 | 낮음 | 중간 |
| 3 | 서비스별 PostgreSQL instance | 높음 | 중간 | 높음 |
| 4 | 서비스별 Storage VM | 매우 높음 | 높음 | 매우 높음 |
| 5 | Kubernetes Postgres Operator | 정책에 따라 다름 | 구성에 따라 다름 | 학습 및 운영 비용 높음 |

물리적으로 더 많이 나눌수록 보안과 장애 격리는 좋아질 수 있지만 backup, restore, monitoring, patch, memory, connection pool도 각각 관리해야 한다.

## 현재 프로젝트의 권장 구조

현재 단계에서는 Storage VM 한 대와 PostgreSQL instance 한 개를 유지한다. 그 안에서 서비스별 schema와 role을 분리한다.

```text
Storage VM
  |
  +-- PostgreSQL instance
      |
      +-- user_service schema
      |   +-- user_migrator
      |   +-- user_app
      |
      +-- community_service schema
      |   +-- community_migrator
      |   +-- community_app
      |
      +-- stock_service schema
      |   +-- stock_migrator
      |   +-- stock_app
      |
      +-- member_bff schema
          +-- member_bff_migrator
          +-- member_bff_app
```

이 구조는 VM 자원을 적게 사용하면서 계정 수준의 경계를 실제로 검증할 수 있다.

## Migrator 계정과 Runtime 계정 분리

Flyway는 테이블과 인덱스를 생성하고 변경하므로 DDL 권한이 필요하다. 애플리케이션은 실행 중 SELECT, INSERT, UPDATE, DELETE만 수행하면 된다.

두 역할을 같은 계정으로 실행하면 애플리케이션 취약점이 발생했을 때 공격자가 테이블을 DROP하거나 schema를 변경할 수도 있다.

| 계정 | 권한 |
|---|---|
| `community_migrator` | 자기 schema의 DDL, Flyway migration |
| `community_app` | 자기 schema의 필요한 DML만 수행 |

개념적인 초기 SQL은 다음과 같다.

```sql
CREATE ROLE community_migrator LOGIN PASSWORD '<migration-secret>';
CREATE ROLE community_app LOGIN PASSWORD '<runtime-secret>';

CREATE SCHEMA community_service AUTHORIZATION community_migrator;

GRANT CONNECT ON DATABASE springmsa
TO community_migrator, community_app;

GRANT USAGE ON SCHEMA community_service
TO community_app;

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA community_service
TO community_app;

GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA community_service
TO community_app;
```

Flyway가 앞으로 생성할 테이블과 sequence에도 runtime 권한이 자동으로 적용되도록 default privileges를 설정한다.

```sql
ALTER DEFAULT PRIVILEGES FOR ROLE community_migrator
IN SCHEMA community_service
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO community_app;

ALTER DEFAULT PRIVILEGES FOR ROLE community_migrator
IN SCHEMA community_service
GRANT USAGE, SELECT ON SEQUENCES TO community_app;
```

환경에 따라 `public` schema의 생성 권한도 제한한다.

```sql
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
```

비밀번호는 SQL 파일과 Git에 넣지 않고 Kubernetes Secret 또는 별도 환경 변수 파일로 주입해야 한다.

## 다른 서비스 schema에는 권한을 주지 않는다

Community Service가 Stock 데이터를 변경해야 하는 요구가 생겼다고 가정해 보자. 가장 쉬운 구현은 `community_app`에 Stock 테이블 INSERT 권한을 주는 것이다.

하지만 이 방식은 서비스 경계를 무너뜨린다.

- Community가 Stock schema 구조를 알아야 한다.
- Stock migration이 Community 배포에 영향을 준다.
- 누가 데이터를 변경했는지 책임이 모호해진다.
- Stock의 validation과 domain rule을 우회한다.
- 장애가 다른 서비스 DB로 직접 전파된다.

권장 구조는 다음과 같다.

```text
동기 요청
Community Service -> Stock API -> stock_app -> Stock schema

비동기 요청
Community Service -> Outbox -> Kafka -> Stock Consumer
                                  -> stock_app -> Stock schema
```

최종 INSERT는 항상 데이터를 소유한 Stock Service와 `stock_app` 계정이 수행한다.

## Schema 분리와 Database 분리

같은 PostgreSQL instance에서 schema만 분리하면 connection string과 운영이 단순하다. 반면 잘못된 권한 부여로 다른 schema에 접근할 가능성이 있고 instance 장애와 자원 경합을 공유한다.

서비스별 database를 만들면 기본적인 namespace가 더 강하게 나뉜다. 다른 database의 테이블을 일반 SQL로 직접 join하기도 어려워진다. 하지만 PostgreSQL process와 CPU, memory, disk, 장애 영역은 여전히 공유한다.

| 항목 | Schema 분리 | Database 분리 |
|---|---|---|
| 설정 난이도 | 낮음 | 중간 |
| connection 관리 | 단순 | 서비스별 URL 필요 |
| cross-schema 접근 | 권한이 있으면 쉬움 | 기본적으로 어려움 |
| instance 장애 격리 | 없음 | 없음 |
| 자원 경합 격리 | 없음 | 제한적 |

현재 단계에서는 schema와 role 분리로 시작하고 권한 테스트가 완성된 뒤 database 분리를 실험하는 것이 적절하다.

## Instance와 VM 분리는 언제 필요한가

다음 요구가 실제로 생기면 별도 instance 또는 VM을 고려한다.

- 특정 서비스 I/O가 다른 서비스 latency에 영향을 준다.
- 서비스별 backup과 Point-in-Time Recovery 정책이 다르다.
- 서로 다른 PostgreSQL version이나 extension이 필요하다.
- OS 또는 network 수준의 보안 격리가 필요하다.
- 한 서비스 DB 장애가 다른 서비스에 영향을 주면 안 된다.
- 독립적인 scaling과 failover가 필요하다.

VM을 나누더라도 모두 같은 Windows 호스트의 같은 물리 디스크를 사용하면 호스트와 디스크 장애는 공유한다. 논리적으로 VM이 여러 개라는 사실만으로 완전한 물리 HA가 되지는 않는다.

## Postgres Operator를 바로 도입하지 않는 이유

Postgres Operator는 PostgreSQL cluster 생성, replication, failover, backup, upgrade 같은 수명주기를 Kubernetes에서 자동화한다.

하지만 다음 문제를 자동으로 해결하지는 않는다.

- 어떤 서비스가 어떤 데이터를 소유하는가
- 어떤 계정에 어떤 권한을 줄 것인가
- 서비스 간 데이터 변경을 API로 할지 이벤트로 할지
- 감사 로그에 어떤 application identity를 남길 것인가

현재 환경에서는 먼저 PostgreSQL 자체의 role, privilege, backup, restore를 이해한 뒤 Operator를 도입하는 편이 학습 효과와 문제 분리에 유리하다.

## 누가 어떤 계정으로 INSERT했는지 확인하기

PostgreSQL 기본 로그에 connection과 statement 정보를 남길 수 있다. 운영 부하와 개인정보를 고려해 설정해야 한다.

```conf
log_connections = on
log_disconnections = on
log_line_prefix = '%m [%p] user=%u db=%d app=%a client=%h '
```

모든 SQL을 무조건 기록하는 `log_statement = 'all'`은 로그 양과 민감정보 노출 위험이 크다. 실습에서는 제한적으로 사용할 수 있지만 운영에서는 `pgaudit`, slow query 기준, 애플리케이션 audit event를 조합하는 편이 낫다.

애플리케이션의 JDBC URL 또는 connection pool에 `ApplicationName`을 지정하면 같은 DB 계정이라도 서비스 이름을 로그에 남길 수 있다. 그러나 가장 명확한 구분은 서비스별 runtime role을 사용하는 것이다.

## 반드시 작성할 권한 테스트

| 테스트 | 기대 결과 |
|---|---|
| `community_app`의 Community SELECT/INSERT | 성공 |
| `community_app`의 Stock SELECT | 권한 오류 |
| `community_app`의 Stock INSERT | 권한 오류 |
| `stock_app`의 Community UPDATE | 권한 오류 |
| Runtime 계정의 `ALTER TABLE` | 권한 오류 |
| Migrator 계정의 자기 schema Flyway | 성공 |
| Community 이벤트를 Stock Consumer가 처리 | 성공 |
| Stock Consumer가 `stock_app`으로 INSERT | 성공 |

이 테스트가 통과해야 파일 이름과 schema 이름만 나눈 것이 아니라 실제 접근 경계가 만들어졌다고 말할 수 있다.

## 단계별 적용 계획

1. Storage VM 한 대와 PostgreSQL instance 한 개를 유지한다.
2. 서비스별 schema를 유지한다.
3. 서비스별 migrator와 runtime role을 만든다.
4. Flyway와 애플리케이션의 credential을 분리한다.
5. 다른 서비스 schema 접근 차단 테스트를 작성한다.
6. 서비스 간 변경은 API 또는 Kafka Outbox로만 처리한다.
7. 실제 I/O와 장애 요구를 측정한다.
8. 필요할 때 database, instance, VM 순서로 격리 수준을 높인다.
9. 자동 failover와 운영 자동화가 목표가 되면 Postgres Operator를 비교한다.

## 결론

MSA 데이터베이스 분리는 VM 개수를 늘리는 작업에서 시작하지 않는다. 데이터 소유권과 최소 권한을 코드와 DB 양쪽에서 강제하는 것부터 시작한다.

현재 프로젝트에는 다음 구성이 가장 현실적이다.

```text
Storage VM 1대
PostgreSQL instance 1개
서비스별 schema
서비스별 migrator role
서비스별 runtime role
서비스 간 직접 DB 접근 금지
서비스 간 변경은 API 또는 Kafka Outbox
```

이 경계를 테스트로 증명한 뒤 실제 자원 경합, backup, failover 요구에 따라 물리적 분리를 늘려 가는 것이 비용과 학습 측면에서 가장 합리적이다.

## 참고 자료

- [PostgreSQL Schemas](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [PostgreSQL Privileges](https://www.postgresql.org/docs/current/ddl-priv.html)
- [PostgreSQL Role Membership](https://www.postgresql.org/docs/current/role-membership.html)
- [PostgreSQL Error Reporting and Logging](https://www.postgresql.org/docs/current/runtime-config-logging.html)
