# Spring React MSA 문서

이 디렉터리는 `C:\Portfolio` 저장소의 코드와 인프라를 기준으로 작성한 설계·운영 문서의 시작점이다. 문서의 기준일은 2026-07-19이며, 구현과 문서가 충돌하면 실행 가능한 코드와 배포 매니페스트를 우선 확인한다.

## 문서 읽는 순서

1. [시스템 개요](architecture/overview.md)
2. [MSA 구성](architecture/msa-structure.md)
3. [인증 흐름](architecture/authentication-flow.md)
4. [회원·관리자 BFF](architecture/member-admin-bff.md)
5. [채팅 아키텍처](architecture/chat-architecture.md)
6. [CI/CD와 배포](architecture/cicd-deployment.md)
7. [Kubernetes↔AWS 재해 복구](architecture/disaster-recovery.md)
8. 필요한 [스펙](specs/), [운영 런북](runbooks/), [테스트 문서](testing/) 확인

## 현재 기술 기준선

| 영역 | 기준 |
| --- | --- |
| Backend | Java 17, Spring Boot 4.0.6, Spring Cloud 2025.1.1 |
| JSON | Jackson 3 (`tools.jackson.*`) |
| Build | Gradle Wrapper 9.3.0, dependency lock, verification metadata |
| Frontend | React 19.2.6, TypeScript 6.0.3, Vite 8.0.13 |
| Runtime | Node 24.18.0, pnpm 10.0.0, 단일 workspace lockfile |
| Data | PostgreSQL 16, Redis 7 |
| Messaging | Kafka 3.7.0 |
| Platform | Docker Compose, Kubernetes, ingress-nginx, GHCR, Argo CD |
| AWS migration | Runtime OFF·RDS 정지 유지; 관측성 Policy 복구 Apply `6/0/0`·재계획 `No changes`, Email 확인 대기 |
| Observability | Kubernetes Prometheus·Grafana·Loki와 AWS CloudWatch Log·Budget·RDS Alarm/SNS 계약 |

## 문서 상태 표현

- **현재**: 저장소에서 동작이 확인되는 구현이다.
- **목표**: 계획 또는 ADR에서 채택했지만 아직 구현 완료되지 않은 상태다.
- **위험**: 운영 전 해소하거나 명시적으로 수용해야 하는 항목이다.

특히 다음 항목은 현재와 목표가 다르다.

- 채팅 이벤트는 트랜잭션 커밋 후 Kafka로 전송하지만 영속 Outbox 테이블과 relay는 아직 없다.
- 커뮤니티 게시물은 프로세스 메모리에 저장되어 재시작 시 사라진다.
- Argo CD Application에 자동 동기화가 설정되어 있지 않아 Git 변경 후 수동 Sync가 필요하다.
- Admin BFF의 관리자 가입 Controller는 설정 Flag로 제어하며 `prod` 기본값은 비활성이다. 로컬 Kubernetes만 명시적으로 활성화하고 AWS ECS는 비활성으로 고정한다. 최초 관리자 Bootstrap 절차는 아직 남아 있다.
- Admin Session 조회 응답과 Frontend 타입에서 원본 `sessionId`를 제거하고 SHA-256 `sessionFingerprint`만 사용한다.
- GHCR Build Once와 ECR Digest Promote Workflow는 구현됐고, Database Migration 대상 3개 Image에서 재빌드 없는 Promote와 Digest 일치를 실제 검증했다.
- Kubernetes↔AWS DR은 Learning 적용 범위에서 제외하고 후속 학습 과제로 보류했다.

## 문서 지도

### Architecture

- [overview.md](architecture/overview.md): 시스템 경계와 전체 요청 흐름
- [msa-structure.md](architecture/msa-structure.md): 서비스, 포트, 저장소, 의존 관계
- [authentication-flow.md](architecture/authentication-flow.md): OAuth2/OIDC, 세션, CSRF, 토큰 전달
- [member-admin-bff.md](architecture/member-admin-bff.md): 두 BFF의 책임과 차이
- [chat-architecture.md](architecture/chat-architecture.md): WebSocket, PostgreSQL, Redis, Kafka
- [cicd-deployment.md](architecture/cicd-deployment.md): GitHub Actions, GHCR, Kubernetes, Argo CD
- [disaster-recovery.md](architecture/disaster-recovery.md): Kubernetes와 AWS 사이의 장애 전환·원복 목표

### Decisions

- [ADR-001](decisions/ADR-001-separate-member-admin-bff.md): 회원·관리자 BFF 분리
- [ADR-002](decisions/ADR-002-session-based-bff-auth.md): 세션 기반 BFF 인증
- [ADR-003](decisions/ADR-003-kafka-outbox-chat.md): 채팅 Kafka Outbox 목표
- [ADR-004](decisions/ADR-004-ghcr-immutable-image-tag.md): Git SHA 추적과 OCI Digest 무결성
- [ADR-005](decisions/ADR-005-argocd-gitops.md): Argo CD GitOps
- [ADR-006](decisions/ADR-006-reversible-k8s-aws-dr.md): 단일 writer 기반 K8s↔AWS 재해 복구 보류 제안

### Specs

- [authentication.md](specs/authentication.md)
- [member-service.md](specs/member-service.md)
- [admin-service.md](specs/admin-service.md)
- [stock-service.md](specs/stock-service.md)
- [realtime-chat.md](specs/realtime-chat.md)

### Plans

- [Member BFF 계획](plans/2026-07-17-member-bff-plan.md)
- [Admin BFF 계획](plans/2026-07-17-admin-bff-plan.md)
- [채팅 계획](plans/2026-07-17-chat-plan.md)
- [관측성 계획](plans/2026-07-17-observability-plan.md)
- [Kubernetes↔AWS DR 계획](plans/2026-07-17-k8s-aws-dr-plan.md)

### Runbooks

- [로컬 개발](runbooks/local-development.md)
- [Kubernetes 배포](runbooks/kubernetes-deployment.md)
- [Argo CD 배포](runbooks/argocd-deployment.md)
- [롤백](runbooks/rollback.md)
- [공통 오류](runbooks/common-errors.md)
- [AWS Application Runtime](runbooks/aws-application-runtime.md)
- [AWS Learning RDS 운영·복구](runbooks/aws-rds-learning.md)
- [AWS DB Bootstrap·Flyway 실행](runbooks/aws-database-bootstrap-and-flyway.md)
- [AWS Image Build Once·ECR Promote](runbooks/aws-image-build-once-promote.md)
- [AWS Frontend S3·CloudFront 배포](runbooks/aws-frontend-hosting.md)
- [AWS Route 53·ACM·TLS·API Origin](runbooks/aws-domain-tls.md)
- [AWS 관측성 Foundation](runbooks/aws-observability.md)
- [Kubernetes에서 AWS로 장애 전환](runbooks/k8s-to-aws-failover.md)
- [AWS에서 Kubernetes로 원복](runbooks/aws-to-k8s-failback.md)

### Testing

- [테스트 전략](testing/test-strategy.md)
- [인증 테스트](testing/authentication-test.md)
- [부하 테스트](testing/load-test.md)

### AWS Migration

`aws-migration/`은 현재 아키텍처를 다시 정의하는 문서가 아니라 AWS 목표 환경의 차이, 준비 상태와 실행 기록을 관리한다. 서비스 동작은 위 Architecture/Specs를 기준으로 하고, 실제 AWS 적용 상태와 명령은 [`infra/aws/terraform/README.md`](../infra/aws/terraform/README.md)를 우선한다.

| 문서 | 역할 | 현재 상태 |
| --- | --- | --- |
| [서비스 인벤토리](aws-migration/00-service-inventory.md) | ECS 대상 서비스와 환경 변수 | 저장소 기준 확인됨 |
| [리소스 기준선](aws-migration/01-resource-baseline.md) | ECS on EC2 초기 용량 가정 | EC2 1대 배치 검증 완료, 부하 검증 필요 |
| [환경 매트릭스](aws-migration/02-environment-matrix.md) | 로컬·K8s·AWS 설정 차이 | Runtime ON 실상태 검증 후 ECS/ASG 0·RDS 정지 완료 |
| [DB 전환 준비](aws-migration/03-database-migration.md) | RDS schema와 migration gap | Build Once·ECR Promote와 실제 RDS Flyway V1 3개 실행·검증 완료 |
| [AWS Foundation](aws-migration/04-aws-foundation-design.md) | VPC/subnet/SG 설계 | Foundation 유지, Runtime ON 검증 후 현재 OFF |
| [ECR/OIDC 설계](aws-migration/05-ecr-github-oidc-design.md) | SHA 이미지와 GitHub OIDC | Apply·GitHub 변수·Backend 8개 게시 완료 |
| [ECR/OIDC 구현 계획](aws-migration/06-ecr-github-oidc-implementation-plan.md) | 구현·승인 gate 실행 기록 | Task 6·단일/중복/전체 게시 검증 완료 |
| [Learning Runtime 결정](aws-migration/07-learning-runtime-design.md) | NAT, State, ECS, RDS, Frontend, Secret, DNS 결정 | Runtime ON HTTPS·OAuth·Session·WebSocket 검증과 후속 OFF 완료; 관측성 Apply 완료·Email 확인 대기 |

AWS 적용 여부는 Git만으로 확정할 수 없으므로 문서의 `저장소 상태`와 `AWS 적용 상태`를 구분한다. Terraform state, 저장 plan, 계정 식별자와 secret은 문서나 Git에 추가하지 않는다.
