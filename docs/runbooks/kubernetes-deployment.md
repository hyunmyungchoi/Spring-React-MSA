# Kubernetes 배포 런북

## 범위와 전제

현재 `infra/k8s`는 `localtest.me` 기반 로컬 Kubernetes 환경용이다. AWS ECS 배포 소스가 아니다. Docker Desktop Kubernetes 같은 단일 로컬 cluster, ingress-nginx, 기본 `standard` StorageClass를 전제로 한다.

## 1. Cluster 확인

```powershell
kubectl config current-context
kubectl cluster-info
kubectl get nodes
kubectl get storageclass
```

의도한 local context인지 확인하기 전에는 apply/delete를 실행하지 않는다.

## 2. ingress-nginx

```powershell
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/cloud/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=180s
kubectl get ingressclass
```

`nginx` IngressClass가 보여야 한다.

## 3. Secret 준비

```powershell
Copy-Item C:\Portfolio\infra\k8s\spring-msa\examples\02-secrets.example.yaml `
  C:\Portfolio\infra\k8s\spring-msa\02-secrets.local.yaml
```

파일의 placeholder를 로컬 값으로 교체한다. 이 파일은 `.gitignore` 대상이다. 다음 항목이 서로 일치해야 한다.

- BFF plain client secret과 Auth Server BCrypt client secret hash
- issuer와 외부 Gateway URL
- member/admin redirect 및 post-logout URL
- internal API token
- PostgreSQL/Redis/Toss 자격 증명
- `ghcr-secret`의 GitHub 사용자명, package read 권한 token, `username:token` Base64 값

예제 파일은 `spring-msa-secret`, `spring-msa-oauth-secret`, `ghcr-secret` 세 Secret을 모두 포함한다. `change-me-*` placeholder가 하나라도 남은 상태로 Apply하지 않는다.

```powershell
kubectl apply -f C:\Portfolio\infra\k8s\spring-msa\00-namespace.yaml
kubectl apply -f C:\Portfolio\infra\k8s\spring-msa\02-secrets.local.yaml
```

Secret은 Argo CD가 추적하는 Git 경로에 포함되지 않으므로 cluster에 별도로 유지해야 한다.

## 4. Kafka

```powershell
kubectl apply -f C:\Portfolio\infra\k8s\kafka\00-namespace.yaml
kubectl apply -f C:\Portfolio\infra\k8s\kafka\05-kafka.yaml
kubectl apply -f C:\Portfolio\infra\k8s\kafka\10-kafka-exporter.yaml
kubectl rollout status statefulset/kafka -n kafka --timeout=300s
```

Kafka는 단일 broker/KRaft controller와 1Gi PVC인 로컬 구성이다.

## 5. Spring MSA 수동 apply

Argo CD 없이 최초 확인할 때 다음 순서로 적용한다.

```powershell
$root = "C:\Portfolio\infra\k8s\spring-msa"
kubectl apply -f "$root\00-namespace.yaml"
kubectl apply -f "$root\01-configmap.yaml"
kubectl apply -f "$root\02-secrets.local.yaml"
kubectl apply -f "$root\03-00-postgres-pvc.yaml"
kubectl apply -f "$root\03-01-postgres-headless-service.yaml"
kubectl apply -f "$root\03-02-postgres-statefulset.yaml"
kubectl apply -f "$root\03-03-postgres-service.yaml"
kubectl apply -f "$root\04-redis.yaml"
kubectl apply -f "$root\10-user-service.yaml"
kubectl apply -f "$root\11-community-service.yaml"
kubectl apply -f "$root\12-stock-service.yaml"
kubectl apply -f "$root\13-auth-server.yaml"
kubectl apply -f "$root\20-member-bff-service.yaml"
kubectl apply -f "$root\21-admin-bff-service.yaml"
kubectl apply -f "$root\30-member-gateway.yaml"
kubectl apply -f "$root\31-admin-gateway.yaml"
kubectl apply -f "$root\40-web.yaml"
kubectl apply -f "$root\50-ingress.yaml"
```

## 6. 상태 확인

```powershell
kubectl get pods,svc,ingress,pvc -n spring-msa
kubectl rollout status statefulset/postgres -n spring-msa --timeout=300s
kubectl rollout status deployment/spring-member-bff-service -n spring-msa --timeout=300s
kubectl rollout status deployment/spring-admin-bff-service -n spring-msa --timeout=300s
```

실패 pod:

```powershell
kubectl describe pod <pod-name> -n spring-msa
kubectl logs <pod-name> -n spring-msa --all-containers --tail=200
```

접속 확인:

```powershell
Invoke-WebRequest http://user.localtest.me
Invoke-WebRequest http://admin.localtest.me
```

## 7. 관측성

```powershell
Set-Location C:\Portfolio\infra\k8s\observability
.\scripts\install-observability.ps1
.\scripts\check-observability.ps1
```

chart version은 설치 스크립트에 고정돼 있다.

## 배포 중단 기준

- ImagePullBackOff 또는 readiness 실패가 지속됨
- OAuth issuer/redirect 불일치
- PostgreSQL migration/validation 실패
- Member BFF 2 replicas 중 하나라도 반복 재시작
- Kafka topic/DLT 또는 Redis 연결 실패로 핵심 기능이 예상과 다름

중단 시 새 변경을 더 적용하지 말고 [rollback.md](rollback.md)를 따른다.
