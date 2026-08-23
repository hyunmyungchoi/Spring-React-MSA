# 온프레미스 아키텍처

## 물리 구성

~~~text
Windows Host
|
+-- Kubernetes
|   +-- control-plane      192.168.147.110
|   +-- worker-1           192.168.147.111  application
|   +-- worker-2           192.168.147.112  application
|   +-- worker-platform-observability 192.168.147.113  platform + observability
|
+-- Storage VM             192.168.147.101
|   +-- PostgreSQL
|   +-- Redis
|   +-- Alloy
|
+-- Kafka VM               192.168.147.131
    +-- Kafka KRaft broker/controller
    +-- Alloy
~~~

NAT NIC는 외부 다운로드, Host-only NIC는 고정 IP 통신에 사용한다.

## Node Pool과 Namespace

| Pool | Node | 주요 workload |
| --- | --- | --- |
| application | worker-1/2 | Spring, BFF, Gateway, Web, Ingress |
| platform-observability | worker-platform-observability | Argo CD, Metrics Server, local-path, Kafka Exporter, Prometheus, Grafana, Loki, MinIO |

| Namespace | 역할 |
| --- | --- |
| spring-msa | 애플리케이션 |
| argocd | GitOps |
| observability | metrics와 logs |
| kafka | 외부 Kafka endpoint와 exporter |
| ingress-nginx | HTTP ingress |
| kube-system | Kubernetes와 Cilium |

K9s 단축키는 Shift-1 application, Shift-2 Argo CD, Shift-3 observability, Shift-4 Kafka, Shift-5 ingress, Shift-6 system이다.

## 데이터 흐름

~~~text
Browser -> Ingress -> Gateway -> BFF -> Domain Service
                                    |
                                    +-> PostgreSQL
                                    +-> Redis
                                    +-> Kafka
~~~

Community, Stock, User는 DB transaction에서 Outbox를 저장한다. Relay가 Kafka에 발행하고 Member BFF는 processed_kafka_events로 중복 소비를 막는다. 실패 이벤트는 DLT로 이동한다.

## HA 경계

Application과 Ingress는 worker-1/2에 분산된다. 다음은 현재 단일 장애 지점이다.

- control-plane
- Storage VM
- Kafka broker
- worker-platform-observability
- Authorization Server 1 replica

Authorization Server는 공유 OAuth2 authorization 저장소와 공유 JWK가 없다. 2 replicas에서는 authorization code/token 교환이 실패하므로 현재 의도된 값은 1 replica다. Active-Active 전환 전 OAuth2AuthorizationService 영속화와 고정 JWK가 필요하다.
