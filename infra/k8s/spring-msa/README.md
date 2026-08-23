# Spring MSA Kubernetes Manifests

현재 VMware 온프레미스 배포 선언이다.

- PostgreSQL: 192.168.147.101:5432
- Redis: 192.168.147.101:6379
- Kafka: 192.168.147.131:9092
- Application: worker-1/2
- Platform + Observability: worker-platform-observability

02-external-data-services.yaml이 외부 endpoint를 cluster Service로 제공한다. Required anti-affinity로 application replica를 worker-1/2에 분리한다.

Authorization Server는 현재 replicas 1이다. 공유 OAuth2 authorization 저장소와 JWK를 구현하기 전에는 확장하지 않는다.

~~~bash
kubectl apply -k infra/k8s/spring-msa
kubectl rollout status deployment -n spring-msa --timeout=10m
~~~

~~~powershell
powershell.exe -ExecutionPolicy Bypass -File infra\ci\k8s-live-smoke.ps1
~~~
