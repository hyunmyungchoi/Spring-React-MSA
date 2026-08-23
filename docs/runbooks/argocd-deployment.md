# Argo CD 런북

- Application: spring-msa
- Branch: master
- Path: infra/k8s/spring-msa
- Sync: 수동
- Node: worker-platform-observability
- URL: http://argocd.localtest.me

## 상태

~~~bash
kubectl get application spring-msa -n argocd
kubectl get pods -n argocd -o wide
~~~

정상은 Synced, Healthy다. Healthy, OutOfSync는 live와 Git 선언이 다르다는 뜻이다.

2026-08-10 현재 Application은 `Healthy / OutOfSync`다. 애플리케이션은 정상 동작하지만 최초 수동 배포와 이후 node placement·운영 patch가 Git 선언과 달라 전체 resource가 drift로 표시된다. 자동 Sync하지 말고 Diff를 먼저 검토한 뒤 수동 Sync한다.

## 배포

1. CI와 GHCR image를 확인한다.
2. manifest 변경을 master에 push한다.
3. Argo CD Diff를 확인한다.
4. 수동 Sync한다.
5. Healthy, Synced를 확인한다.
6. infra/ci/k8s-live-smoke.ps1을 실행한다.

02-secrets.local.yaml은 Git에 없으므로 Argo CD가 만들지 않는다. live 긴급 수정은 반드시 Git에도 반영한다.
