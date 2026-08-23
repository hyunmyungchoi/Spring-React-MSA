# Spring React MSA 문서

현재 기준은 VMware 기반 온프레미스 환경이다. AWS 문서는 과거 기록으로 남겨두지만 현재 설치·운영 절차에서는 제외한다.

## 읽는 순서

1. [온프레미스 아키텍처](architecture/overview.md)
2. [MSA 구조](architecture/msa-structure.md)
3. [인증 흐름](architecture/authentication-flow.md)
4. [Kafka Outbox](architecture/kafka-outbox.md)
5. [Kubernetes 배포](runbooks/kubernetes-deployment.md)
6. [운영 검증](runbooks/on-premise-validation.md)
7. [로컬 개발](runbooks/local-development.md)
8. [Kafka VM](runbooks/vm-kafka.md)
9. [Argo CD](runbooks/argocd-deployment.md)
10. [인증 테스트](testing/authentication-test.md)

## 현재 구성

- Ubuntu Server 24.04, kubeadm Kubernetes 1.36.3
- Cilium 1.20.0, containerd
- application: worker-1/2
- platform + observability: worker-platform-observability
- PostgreSQL/Redis: Storage VM
- Kafka: 별도 Kafka VM
- Argo CD 수동 Sync, GHCR image

Secret, Cookie, OAuth code, 비밀번호는 문서와 로그에 남기지 않는다.
