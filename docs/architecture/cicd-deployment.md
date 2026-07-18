# CI/CD와 배포

## 파이프라인 개요

```mermaid
flowchart LR
    PR["Pull Request"] --> V["verify.yml"]
    V --> T["Gradle tests / frontend lint+build"]
    T --> DB["Docker build without push"]
    M["main/master push"] --> P["ghcr-build-push.yml"]
    P --> S["변경 서비스 선택"]
    S --> Q["테스트와 Docker build"]
    Q --> G["GHCR latest + github.sha"]
    G --> U["Kubernetes manifest SHA tag 갱신"]
    U --> C["bot commit [skip ci]"]
    C --> A["Argo CD OutOfSync"]
    A --> K["수동 Sync 후 rollout"]
```

## 변경 범위 선택

`infra/ci/select-build-matrix.py`가 Git diff를 다음 원칙으로 매핑한다.

- 서비스 디렉터리 변경: 해당 backend image
- `spring-msa-common-web`: Auth, User, Member BFF, Admin BFF
- `spring-msa-common-kafka`: Member BFF
- member/admin 공통 프런트 파일: 각 workspace의 모든 feature image
- 특정 feature 소스/Dockerfile: 해당 feature image
- 수동 실행: `deploy_target` 하나 또는 `all`

매핑 자체는 `infra/ci/test_select_build_matrix.py`로 검증한다.

## 검증 단계

Backend job은 Java 17로 각 서비스의 `./gradlew test`를 실행한다. Wrapper 9.3.0, dependency lock, verification metadata가 저장소에 있으므로 잠기지 않거나 검증되지 않은 artifact는 실패해야 한다.

Frontend job은 Node 24.18.0과 Corepack을 사용해 pnpm 10.0.0을 설치하고 루트에서 `pnpm install --frozen-lockfile`, workspace별 lint와 `build:all`을 실행한다. 이후 모든 선택 이미지에 대해 Docker build를 수행한다.

## 이미지 게시

GHCR에는 두 태그가 게시된다.

- `latest`: 사람이 확인하기 위한 이동 태그이며 배포에는 사용하지 않는다.
- `${github.sha}`: Kubernetes 매니페스트가 사용하는 고정 태그다.

`update-k8s-image-tags.py`는 선택한 컨테이너의 image line을 Git SHA 태그로 변경한다. workflow는 `:latest`가 매니페스트에 남아 있으면 실패하고, 변경을 `github-actions[bot]`으로 commit/push한다.

Git SHA 태그는 운영 규칙상 immutable로 취급한다. GHCR에서 tag overwrite를 기술적으로 막는 정책은 이 저장소에 없으므로 같은 SHA 태그를 다른 내용으로 재게시하지 않는 통제가 필요하다. 더 강한 보장이 필요하면 manifest digest 배포로 전환한다.

## Argo CD

Application은 `master` branch의 `infra/k8s/spring-msa`를 감시하고 `spring-msa` namespace를 대상으로 한다. 현재 `syncPolicy`에는 `CreateNamespace=true`만 있고 `automated`가 없다. 따라서 Git 변경을 감지해 OutOfSync가 되지만 실제 적용에는 UI 또는 CLI Sync가 필요하다.

자동 Sync를 도입할 때는 다음을 먼저 결정한다.

- `prune` 허용 여부
- `selfHeal` 허용 여부
- production 승인 gate
- Secret 관리 방식
- 실패 시 자동 rollback 대신 Git revert 원칙

## AWS ECR 전환 경로

최초 ECR 구현은 Kubernetes Delivery와 분리된 수동 재빌드 경로였으며 Backend 8개 게시 검증까지 완료했다. 현재 작업 트리에서는 이 Workflow를 Build Once·Promote 방식으로 교체했다. GitHub `master` 반영과 새 Source SHA의 실제 Promote 검증은 아직 남아 있다.

```mermaid
flowchart LR
    G["GHCR SHA Image"] --> D["최상위 OCI Digest 조회"]
    O["운영자 workflow_dispatch"] --> S["Backend 대상과 source_sha 선택"]
    S --> I["GitHub OIDC로 AWS role assume"]
    D --> C["crane copy: 재빌드 없음"]
    I --> C
    C --> V["GHCR/ECR Digest 비교"]
    V --> E["ECR SHA Tag와 Digest"]
    E -. "현재 소비자 없음" .-> X["미구현 ECS Task/Service"]
```

- `.github/workflows/ecr-build-push.yml`은 backend 8개 또는 Database Migration 대상 3개를 선택한다.
- ECR에는 `latest`를 발행하지 않고 전체 Git commit SHA만 사용한다.
- 최초 Terraform module, 저장 Plan Apply, GitHub 변수 연결과 Backend 8개 재빌드 게시 검증은 완료했다.
- ECR 전체 게시 기준은 SHA `3564959efa1637e60fe72f009d4fa1a5809de01b`, GitHub Actions run `29561837114`다.
- 새 Workflow는 `source_sha`의 GHCR Image를 `crane copy`하고, 기존 ECR Tag가 같은 Digest면 Skip하며 다르면 실패한다. 로컬 단위 테스트와 GitHub Actions Lint는 통과했지만 실제 새 Promote Run은 아직 없다.
- RDS/Secrets Terraform, DB Secret 초기화와 실제 RDS Role·Schema Bootstrap은 완료했고 Flyway SQL도 검증했다. 현재 Source를 포함한 불변 이미지 Promote와 ECS Flyway Migration Task는 아직 없으며 ECS Application Service, ALB, ElastiCache와 AWS 자동 배포도 아직 없다.
- 실제 적용 상태와 승인 gate는 [`infra/aws/terraform/README.md`](../../infra/aws/terraform/README.md)를 기준으로 한다.

GHCR→Kubernetes가 현재 delivery 기준이고 ECR→AWS는 migration lane이다. 한쪽 장애가 다른 쪽 image publication을 막지 않도록 workflow와 registry 권한을 독립적으로 유지한다.

### Build Once·Promote 구현

현재 재빌드 방식은 같은 Source SHA를 사용하더라도 Build 시점의 Base Image나 Package Repository 상태 때문에 GHCR과 ECR의 Binary가 달라질 수 있다. 이를 제거하기 위해 다음 전환을 승인했다.

```mermaid
flowchart LR
    S["Source SHA"] --> B["GHCR에서 한 번 Build"]
    B --> D["최상위 OCI Digest 기록"]
    D --> K["Kubernetes: GHCR Digest"]
    D --> P["재빌드 없는 ECR Promote"]
    P --> V["GHCR/ECR Digest 비교"]
    V --> E["ECS: ECR 동일 Digest"]
```

- GHCR Workflow가 서비스와 Source SHA당 Backend Image를 한 번만 Build한다.
- 무결성 기준은 Git SHA Tag가 아니라 최상위 OCI Manifest Digest다.
- ECR Workflow는 명시적으로 입력받은 `source_sha`의 GHCR Image를 재빌드하지 않고 복사한다.
- Promote 후 GHCR과 ECR Digest가 같아야 성공한다.
- 기존 ECR SHA Tag가 있으면 Digest가 같을 때만 Skip하고 다르면 실패한다.
- Kubernetes와 ECS는 각 Registry의 동일 Digest를 사용하며 `latest`는 배포 기준으로 사용하지 않는다.

구현과 로컬 검증은 완료했다. 실제 원격 적용 순서는 [AWS Learning Image Build Once·Promote Runbook](../runbooks/aws-image-build-once-promote.md)을 따른다.

## DR delivery 참고 원칙

Kubernetes↔AWS DR은 Learning 적용 범위에서 제외했다. 향후 운영형 DR을 다시 검토할 때는 Build Once·Promote로 검증된 동일 OCI Digest뿐 아니라 DB 복제, Write Fencing, DNS/TLS, Secret과 Smoke Test가 모두 준비돼야 한다. 자세한 목표 경계는 [재해 복구 아키텍처](disaster-recovery.md)를 참고하되 현재 실행 가능한 기능으로 간주하지 않는다.

## 버전 고정

- 외부 Docker base/runtime 이미지는 digest로 고정한다.
- 현재 애플리케이션 이미지는 Git SHA Tag로 추적한다. 목표 배포 기준은 Registry별 동일 OCI Digest다.
- Helm chart는 설치 스크립트에서 버전을 명시한다.
- Gradle distribution과 wrapper JAR은 checksum을 검증한다.
- pnpm lockfile은 workspace 루트 하나만 사용한다.

## 롤백

권장 롤백은 정상으로 확인된 이전 Git SHA image tag를 매니페스트에 다시 기록하고 commit한 뒤 Argo CD Sync하는 것이다. Kubernetes `rollout undo`는 긴급 복구에만 사용하고, 사용 후 Git desired state도 반드시 같은 이미지로 맞춘다. 자세한 절차는 [rollback runbook](../runbooks/rollback.md)을 따른다.
