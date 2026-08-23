# Kubernetes 배포 런북

현재 환경은 VMware, Ubuntu Server 24.04, kubeadm, containerd, Cilium이다. Rocky Linux, k3s, VirtualBox 절차는 사용하지 않는다.

## 1. 노드

~~~bash
kubectl get nodes -o wide
kubectl get nodes -L node-pool,workload
~~~

control-plane, worker-1, worker-2, worker-platform-observability가 모두 Ready여야 한다.

## 2. 외부 서비스

~~~powershell
Test-NetConnection 192.168.147.101 -Port 5432
Test-NetConnection 192.168.147.101 -Port 6379
Test-NetConnection 192.168.147.131 -Port 9092
Test-NetConnection 192.168.147.110 -Port 6443
~~~

~~~bash
kubectl apply -f infra/k8s/spring-msa/02-external-data-services.yaml
kubectl get svc,endpoints -n spring-msa
kubectl get svc,endpoints -n kafka
~~~

## 3. Secret

~~~powershell
Copy-Item infra\k8s\spring-msa\examples\02-secrets.example.yaml infra\k8s\spring-msa\02-secrets.local.yaml
kubectl apply -f infra\k8s\spring-msa\02-secrets.local.yaml
~~~

02-secrets.local.yaml은 Git에 올리지 않는다.

## 4. 애플리케이션

~~~bash
kubectl apply -k infra/k8s/spring-msa
kubectl rollout status deployment -n spring-msa --timeout=10m
kubectl get pods -n spring-msa -o wide
~~~

Authorization Server는 1 replica로 유지한다. 공유 authorization 저장소와 JWK를 구현하기 전에는 확장하지 않는다.

## 5. 전용 Node Pool

~~~bash
chmod +x infra/k8s/platform/apply-platform-placement.sh
infra/k8s/platform/apply-platform-placement.sh
kubectl get pods -A -o wide --field-selector spec.nodeName=worker-platform-observability
~~~

## 6. Ingress

Windows hosts:

~~~text
192.168.147.111 user.localtest.me admin.localtest.me argocd.localtest.me grafana.localtest.me
~~~

~~~powershell
curl.exe -I http://user.localtest.me
curl.exe -I http://admin.localtest.me
curl.exe -I http://argocd.localtest.me
curl.exe -I http://grafana.localtest.me
~~~

## 7. Observability

~~~bash
cd infra/k8s/observability
chmod +x scripts/install-observability.sh scripts/expose-control-plane-metrics.sh
./scripts/install-observability.sh
./scripts/expose-control-plane-metrics.sh
~~~

control-plane 원본은 /etc/kubernetes/backup-springmsa에 보관한다. 백업을 /etc/kubernetes/manifests에 두면 kubelet이 static Pod로 읽으므로 금지한다.

## 8. 최종 점검

~~~bash
cilium status --wait
kubectl top nodes
kubectl get application spring-msa -n argocd
kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded
~~~

기능 E2E는 [운영 검증](on-premise-validation.md)을 따른다.
