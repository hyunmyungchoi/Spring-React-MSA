# Rocky Linux 9 k3s 배포

## 범위

이 문서는 VirtualBox의 Rocky Linux 9 Minimal 단일 노드 `k3s` 환경을 기준으로 한다.
명령은 Windows Termius SSH에서 실행하고 저장소 경로는 `C:\Project\SpringMSA`다.

현재 기본 StorageClass는 k3s의 `local-path`를 그대로 사용한다. `standard` 별칭 생성은 별도 작업으로 보류한다.

## 1. 클러스터 확인

```bash
kubectl get nodes -o wide
kubectl get storageclass
kubectl get pods -A
```

노드는 `Ready`, `local-path`는 기본 StorageClass여야 한다.

## 2. Secret 준비

Windows 저장소에서 예제 파일을 Git 비추적 로컬 파일로 복사하고 모든 `change-me` 값을 교체한다.

```powershell
Copy-Item C:\Project\SpringMSA\infra\k8s\spring-msa\examples\02-secrets.example.yaml `
  C:\Project\SpringMSA\infra\k8s\spring-msa\02-secrets.local.yaml
```

관리자 비밀번호는 UTF-8 기준 20~72바이트여야 한다. `ADMIN_BOOTSTRAP_REQUEST_ID`는 실행마다 추적 가능한 고유값을 사용한다.

## 3. 데이터 계층

```bash
ROOT=/path/to/SpringMSA/infra/k8s/spring-msa
kubectl apply -f "$ROOT/00-namespace.yaml"
kubectl apply -f "$ROOT/01-configmap.yaml"
kubectl apply -f "$ROOT/02-secrets.local.yaml"
kubectl apply -f "$ROOT/03-00-postgres-pvc.yaml"
kubectl apply -f "$ROOT/03-01-postgres-headless-service.yaml"
kubectl apply -f "$ROOT/03-02-postgres-statefulset.yaml"
kubectl apply -f "$ROOT/03-03-postgres-service.yaml"
kubectl apply -f "$ROOT/03-50-redis-pvc.yaml"
kubectl apply -f "$ROOT/04-redis.yaml"
kubectl rollout status statefulset/postgres -n spring-msa --timeout=300s
kubectl rollout status deployment/redis -n spring-msa --timeout=180s
```

Redis는 AOF, 2Gi PVC, readiness/liveness probe, CPU/메모리 request와 limit를 사용한다.

## 4. PostgreSQL 스키마와 관리자 생성

네 데이터 서비스는 각각 `user_service`, `community_service`, `stock_service`, `member_bff` 스키마를 Flyway로 생성한다.

```bash
kubectl apply -f "$ROOT/10-user-service.yaml"
kubectl apply -f "$ROOT/11-community-service.yaml"
kubectl apply -f "$ROOT/12-stock-service.yaml"
kubectl apply -f "$ROOT/20-member-bff-service.yaml"
kubectl rollout status deployment/spring-user-service -n spring-msa --timeout=300s
kubectl rollout status deployment/spring-member-community-service -n spring-msa --timeout=300s
```

최초 관리자만 수동 Job으로 한 번 생성한다. 예제의 User Service image digest를 배포 버전과 맞춘 뒤 적용한다.

```bash
kubectl apply -f "$ROOT/examples/08-admin-bootstrap-job.example.yaml"
kubectl logs -n spring-msa job/admin-bootstrap
kubectl delete job admin-bootstrap -n spring-msa
```

첫 실행 결과는 `created`, 동일한 자격증명 재실행은 `already_present`여야 한다. 공개 관리자 가입 API와 UI는 존재하지 않는다.

## 5. Kafka

```bash
KAFKA=/path/to/SpringMSA/infra/k8s/kafka
kubectl apply -f "$KAFKA/00-namespace.yaml"
kubectl apply -f "$KAFKA/05-kafka.yaml"
kubectl apply -f "$KAFKA/10-kafka-exporter.yaml"
kubectl rollout status statefulset/kafka -n kafka --timeout=300s
```

Kafka는 실행 중 외부 JAR을 다운로드하지 않는다. 메트릭은 digest 고정 Kafka exporter가 제공한다.

## 6. 관측성과 ServiceMonitor

Windows PowerShell에서 실행한다.

```powershell
Set-Location C:\Project\SpringMSA\infra\k8s\observability
.\scripts\install-observability.ps1
.\scripts\check-observability.ps1
kubectl apply -f C:\Project\SpringMSA\infra\k8s\kafka\20-servicemonitors.yaml
```

Prometheus Operator가 설치된 뒤에만 `20-servicemonitors.yaml`을 적용한다. 로그 수집기는 Promtail이 아니라 Grafana Alloy다.

## 7. 나머지 애플리케이션

```bash
kubectl apply -f "$ROOT/13-auth-server.yaml"
kubectl apply -f "$ROOT/21-admin-bff-service.yaml"
kubectl apply -f "$ROOT/30-member-gateway.yaml"
kubectl apply -f "$ROOT/31-admin-gateway.yaml"
kubectl apply -f "$ROOT/40-web.yaml"
kubectl apply -f "$ROOT/50-ingress.yaml"
kubectl get pods,svc,ingress,pvc -n spring-msa
```

실패 시 다음 순서로 확인한다.

```bash
kubectl describe pod POD_NAME -n spring-msa
kubectl logs POD_NAME -n spring-msa --all-containers --tail=200
kubectl get events -n spring-msa --sort-by=.lastTimestamp
```
