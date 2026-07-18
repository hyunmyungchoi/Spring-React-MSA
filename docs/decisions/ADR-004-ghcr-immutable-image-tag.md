# ADR-004: Source SHA를 추적하고 OCI Digest로 배포 이미지를 고정한다

- 상태: 승인
- 결정일: 2026-07-17
- 구현 상태: Workflow·검증 코드·GitHub `master` 반영 완료, Database Migration Image 3개 GHCR Build Once·ECR Promote와 Digest 일치 실제 검증 완료

## 배경

`latest` 같은 이동 태그는 같은 Kubernetes Manifest가 시간에 따라 다른 Image를 가리킬 수 있어 감사와 Rollback을 어렵게 한다. Git SHA는 Source Revision 추적에는 적합하지만 서로 다른 시점에 두 번 Build한 Image가 동일한 Binary임을 증명하지는 않는다. Source 추적용 Tag와 Binary 무결성용 Digest를 함께 사용해야 한다.

## 결정

현재 GitHub Actions는 각 Image를 GHCR에 다음 두 Tag로 기록한다.

- `latest`: 편의용 이동 태그
- `${github.sha}`: Source 추적용 불변 태그

CI의 `update-k8s-image-tags.py`는 선택한 Kubernetes Image를 GHCR 최상위 OCI Digest로 고정하고, Manifest에 GHCR `:latest`가 남으면 Pipeline을 실패시킨다. Database Migration 대상 3개 Manifest는 실제 검증된 Digest를 사용하며 나머지는 다음 서비스별 Build Once 시 같은 방식으로 전환한다.

구현한 동작은 다음과 같다.

1. GHCR Workflow가 서비스와 Source SHA당 한 번만 Build한다.
2. Git SHA Tag와 최상위 OCI Manifest Digest를 기록한다.
3. ECR Workflow는 명시적으로 받은 `source_sha`의 GHCR Image를 재빌드하지 않고 Promote한다.
4. GHCR과 ECR의 최상위 Digest가 같을 때만 성공한다.
5. Kubernetes는 GHCR Digest, ECS는 동일한 ECR Digest를 사용한다.
6. 동일 SHA Tag가 기존 Registry에 있으면 Digest가 같을 때만 Skip하고 다르면 무결성 오류로 실패한다.

외부 Base/Runtime Image는 Tag만 쓰지 않고 `@sha256:` Digest까지 고정한다. Helm Chart도 명시적 Version을 사용한다.

## 결과

### 장점

- 배포 image와 source commit을 바로 연결할 수 있다.
- GHCR과 ECR이 동일한 Binary임을 Digest로 검증할 수 있다.
- AWS 게시를 위해 같은 Source를 다시 Build하지 않는다.
- 이전 정상 SHA로 manifest를 되돌려 결정적 rollback이 가능하다.
- Argo CD diff에서 image 변경이 명시적으로 보인다.
- node/Java base image의 예기치 않은 재해석을 막는다.

### 비용

- Git SHA 태그가 누적되므로 GHCR retention 정책이 필요하다.
- 보안 패치 base image로 이동하려면 digest 갱신 PR이 필요하다.
- Build Once·Promote Workflow와 Kubernetes/ECS Digest 참조를 구현해야 한다.
- Multi-architecture Image는 Platform별 Digest가 아닌 최상위 OCI Index Digest를 비교해야 한다.

## 운영 규칙

- 이미 Push한 Git SHA Tag에 다른 Manifest를 덮어쓰지 않는다.
- Kubernetes에서 `imagePullPolicy: Always`에 기대어 이동 태그를 사용하지 않는다.
- rollback 후보 SHA가 retention으로 삭제되지 않도록 최근 정상 release를 보호한다.
- GHCR/ECR Binary 동일성은 실제 Promote Run에서 두 Registry의 최상위 Digest가 같은 경우에만 기록한다.
- Kubernetes와 ECS Task Definition에는 검증된 Digest를 기록한다.
- SBOM, image signature, vulnerability scan은 후속 supply-chain 단계로 추가한다.

## 대안

1. semantic version: release 의미는 좋지만 모든 commit 자동 배포와 연결하려면 추가 versioning 단계가 필요하다.
2. build number: CI 실행과 연결되지만 source revision 추적이 한 단계 더 필요하다.
3. digest-only: 가장 강하지만 Source 추적성이 약해질 수 있으므로 Git SHA Tag를 추적용으로 함께 유지한다.
