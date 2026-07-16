# ADR-005: Kubernetes desired state는 Argo CD GitOps로 관리한다

- 상태: 승인
- 결정일: 2026-07-17
- 구현 상태: 부분 적용 — Application은 존재하며 자동 Sync는 비활성

## 배경

CI가 cluster에 직접 `kubectl apply`하면 배포 권한이 GitHub Actions에 집중되고, 실제 cluster 상태와 Git manifest가 어긋날 수 있다. image build와 runtime 적용 책임을 분리하고 Git 변경 이력을 배포 감사 기록으로 사용하고자 한다.

## 결정

- GitHub Actions는 test, image build/push, Kubernetes image tag 변경까지만 담당한다.
- Argo CD는 `master` branch의 `infra/k8s/spring-msa`를 desired state로 본다.
- destination은 in-cluster `spring-msa` namespace다.
- 배포 변경은 Git commit으로 남기고 Argo CD가 diff와 sync를 담당한다.
- rollback은 원칙적으로 이전 정상 image SHA를 복원하는 Git revert/commit으로 수행한다.

## 현재 동작

Application manifest에는 `CreateNamespace=true`가 있으나 `syncPolicy.automated`는 없다. 따라서 Argo CD가 Git 변경을 감지해 OutOfSync로 표시하지만 operator가 UI 또는 CLI에서 Sync해야 실제 rollout이 시작된다.

이는 승인 없는 자동 반영을 피하는 초기 안전 정책으로 본다. 문서나 운영 절차에서 현재 상태를 자동 배포라고 표현하지 않는다.

## 결과

### 장점

- cluster 변경의 근거가 Git에 남는다.
- CI에 cluster-admin credential을 둘 필요가 없다.
- desired/live diff와 resource health를 한 화면에서 확인한다.
- Git 기반 rollback과 환경 재구성이 가능하다.

### 비용

- Argo CD 자체 설치·접근 제어·백업·업그레이드가 필요하다.
- GitHub Actions bot commit과 사람이 작성한 manifest 변경의 충돌을 관리해야 한다.
- 자동 Sync가 없으면 승인 대기 시간이 생긴다.
- Secret을 평문 Git에 저장할 수 없어 별도 Secret 관리가 필요하다.

## 자동 Sync 전환 조건

1. production과 local cluster/Application 분리
2. Secret 관리 도구 결정
3. sync window와 승인 정책 정의
4. health check와 smoke test 확립
5. `prune`, `selfHeal`의 영향 검증
6. rollback 및 장애 대응 훈련

조건을 충족하면 환경별로 `automated.prune`과 `automated.selfHeal`을 선택적으로 활성화한다. production은 PR 승인과 promotion branch/tag를 별도로 두는 방안을 권장한다.
