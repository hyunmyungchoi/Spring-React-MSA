# Argo CD 배포 런북

## 현재 정책

Argo CD Application `spring-msa`는 GitHub `master` branch의 `infra/k8s/spring-msa`를 감시한다. 자동 Sync는 비활성이다. GitHub Actions가 image SHA를 commit하면 Application은 OutOfSync가 되고 운영자가 Sync해야 배포된다.

## 설치

```powershell
kubectl apply -f C:\Portfolio\infra\k8s\argocd\00-namespace.yaml
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl patch configmap argocd-cmd-params-cm -n argocd --type merge --patch-file C:\Portfolio\infra\k8s\argocd\10-cmd-params-patch.json
kubectl rollout restart deployment/argocd-server -n argocd
kubectl rollout status deployment/argocd-server -n argocd
kubectl apply -f C:\Portfolio\infra\k8s\argocd\20-ingress.yaml
kubectl apply -f C:\Portfolio\infra\k8s\argocd\30-spring-msa-application.yaml
```

`stable` 원격 URL은 시간이 지나면 내용이 변한다. 재현 가능한 운영 설치에서는 검증된 Argo CD release version URL 또는 Helm chart version으로 고정한다.

접속: `http://argocd.localtest.me`

초기 비밀번호:

```powershell
[System.Text.Encoding]::UTF8.GetString(
  [System.Convert]::FromBase64String(
    (kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}")
  )
)
```

초기 로그인 후 password를 바꾸고 local 외 환경에서는 TLS/SSO/RBAC를 적용한다.

## 배포 절차

1. GitHub Actions `Build and Push Images to GHCR`가 성공했는지 확인한다.
2. GHCR에 대상 `${github.sha}` image가 있는지 확인한다.
3. bot commit이 의도한 Kubernetes image line만 변경했는지 검토한다.
4. Argo CD에서 `spring-msa`가 OutOfSync인지 확인한다.
5. Diff에서 예상 resource/image만 바뀌었는지 확인한다.
6. Sync를 실행한다.
7. resource health가 Healthy/Synced가 될 때까지 관찰한다.
8. 로그인, `/auth/me`, 대상 기능 smoke test를 수행한다.

CLI가 설치돼 있다면:

```powershell
argocd app diff spring-msa
argocd app sync spring-msa
argocd app wait spring-msa --health --sync --timeout 300
```

CLI가 없으면 UI에서 Diff와 Sync를 사용한다.

## Secret 주의

`02-secrets.local.yaml`은 Git에서 무시되므로 Argo CD Application이 생성하지 않는다. Secret이 없거나 key가 부족하면 Deployment가 시작되지 않는다.

```powershell
kubectl get secret -n spring-msa
kubectl describe deployment <deployment> -n spring-msa
```

운영 GitOps에서는 Sealed Secrets, External Secrets 또는 cloud secret manager 연동을 도입한다.

## Sync 실패

```powershell
kubectl get application spring-msa -n argocd -o yaml
kubectl get events -n spring-msa --sort-by=.lastTimestamp
kubectl get pods -n spring-msa
```

- ComparisonError: repo URL/branch/path 및 repository 접근 확인
- Missing: namespace/CRD/Secret 선행 적용 확인
- Degraded: pod describe/log와 readiness 확인
- Progressing 장기화: image pull, PVC, probe, rollout 상태 확인

실패한 live 상태만 임의 수정하지 않는다. 긴급 수정 후에는 동일 변경을 Git에 반영해 drift를 제거한다.

## 배포 승인 체크

- [ ] CI test와 Docker build 성공
- [ ] Git SHA image 존재
- [ ] manifest에 `latest` 없음
- [ ] Argo diff가 의도한 범위
- [ ] Secret/ConfigMap 준비
- [ ] rollback SHA 확인
- [ ] Sync 후 health/smoke test 성공
