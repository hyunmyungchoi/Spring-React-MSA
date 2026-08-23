# iptables에서 nftables로 바뀌는 이유와 Cilium의 위치

> Netfilter, iptables, nftables, kube-proxy, CNI, Cilium, eBPF

## 시작하며

Kubernetes 노드를 구성하면서 다음 설정을 적용했다.

```bash
sudo modprobe overlay
sudo modprobe br_netfilter

cat <<'EOF' | sudo tee /etc/sysctl.d/99-kubernetes-cri.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sudo sysctl --system
```

여기서 의문이 생겼다.

> 요즘은 iptables를 버리고 nftables로 바꾸는 추세라는데, 왜 Kubernetes 설치 과정에는 여전히 iptables라는 이름이 등장할까? Cilium을 설치하면 둘 다 필요 없는 것 아닐까?

이 질문에 답하려면 Netfilter, iptables, nftables, kube-proxy, Cilium의 책임을 분리해야 한다.

## Netfilter와 iptables, nftables

Netfilter는 Linux 커널의 패킷 처리 프레임워크다. routing 전후, local input/output, forwarding 같은 지점에서 패킷을 검사하거나 변경할 수 있는 hook을 제공한다.

iptables와 nftables는 이 커널 기능을 설정하는 사용자 공간 인터페이스다.

```text
Packet
  |
Linux Netfilter hooks
  |
  +-- iptables interface
  +-- nftables interface
```

Ubuntu에서 `iptables` 명령을 실행한다고 반드시 legacy iptables backend를 사용하는 것은 아니다. `iptables-nft` 호환 계층을 사용하면 익숙한 iptables 명령을 nftables ruleset으로 변환한다.

현재 backend는 다음 명령으로 확인할 수 있다.

```bash
iptables --version
sudo update-alternatives --display iptables
sudo nft list ruleset
```

출력에 `nf_tables`가 보인다면 명령 이름은 iptables여도 커널 쪽에서는 nftables backend를 사용하고 있을 수 있다.

## 기존 iptables 방식의 한계

iptables가 오래됐다는 이유만으로 나쁜 도구는 아니다. 수많은 시스템에서 검증됐고 소규모 ruleset에서는 충분히 동작한다.

문제는 Kubernetes처럼 Service와 Endpoint가 지속적으로 늘고 바뀌는 환경이다. kube-proxy iptables 모드는 Service와 Endpoint에 비례해 많은 규칙을 생성한다.

첫 패킷이 Service 규칙을 순차적으로 비교해야 하는 구조에서는 Service 수가 커질수록 탐색 비용이 증가한다. Endpoint 변경 시 ruleset을 갱신하는 비용과 여러 컴포넌트가 규칙을 변경할 때의 lock 경쟁도 문제가 될 수 있다.

## nftables가 개선한 부분

nftables는 set, map, concatenation, verdict map 같은 데이터 구조를 제공한다. 유사한 규칙을 수천 줄 반복하는 대신 tuple을 map에서 조회해 동작을 결정할 수 있다.

```text
iptables 방식의 개념
if destination == service-A then jump A
if destination == service-B then jump B
if destination == service-C then jump C

nftables 방식의 개념
(destination IP, protocol, port) -> verdict map lookup
```

| 항목 | iptables 계열 | nftables |
|---|---|---|
| 규칙 표현 | 개별 규칙 중심 | set과 map 중심 |
| IPv4/IPv6 도구 | 전통적으로 분리 | 통합 family 제공 |
| ruleset 교체 | 대규모 변경 부담 | atomic transaction 지원 |
| 변경 방식 | 전체 크기의 영향이 큼 | 변경분 중심 갱신 가능 |
| 대규모 Service 조회 | 규칙 수의 영향이 큼 | map 기반 조회 가능 |

Kubernetes의 kube-proxy nftables 모드는 Kubernetes 1.33에서 stable이 됐다. Linux kernel 5.13 이상이 필요하며, iptables 모드와 100% 동일한 기본 동작을 보장하지 않으므로 전환 전에 Network Plugin과 모니터링 도구 호환성을 확인해야 한다.

## kube-proxy는 어디에 쓰이는가

kube-proxy는 Kubernetes Service의 가상 IP를 실제 Pod Endpoint로 연결한다.

```text
Client
  -> ClusterIP:Port
  -> kube-proxy data plane
  -> Pod IP:TargetPort
```

kube-proxy는 구현 모드에 따라 iptables, IPVS 또는 nftables를 사용할 수 있다.

```text
Service -> kube-proxy iptables -> Pod
Service -> kube-proxy IPVS     -> Pod
Service -> kube-proxy nftables -> Pod
```

따라서 "Kubernetes가 nftables를 지원한다"는 것은 CNI가 아니라 kube-proxy의 Service 구현 방식에 관한 이야기일 수 있다.

## Cilium은 무엇을 대체하는가

Cilium은 eBPF 기반 CNI다. Pod IP 할당, routing, NetworkPolicy, Service load balancing, 네트워크 관측을 담당할 수 있다.

Cilium을 kube-proxy replacement 모드로 구성하면 Kubernetes Service 처리를 eBPF가 담당한다.

```text
일반적인 kube-proxy 구성
Service -> iptables/IPVS/nftables -> Pod

Cilium kube-proxy replacement
Service -> Cilium eBPF -> Pod
```

이 경우 Service 처리 관점에서 kube-proxy의 iptables 또는 nftables 규칙이 필요하지 않을 수 있다. 하지만 호스트 전체에서 Netfilter가 사라지는 것은 아니다.

- 호스트 방화벽은 nftables를 사용할 수 있다.
- Docker나 다른 도구가 NAT 규칙을 만들 수 있다.
- 외부 관리 정책이 Netfilter를 사용할 수 있다.
- `br_netfilter` 관련 sysctl 이름에는 계속 iptables가 들어간다.

## sysctl 이름에 iptables가 남는 이유

다음 설정은 legacy iptables 사용자 공간 도구를 선택하는 설정이 아니다.

```text
net.bridge.bridge-nf-call-iptables=1
```

Linux bridge를 통과하는 IPv4 패킷을 Netfilter hook으로 보낼지 결정하는 커널 설정이다. backend를 iptables legacy로 고정한다는 뜻이 아니다.

`net.ipv4.ip_forward=1`은 노드가 서로 다른 interface 사이에서 IPv4 패킷을 forwarding할 수 있게 한다. Pod CIDR과 노드 간 routing이 필요한 Kubernetes에서 중요한 설정이다.

## 현재 인프라의 선택

현재 프로젝트에서는 책임을 다음처럼 나눈다.

| 영역 | 담당 |
|---|---|
| Pod 네트워크 | Cilium |
| NetworkPolicy | Cilium |
| Kubernetes Service | Cilium eBPF 구성에 맞춤 |
| 호스트 방화벽 | 필요 시 nftables로 별도 관리 |
| 컨테이너 실행 | containerd |

운영 원칙은 다음과 같다.

- Ubuntu의 nftables 기반 기본 구성을 유지한다.
- 호환 명령이 필요하면 `iptables-nft`를 사용한다.
- Cilium이 만든 eBPF map과 Kubernetes 관련 규칙을 수동 수정하지 않는다.
- `nft flush ruleset`처럼 전체 규칙을 지우는 명령을 운영 중 실행하지 않는다.
- kube-proxy 제거 여부는 추측하지 않고 실제 DaemonSet과 Cilium 설정으로 확인한다.

```bash
kubectl -n kube-system get daemonset kube-proxy
cilium status
cilium config view | grep -i kube-proxy
```

## 결론

- Netfilter는 커널 프레임워크다.
- iptables와 nftables는 Netfilter 규칙을 관리하는 인터페이스다.
- nftables는 대규모 규칙 조회와 증분 갱신에 유리하다.
- kube-proxy는 Kubernetes Service를 Endpoint로 연결한다.
- Cilium은 Pod 네트워크와 정책을 담당하며 설정에 따라 kube-proxy를 eBPF로 대체한다.
- Cilium을 쓴다고 호스트의 nftables까지 사라지는 것은 아니다.

결국 `iptables 또는 nftables 또는 Cilium` 중 하나를 고르는 문제가 아니다. 각 도구가 어느 경계를 책임지는지 정하고 겹치는 부분을 의도적으로 제거하는 문제다.

## 참고 자료

- [Kubernetes: NFTables mode for kube-proxy](https://kubernetes.io/blog/2025/02/28/nftables-kube-proxy/)
- [Kubernetes: Virtual IPs and Service Proxies](https://kubernetes.io/docs/reference/networking/virtual-ips/)
- [Cilium: kube-proxy replacement](https://docs.cilium.io/en/stable/network/kubernetes/kubeproxy-free/)
- [nftables Wiki](https://wiki.nftables.org/)

