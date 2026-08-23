# containerd와 Cilium은 각각 무슨 일을 할까

> Kubernetes, CRI, CNI, containerd, Cilium, eBPF

## 시작하며

Kubernetes 클러스터를 직접 구성하면 모든 노드에 containerd를 설치하고 그 뒤 Cilium을 설치한다. 둘 다 컨테이너와 관련돼 보여 역할이 쉽게 섞인다.

한 문장으로 구분하면 다음과 같다.

- containerd는 컨테이너를 실행한다.
- Cilium은 Pod가 네트워크로 통신하고 보안 정책을 적용받게 한다.

둘은 경쟁 제품이 아니라 서로 다른 Kubernetes 확장 지점을 담당한다.

## Pod 하나가 만들어지는 과정

사용자가 Deployment를 생성하면 Scheduler가 Pod를 실행할 노드를 선택한다. 해당 노드의 kubelet은 컨테이너 실행과 네트워크 구성을 각각 다른 인터페이스로 요청한다.

```text
kubectl apply
    |
kube-apiserver
    |
scheduler가 node 선택
    |
node의 kubelet
    |
    +-- CRI -> containerd
    |          +-- image pull
    |          +-- container filesystem
    |          +-- namespace와 cgroup
    |          +-- process 실행
    |
    +-- CNI -> Cilium
               +-- Pod IP
               +-- veth 연결
               +-- route
               +-- NetworkPolicy
               +-- eBPF program
```

## containerd의 역할

containerd는 고수준 컨테이너 runtime이다. Kubernetes kubelet은 CRI를 통해 containerd에 요청한다.

주요 역할은 다음과 같다.

- Registry에서 image pull
- image layer와 snapshot 관리
- container 생성, 시작, 정지, 삭제
- container namespace와 cgroup 구성
- 표준 입출력과 process 상태 관리
- low-level runtime인 runc 호출

```text
kubelet
  -> CRI plugin
  -> containerd
  -> runc
  -> Linux container process
```

containerd가 정상이어도 CNI가 준비되지 않으면 Pod는 `ContainerCreating`에 머물 수 있다. 반대로 Cilium이 정상이어도 containerd가 죽으면 컨테이너 process를 만들 수 없다.

## Cilium의 역할

Cilium은 eBPF 기반의 CNI와 네트워크 보안 플랫폼이다.

주요 역할은 다음과 같다.

- Pod IP 할당
- Pod network namespace를 host network와 연결
- Node 간 Pod routing
- Kubernetes NetworkPolicy 적용
- L3/L4 및 선택적인 L7 정책
- Service load balancing
- Hubble을 통한 network flow 관측

Cilium Agent는 각 노드에서 DaemonSet으로 실행된다. 노드마다 Pod networking을 설정해야 하므로 Worker뿐 아니라 Kubernetes에 참여하는 모든 노드에 배치되는 것이 일반적이다.

## CRI와 CNI의 차이

| 구분 | CRI | CNI |
|---|---|---|
| 전체 이름 | Container Runtime Interface | Container Network Interface |
| 호출 주체 | kubelet | container runtime 및 kubelet 흐름 |
| 목적 | 컨테이너 생명주기 | 컨테이너 네트워크 구성 |
| 현재 구현 | containerd | Cilium |

CRI는 "이 image로 컨테이너를 실행해 달라"는 계약이고 CNI는 "이 network namespace에 interface와 IP를 구성해 달라"는 계약이다.

## 기능 비교

| 기능 | containerd | Cilium |
|---|---|---|
| image pull | O | X |
| process 실행 | O | X |
| filesystem snapshot | O | X |
| cgroup 관리 | O | X |
| Pod IP 할당 | X | O |
| Node 간 routing | X | O |
| NetworkPolicy | X | O |
| Service load balancing | X | 설정에 따라 O |
| Network flow 관측 | 제한적 | Hubble로 가능 |

## containerd 2.x 설정에서 겪은 문제

Ubuntu에 containerd를 설치했는데 `/etc/containerd/config.toml`이 없는 노드가 있었다. service는 active였지만 명시적인 config 파일이 생성되지 않은 상태였다.

containerd는 기본 내장 설정으로 시작할 수 있으므로 파일이 없다고 service가 반드시 실패하는 것은 아니다. 그러나 Kubernetes에서 CRI와 systemd cgroup 설정을 명시적으로 통일하려면 기본 config를 생성하는 편이 관리하기 쉽다.

```bash
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml
```

containerd 1.x와 2.x는 config schema와 plugin 경로가 다를 수 있으므로 인터넷에서 찾은 `sed` 명령을 버전 확인 없이 그대로 적용하면 안 된다.

```bash
containerd --version
sudo ctr plugins ls
sudo systemctl status containerd
```

Kubernetes에서는 CRI plugin이 정상인지, cgroup driver가 kubelet과 일치하는지 확인해야 한다.

## Cilium 상태 확인

Pod가 실행되지 않을 때 containerd와 Cilium을 따로 점검한다.

```bash
sudo systemctl is-active containerd
sudo crictl ps

cilium status
kubectl -n kube-system get pods -l k8s-app=cilium -o wide
cilium connectivity test
```

점검 순서는 다음이 효율적이다.

1. 노드가 `Ready`인지 확인한다.
2. containerd와 kubelet이 active인지 확인한다.
3. Cilium Agent가 모든 노드에서 Ready인지 확인한다.
4. CoreDNS와 Service 통신을 확인한다.
5. 전체 connectivity test를 실행한다.

## containerd와 Docker의 관계

Docker Engine도 내부적으로 containerd를 사용한다. 하지만 Kubernetes는 Docker CLI나 Docker daemon 없이 containerd의 CRI endpoint에 직접 연결할 수 있다.

```text
Docker 환경
docker CLI -> dockerd -> containerd -> runc

현재 Kubernetes 환경
kubelet -> containerd CRI -> runc
```

따라서 Kubernetes 노드에 Docker Engine을 반드시 설치할 필요는 없다. 이 프로젝트는 로컬 Docker Desktop 대신 VM 내부의 containerd를 Kubernetes runtime으로 사용한다.

## 결론

- kubelet은 CRI를 통해 containerd에 컨테이너 실행을 요청한다.
- containerd는 image, filesystem, cgroup, process 생명주기를 관리한다.
- kubelet의 Pod 생성 흐름은 CNI를 통해 Cilium에 네트워크 구성을 요청한다.
- Cilium은 Pod IP, routing, policy, eBPF data plane을 담당한다.
- 두 구성 요소 중 하나라도 없으면 정상적인 Kubernetes Pod 실행이 완성되지 않는다.

containerd는 컨테이너의 몸을 만들고 Cilium은 그 컨테이너가 Kubernetes 네트워크 안에서 움직일 길과 규칙을 만든다고 이해하면 가장 명확하다.

## 참고 자료

- [containerd Documentation](https://containerd.io/docs/)
- [Kubernetes Container Runtimes](https://kubernetes.io/docs/setup/production-environment/container-runtimes/)
- [CNI Specification](https://www.cni.dev/docs/spec/)
- [Cilium Documentation](https://docs.cilium.io/)

