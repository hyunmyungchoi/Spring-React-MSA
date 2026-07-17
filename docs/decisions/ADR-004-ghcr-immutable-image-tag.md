# ADR-004: 배포 이미지는 Git SHA 태그로 고정한다

- 상태: 승인
- 결정일: 2026-07-17
- 구현 상태: 적용됨 — registry의 tag immutability 강제는 별도 과제

## 배경

`latest` 같은 이동 태그는 같은 Kubernetes manifest가 시간에 따라 다른 image를 가리킬 수 있어 감사와 rollback을 어렵게 한다. 애플리케이션 source revision과 image revision을 연결할 수 있는 고정 식별자가 필요하다.

## 결정

GitHub Actions가 각 image를 GHCR에 다음 두 태그로 push한다.

- `latest`: 편의용 이동 태그
- `${github.sha}`: Kubernetes 배포용 고정 태그

Kubernetes manifest에는 Git SHA 태그만 기록한다. CI의 `update-k8s-image-tags.py`가 선택한 image line을 갱신하고, manifest에 GHCR `:latest`가 남으면 pipeline을 실패시킨다.

외부 base/runtime image는 tag만 쓰지 않고 `@sha256:` digest까지 고정한다. Helm chart도 명시적 version을 사용한다.

## 결과

### 장점

- 배포 image와 source commit을 바로 연결할 수 있다.
- 이전 정상 SHA로 manifest를 되돌려 결정적 rollback이 가능하다.
- Argo CD diff에서 image 변경이 명시적으로 보인다.
- node/Java base image의 예기치 않은 재해석을 막는다.

### 비용

- Git SHA 태그가 누적되므로 GHCR retention 정책이 필요하다.
- 보안 패치 base image로 이동하려면 digest 갱신 PR이 필요하다.
- 같은 SHA 태그 overwrite를 registry가 막지 않으면 규칙 위반 가능성이 남는다.

## 운영 규칙

- 이미 push한 Git SHA 태그에 다른 manifest를 덮어쓰지 않는다.
- Kubernetes에서 `imagePullPolicy: Always`에 기대어 이동 태그를 사용하지 않는다.
- rollback 후보 SHA가 retention으로 삭제되지 않도록 최근 정상 release를 보호한다.
- 더 강한 무결성이 필요하면 최종 image manifest digest를 Kubernetes에 기록한다.
- SBOM, image signature, vulnerability scan은 후속 supply-chain 단계로 추가한다.

## 대안

1. semantic version: release 의미는 좋지만 모든 commit 자동 배포와 연결하려면 추가 versioning 단계가 필요하다.
2. build number: CI 실행과 연결되지만 source revision 추적이 한 단계 더 필요하다.
3. digest-only: 가장 강하지만 사람이 읽기 어렵고 현재 update script 변경이 필요하다.
