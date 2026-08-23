# 온프레미스 운영 검증

## 상태

~~~bash
kubectl get nodes -L node-pool,workload
kubectl get deployment,statefulset -A
kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded
kubectl top nodes
cilium status --wait
~~~

## 연결과 HTTP

~~~powershell
Test-NetConnection 192.168.147.101 -Port 5432
Test-NetConnection 192.168.147.101 -Port 6379
Test-NetConnection 192.168.147.131 -Port 9092
curl.exe -I http://user.localtest.me
curl.exe -I http://admin.localtest.me
curl.exe -I http://argocd.localtest.me
curl.exe -I http://grafana.localtest.me
powershell.exe -ExecutionPolicy Bypass -File infra\ci\k8s-live-smoke.ps1
~~~

Grafana 302는 로그인 redirect이므로 정상이다.

## Outbox와 DLT

~~~sql
select event_type, published_at, attempts, last_error
from community_service.outbox_events
order by occurred_at desc
limit 10;
~~~

정상은 published_at 존재, attempts 1, last_error null, processed_kafka_events 반영이다.

~~~bash
kubectl logs -n spring-msa -l app=spring-member-bff-service --all-containers --since=5m |
  grep 'Domain event moved to DLT'
~~~

## Observability

~~~bash
kubectl get --raw '/api/v1/namespaces/observability/services/http:kube-prometheus-stack-prometheus:9090/proxy/-/ready'
kubectl get --raw '/api/v1/namespaces/observability/services/http:loki-gateway:80/proxy/loki/api/v1/labels'
~~~

control-plane target 3개가 down이면 expose-control-plane-metrics.sh을 실행한다.

## Cilium

~~~bash
cilium connectivity test --test-namespace cilium-test-final --timeout 15m
~~~

재부팅 후 과거 restart count 때문에 check-log-errors만 실패할 수 있다. 이 경우 cilium status, 현재 Pod Ready, 최근 error/fatal/soft-lockup 로그를 함께 확인한다.

## 2026-08-10 결과

- Nodes 6/6 Ready, 비정상 Pod 0
- Member/Admin 인증과 logout 통과
- Community/Stock CRUD 통과
- Toss 통과
- Outbox publish와 processed event 통과
- Kafka DLT 이동과 소비 통과
- PostgreSQL/Redis/Kafka 연결 통과
- Loki 통과
- Prometheus 36/36 up
- Cilium 6/6 OK, 최근 오류 0
- Cilium suite는 기능 시나리오 통과, 과거 restart count 검사로 exit 1
- Authorization Server는 1 replica에서 5/5 성공
- Argo CD는 Healthy / OutOfSync이며, 전체 Diff 검토와 수동 Sync가 남아 있음
