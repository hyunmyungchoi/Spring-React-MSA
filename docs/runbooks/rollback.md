# 롤백 런북

## 원칙

Git이 Kubernetes desired state의 기준이다. 권장 롤백은 마지막 정상 Git SHA image tag로 manifest를 되돌리고 commit한 뒤 Argo CD Sync하는 것이다. `kubectl rollout undo`는 긴급 임시 조치이며 이후 Git과 반드시 일치시킨다.

## 1. 영향 범위와 정상 SHA 확인

```powershell
kubectl get deployments -n spring-msa `
  -o custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[*].image
kubectl rollout history deployment/<deployment> -n spring-msa
git log --oneline -- infra/k8s/spring-msa
```

다음을 기록한다.

- 실패 deployment와 현재 image SHA
- 마지막 정상 image SHA
- 오류 시작 시각과 증상
- DB schema 또는 Config/Secret 변경 동반 여부

## 2. GitOps 롤백

manifest image를 마지막 정상 SHA로 바꾼다. 가능하면 문제의 bot manifest commit을 `git revert`한다.

```powershell
git revert <manifest-update-commit>
git push
```

Argo CD에서 diff를 확인하고 Sync한다.

```powershell
argocd app diff spring-msa
argocd app sync spring-msa
argocd app wait spring-msa --health --sync --timeout 300
```

수동으로 image line을 수정한 경우에도 별도 commit으로 남기고 `latest`를 사용하지 않는다.

## 3. 긴급 Kubernetes 롤백

Git 변경을 기다릴 수 없고 이전 ReplicaSet이 남아 있을 때만 사용한다.

```powershell
kubectl rollout undo deployment/<deployment> -n spring-msa
kubectl rollout status deployment/<deployment> -n spring-msa --timeout=300s
```

복구 직후 live image를 확인하고 같은 SHA를 Git manifest에 반영한다. 그렇지 않으면 다음 Argo Sync가 실패 version을 다시 적용한다.

## 4. 검증

```powershell
kubectl get pods -n spring-msa
kubectl logs deployment/<deployment> -n spring-msa --tail=200
```

기능별 확인:

- Gateway/BFF: readiness, 로그인, `/auth/me`
- User: 현재 사용자와 관리자 목록
- Stock: workspace, cache/stale 상태, Toss 오류율
- Chat: WebSocket 연결, message 저장, replica fan-out
- Frontend: 해당 경로와 static asset

Grafana에서 rollback 전후 5xx, latency, restart, Kafka lag를 비교한다.

## 데이터베이스 변경

image rollback이 schema rollback을 의미하지 않는다. backward-compatible migration을 기본으로 한다.

- destructive migration은 expand/contract 단계로 분리한다.
- 이미 적용된 migration file을 수정하지 않는다.
- application이 이전 schema와 호환되지 않으면 image만 rollback하지 말고 별도 복구 계획을 따른다.
- volume/PVC 삭제는 rollback 수단으로 사용하지 않는다.

## Config/Secret 롤백

Secret 값 변경이 원인이면 이전 값을 안전한 secret source에서 복원하고 deployment를 재시작한다. 값을 채팅, issue, Git diff, 로그에 남기지 않는다.

```powershell
kubectl rollout restart deployment/<deployment> -n spring-msa
kubectl rollout status deployment/<deployment> -n spring-msa
```

## Kafka/채팅

- consumer 오류는 DLT와 lag를 먼저 확인한다.
- topic message를 삭제하거나 offset을 임의 reset하지 않는다.
- Outbox 구현 전 producer event 유실 가능성이 있으므로 DB message와 Kafka event ID를 대조한다.
- DLT re-drive는 consumer 수정 배포 후 소량으로 검증한다.

## 종료 조건

- workload Healthy/Synced
- 핵심 smoke test 성공
- 오류율과 latency 정상화
- Git/live image 일치
- 사고 시각, 원인, rollback SHA, 후속 작업 기록
