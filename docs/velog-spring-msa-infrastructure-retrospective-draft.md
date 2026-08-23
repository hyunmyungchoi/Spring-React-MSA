# Windows 11에서 Spring MSA 온프레미스 랩을 구축하며 배운 것들

> 상태: Velog 연재를 위한 초안  
> 환경: Windows 11, VMware Workstation, kubeadm Kubernetes, Cilium, containerd, Kafka, PostgreSQL, Redis, Spring Boot  
> 프로젝트: Spring-React-MSA  
> 주의: 이 글은 제품 전체의 절대적인 성능 비교가 아니라 개인 개발 환경에서 발생한 장애와 해결 과정을 정리한 글이다.

## 들어가며

처음 목표는 단순했다. Windows PC 한 대에 여러 Ubuntu VM을 띄우고 Kubernetes 클러스터를 만든 뒤, Spring MSA 애플리케이션을 배포하는 것이었다.

하지만 실제 구축 과정에서는 가상화 계층부터 네트워크, 메시징, 데이터베이스 소유권, 애플리케이션 고가용성까지 서로 다른 문제가 연쇄적으로 드러났다.

이번 연재에서 다룰 주제는 다음과 같다.

| 편 | 주제 | 핵심 질문 |
|---:|---|---|
| 1 | Hyper-V, VT-x, VirtualBox, VMware | 같은 Windows 하이퍼바이저를 경유하는데 왜 결과가 달랐을까? |
| 2 | iptables, nftables, Cilium | 이 셋은 경쟁 기술인가, 서로 다른 계층인가? |
| 3 | Kafka Transactional Outbox | DB 변경과 이벤트 발행 사이의 불일치를 어떻게 줄일까? |
| 4 | Git Submodule과 GHCR | 여러 저장소의 소스와 배포 이미지를 어떻게 조합할까? |
| 5 | containerd와 Cilium | 컨테이너 런타임과 CNI는 각각 무엇을 담당할까? |
| 6 | MSA 데이터베이스 분리 | VM, 인스턴스, 데이터베이스, 스키마 중 어디부터 나눌까? |
| 7 | Active-Active와 Active-Standby | Worker 2대와 3대는 장애 대응 방식이 어떻게 다를까? |
| 8 | Authorization Server 다중화 | Auth Server를 2개로 늘렸는데 왜 OAuth가 불안정해졌을까? |

현재 실습 환경은 다음과 같다.

```text
Windows Host
|
+-- Kubernetes Cluster
|   +-- control-plane       192.168.147.110
|   +-- worker-1            192.168.147.111
|   +-- worker-2            192.168.147.112
|   +-- worker-platform-observability 192.168.147.113
|
+-- Storage VM              192.168.147.101
|   +-- PostgreSQL
|   +-- Redis
|
+-- Kafka VM                192.168.147.131
    +-- Kafka Broker
    +-- KRaft Controller
```

---

# 1. Hyper-V와 VT-x, 그리고 VirtualBox에서 VMware로 옮긴 이유

## 문제 상황

VirtualBox에 Ubuntu VM을 구성하고 Kubernetes와 Cilium을 설치했다. 기본적인 Pod 생성과 통신은 가능했지만 `cilium connectivity test`를 실행하면 특정 구간에서 테스트가 매우 느려지거나 VM 전체가 사실상 멈추는 현상이 반복됐다.

화면과 커널 로그에서는 다음 메시지가 확인됐다.

```text
watchdog: BUG: soft lockup - CPU#0 stuck for ...
rcu_preempt self-detected stall on CPU
rcu_preempt kthread starved for ... jiffies
systemd-logind.service: Watchdog timeout
containerd-shim ... stuck
e1000 ... NETDEV WATCHDOG
```

처음에는 이를 "CPU 누수"라고 표현했지만 정확한 표현은 아니다. 로그가 직접 보여 주는 것은 메모리나 CPU 자원이 새어 나갔다는 사실이 아니라, 게스트의 vCPU가 오랫동안 스케줄링되지 못해 커널 watchdog과 RCU가 stall을 감지했다는 사실이다.

스토리지 지연도 함께 체감됐지만 SQLite가 직접 원인이었다고 단정할 근거도 부족했다.

- 과거 단일 노드 k3s 환경은 기본 datastore로 SQLite를 사용할 수 있다.
- 현재 kubeadm 기반 Kubernetes의 Control Plane 저장소는 etcd다.
- `cilium connectivity test` 자체가 SQLite를 사용하는 것은 아니다.
- 따라서 과거 k3s의 SQLite I/O 지연과 현재 VM의 soft lockup은 구분해서 분석해야 한다.

## VT-x와 Hyper-V는 같은 기술이 아니다

VT-x는 Intel CPU가 제공하는 하드웨어 가상화 기능이다. Hyper-V는 VT-x와 EPT 같은 기능을 이용해 VM과 VBS 격리 영역을 실행하는 Microsoft의 하이퍼바이저다.

```text
Intel CPU
  +-- VT-x: CPU 가상화 명령 지원
  +-- EPT: 2단계 메모리 주소 변환 지원
          |
          v
Microsoft Hyper-V Hypervisor
          |
          v
Windows Hypervisor Platform API
          |
          +-- VirtualBox NEM 경로
          +-- VMware Host VBS Mode 경로
```

Windows에서 Hyper-V 또는 VBS가 하드웨어 가상화를 점유하고 있으면 다른 가상화 제품이 VT-x를 예전처럼 직접 제어하지 못할 수 있다. 이때 VirtualBox와 VMware는 Windows가 제공하는 하이퍼바이저 인터페이스 위에서 동작하는 호환 경로를 사용할 수 있다.

Oracle 공식 문서도 Hyper-V와 VirtualBox를 같은 호스트에서 사용할 때 성능 저하가 발생할 수 있다고 별도 문제로 설명한다. 다만 이것만으로 특정 환경의 모든 정지 현상을 NEM 버그라고 확정할 수는 없다.

## NEM과 VMware Host VBS Mode

VirtualBox의 NEM과 VMware의 Host VBS Mode는 모두 Hyper-V가 활성화된 Windows에서 VM을 실행하기 위한 호환 계층이다. 최하단에서 Microsoft 하이퍼바이저를 경유한다는 점은 같지만 상위 구현까지 같은 것은 아니다.

| 항목 | VirtualBox NEM | VMware Host VBS Mode |
|---|---|---|
| 하단 가상화 계층 | Hyper-V/WHP | Hyper-V/WHP 계열 |
| vCPU 실행 관리 | VirtualBox 구현 | VMware 구현 |
| 가상 장치 모델 | VirtualBox 구현 | VMware 구현 |
| 인터럽트와 타이머 처리 | VirtualBox 구현 | VMware 구현 |
| 디스크 및 네트워크 I/O | VirtualBox 구현 | VMware 구현 |
| 내부 구현 공개 범위 | VirtualBox 쪽이 상대적으로 넓음 | 핵심 구현은 비공개 |

즉, 두 제품이 같은 Hyper-V를 경유하더라도 vCPU 재진입, VM exit 처리, 타이머, 가상 NIC, 디스크 큐 처리 방식에서 차이가 날 수 있다.

## VMware로 교체한 결과

VMware Workstation으로 VM을 다시 구성하고 같은 목적의 Cilium connectivity test를 실행했다. VirtualBox에서 반복적으로 멈추던 구간을 통과했고 전체 132개 테스트가 실행됐다. 이후 확인된 실패 표시는 현재 네트워크 장애가 아니라 과거 VM 재부팅으로 누적된 Cilium 컨테이너 재시작 횟수를 검사한 결과였다.

체감 성능은 크게 개선됐지만 이 결과를 곧바로 "NEM에 결함이 있다"는 결론으로 확대하면 안 된다. 동시에 바뀐 조건이 있기 때문이다.

| 조건 | VirtualBox 당시 | VMware 교체 후 |
|---|---:|---:|
| vCPU | 2 | 4 |
| 메모리 | 약 4GB | 약 4GB |
| VM 저장 위치 | C 드라이브 계열 | `D:\VMware VMs` |
| 가상화 호환 경로 | NEM | Host VBS Mode |
| 결과 | soft lockup과 정지 반복 | 테스트 완료 |

확정할 수 있는 결론은 다음 정도다.

> 이 Windows 호스트와 해당 Kubernetes 부하의 조합에서는 VirtualBox NEM 경로에서 심각한 vCPU stall이 재현됐고, VMware Host VBS Mode로 교체한 뒤 같은 문제가 재현되지 않았다.

원인을 더 엄밀하게 분리하려면 두 제품에서 vCPU, RAM, 디스크 위치, 커널, Cilium 버전을 동일하게 맞추고 `vmstat`, `iostat`, `pidstat`, 커널 로그와 테스트 시간을 함께 수집해야 한다.

## 일시정지 후 재개하면 부팅되던 이유

VirtualBox VM이 `Begin: Loading essential drivers...`에서 멈춘 것처럼 보일 때 일시정지와 재개를 반복하면 부팅이 진행되는 경우도 있었다.

이 현상은 손상된 드라이버가 고쳐진 것이라기보다 VM의 vCPU 실행과 타이머 전달이 pause/resume 과정에서 다시 동기화되면서 일시적으로 진행된 것으로 보는 편이 타당하다. 이 역시 관찰에 근거한 추론이며, 정확한 내부 원인은 제품 수준의 trace 없이는 확정하기 어렵다.

## 이 편의 결론

- VT-x는 CPU 기능이고 Hyper-V는 그 기능을 사용하는 하이퍼바이저다.
- VBS가 활성화된 Windows에서는 VirtualBox와 VMware 모두 Hyper-V 호환 경로를 사용할 수 있다.
- NEM과 Host VBS Mode는 같은 코드가 아니므로 결과도 같다고 보장되지 않는다.
- 이번 장애는 CPU 누수보다는 vCPU starvation 또는 scheduling stall로 표현하는 것이 정확하다.
- SQLite 지연은 별도 실험 없이 직접 원인으로 단정하지 않는다.
- 내 환경에서는 VMware로 교체한 뒤 Cilium 테스트가 정상 완료됐다.

---

# 2. iptables와 nftables, Cilium eBPF의 관계

## 세 기술은 같은 계층의 경쟁자가 아니다

Linux 커널에는 패킷을 검사하고 변경하는 Netfilter 프레임워크가 있다. iptables와 nftables는 Netfilter 규칙을 관리하는 서로 다른 사용자 공간 인터페이스다. Cilium은 eBPF 기반의 Kubernetes 네트워크 데이터패스를 제공한다.

```text
Linux packet path
|
+-- Netfilter
|   +-- iptables legacy interface
|   +-- nftables interface
|
+-- eBPF hooks
    +-- Cilium datapath
```

Ubuntu에서 `iptables` 명령을 사용한다고 해서 반드시 legacy iptables backend를 쓰는 것도 아니다. `iptables-nft` 호환 계층을 통해 nftables ruleset을 조작할 수 있다.

```bash
iptables --version
sudo update-alternatives --display iptables
sudo nft list ruleset
```

## Kubernetes에서 iptables가 부담이 된 이유

kube-proxy의 iptables 모드는 Service와 Endpoint가 증가할수록 많은 규칙을 만들어야 한다. 큰 클러스터에서는 신규 연결이 규칙을 탐색하는 비용과 전체 규칙을 갱신하는 비용이 문제가 될 수 있다.

nftables는 set과 map을 활용할 수 있고 변경된 요소 중심의 갱신이 가능하다. Kubernetes의 nftables kube-proxy 모드는 Kubernetes 1.33에서 stable이 됐다. 다만 기존 iptables 모드가 즉시 폐기된 것은 아니며 호환성을 위해 계속 지원된다.

| 항목 | iptables kube-proxy | nftables kube-proxy |
|---|---|---|
| Service 탐색 구조 | 규칙 수에 영향을 크게 받음 | map 기반 조회 가능 |
| 대규모 규칙 갱신 | 전체 ruleset 크기의 영향이 큼 | 변경분 중심 갱신 가능 |
| 성숙도와 호환성 | 매우 높음 | 비교적 새로운 backend |
| 오래된 커널 지원 | 상대적으로 넓음 | Linux kernel 5.13 이상 필요 |

## Cilium을 사용하면 nftables가 필요 없는가

Cilium을 kube-proxy replacement 모드로 구성하면 Kubernetes Service load balancing의 상당 부분을 eBPF가 처리한다.

```text
kube-proxy 사용
Service -> iptables/IPVS/nftables -> Pod

Cilium kube-proxy replacement
Service -> Cilium eBPF -> Pod
```

하지만 Cilium을 사용한다고 호스트의 Netfilter가 사라지는 것은 아니다. 호스트 방화벽, 다른 컨테이너 도구의 NAT, 관리용 규칙 등은 여전히 nftables 또는 iptables-nft를 사용할 수 있다.

따라서 "CNI가 iptables를 폐기하고 nftables로 이동한다"는 문장은 부정확하다. 정확하게는 다음 세 결정을 분리해야 한다.

| 결정 | 선택지 예시 |
|---|---|
| Kubernetes Service 구현 | kube-proxy iptables, kube-proxy nftables, Cilium eBPF |
| Pod 네트워크와 NetworkPolicy | Cilium, Calico 등 CNI |
| 호스트 방화벽 | nftables, firewalld, ufw 등 |

## 이번 인프라의 선택

이 프로젝트에서는 다음 원칙을 사용한다.

- Ubuntu의 기본 nftables 계열 backend를 유지한다.
- 호환성이 필요한 명령은 `iptables-nft`를 사용한다.
- Pod 네트워크와 NetworkPolicy는 Cilium이 관리한다.
- Cilium이 만든 eBPF map이나 Kubernetes 네트워크 규칙을 수동으로 수정하지 않는다.
- 호스트 방화벽이 필요하면 Kubernetes 데이터패스와 분리해 nftables로 선언적으로 관리한다.
- kube-proxy를 실제로 제거했는지는 Cilium 설정과 kube-proxy DaemonSet 존재 여부로 확인한다.

Kubernetes 설치 과정에서 적용한 다음 sysctl 이름에 `iptables`가 포함돼 있어도 legacy backend를 강제한다는 의미는 아니다. bridge 패킷을 Netfilter hook으로 전달할지 정하는 커널 설정 이름이다.

```bash
net.bridge.bridge-nf-call-iptables=1
net.bridge.bridge-nf-call-ip6tables=1
net.ipv4.ip_forward=1
```

## 이 편의 결론

- iptables와 nftables는 Netfilter 규칙 관리 방식이다.
- nftables는 대규모 규칙 관리와 증분 갱신에 유리하다.
- Cilium eBPF는 nftables와 동일한 계층의 단순 대체제가 아니다.
- 현재 클러스터에서는 Cilium 데이터패스와 호스트 nftables 정책의 책임을 분리한다.

---

# 3. Kafka Transactional Outbox 적용기

## DB 저장 후 Kafka 발행만 하면 생기는 문제

Stock Service에서 관심 종목을 추가한 뒤 다른 서비스에 이벤트를 전달한다고 가정해 보자.

```text
1. Stock DB에 관심 종목 INSERT
2. Kafka에 watchlist-item-added 이벤트 발행
```

두 작업은 서로 다른 시스템에서 수행된다. 일반적인 로컬 DB 트랜잭션 하나로 묶을 수 없다.

| 실패 시점 | 결과 |
|---|---|
| DB commit 후 Kafka 발행 실패 | 데이터는 있지만 이벤트가 없음 |
| Kafka 발행 후 DB rollback | 이벤트는 있지만 실제 데이터가 없음 |
| 응답 유실 후 재시도 | 동일 작업과 이벤트가 중복될 수 있음 |

여기서 말하는 런타임 변경은 DDL이 아니라 DML이다.

- DDL: `CREATE TABLE`, `ALTER TABLE`, `DROP INDEX`
- DML: `INSERT`, `UPDATE`, `DELETE`, `SELECT`

DDL은 Flyway가 버전 관리하고, Transactional Outbox는 런타임 DML과 이벤트 발행 의도를 안전하게 연결한다.

## Outbox 구조

비즈니스 데이터와 이벤트 발행 의도를 같은 데이터베이스 트랜잭션에 저장한다.

```text
Stock Service local transaction
|
+-- stock_service.watchlist INSERT
+-- stock_service.outbox_events INSERT
|
+-- COMMIT

Outbox Relay
|
+-- unpublished event 조회
+-- Kafka topic 발행
+-- published_at 갱신

Member BFF Consumer
|
+-- event_id 중복 확인
+-- 알림 데이터 반영
+-- processed_kafka_events 기록
+-- COMMIT
```

이 프로젝트에서는 User, Community, Stock 이벤트를 Outbox 방식으로 발행한다. 실제 검증에서는 Community와 Stock의 최신 Outbox row가 `attempts=1`, `last_error=null`로 발행됐고 Member BFF의 `processed_kafka_events`에도 소비 결과가 반영됐다.

## Outbox가 보장하는 것과 보장하지 않는 것

Outbox가 보장하는 핵심은 다음과 같다.

> 한 서비스의 비즈니스 데이터 변경과 이벤트 발행 의도를 같은 로컬 트랜잭션으로 commit한다.

반면 여러 서비스의 DB 변경을 하나의 전역 ACID 트랜잭션으로 묶지는 않는다. Relay가 Kafka 발행에 성공하고 `published_at`을 갱신하기 전에 죽으면 같은 이벤트가 다시 발행될 수 있다. 따라서 기본 전달 보장은 일반적으로 at-least-once이며 소비자는 멱등해야 한다.

```sql
INSERT INTO processed_kafka_events(event_id, event_type, processed_at)
VALUES (:eventId, :eventType, now())
ON CONFLICT (event_id) DO NOTHING;
```

중복 claim과 실제 도메인 변경은 같은 소비자 로컬 트랜잭션에서 처리해야 한다.

## Retry와 DLT

일시적인 네트워크 오류나 DB lock은 재시도로 회복할 수 있다. 하지만 역직렬화 오류나 유효하지 않은 데이터처럼 반복해도 해결되지 않는 오류는 무한 재시도하면 안 된다.

```text
Kafka topic
  -> consumer 실패
  -> 정해진 횟수와 backoff로 재시도
  -> 최종 실패
  -> <original-topic>.DLT
```

DLT는 메시지를 버리는 장소가 아니다. 운영자가 원인, 원본 payload, exception, 재처리 여부를 확인하는 격리 공간이다. 이번 실동작 테스트에서는 의도적으로 잘못된 Community 이벤트를 넣고 `.DLT` 소비 로그까지 확인했다.

## Axon을 바로 도입해야 할까

Axon은 CQRS, Event Sourcing, Aggregate, Saga를 지원하는 프레임워크 생태계다. Transactional Outbox의 단순 대체품은 아니다.

| Outbox + Kafka | Axon 도입 시 |
|---|---|
| 기존 Spring/Kafka 구조 유지 | Command/Event 모델 학습 필요 |
| 필요한 이벤트만 명시적으로 발행 | Aggregate와 Saga 모델 추가 |
| 운영 요소가 비교적 단순 | Axon Server 또는 별도 저장 전략 필요 |
| 현재 프로젝트 요구에 충분 | 복잡한 장기 비즈니스 흐름에 장점 |

현재 단계에서는 Outbox, 멱등 소비, Retry, DLT를 완성하는 것이 우선이다. 주문-결제-재고처럼 장시간에 걸친 보상 트랜잭션이 필요해지면 별도의 실험 브랜치에서 Axon Saga와 현재 구조를 비교하는 편이 안전하다.

---

# 4. Git Submodule과 GHCR 적용기

## Submodule과 GHCR은 서로 다른 문제를 해결한다

Git Submodule은 여러 Git 저장소의 특정 commit을 하나의 상위 저장소에서 조합한다. GHCR은 빌드가 끝난 OCI 컨테이너 이미지를 저장한다.

```text
Service repositories
  -> parent repository가 submodule commit 조합을 고정
  -> GitHub Actions build
  -> GHCR image push
  -> Kubernetes deployment
```

상위 저장소는 하위 저장소의 전체 파일을 자기 history에 복사하지 않는다. `.gitmodules`에는 경로와 URL이, gitlink에는 하위 저장소의 특정 commit SHA가 기록된다.

## 하위 저장소가 바뀌면 왜 부모도 commit해야 하는가

`user-service`에 새 commit을 push해도 상위 저장소가 가리키는 SHA는 자동으로 바뀌지 않는다. 상위 저장소에서 submodule pointer를 갱신해야 전체 시스템의 정확한 버전 조합을 재현할 수 있다.

```bash
git submodule update --init --recursive

cd BackEnd/services/user-service
git pull
cd ../../..

git add BackEnd/services/user-service
git commit -m "chore: update user-service submodule"
```

이 추가 commit은 귀찮은 중복 작업이 아니라 상위 시스템의 Bill of Materials 역할을 한다.

## GHCR 배포 흐름

각 서비스 저장소는 이미지를 빌드해 GHCR에 올리고, Kubernetes는 그 이미지를 pull한다.

```text
ghcr.io/hyunmyungchoi/spring-msa-user-service:<tag>
ghcr.io/hyunmyungchoi/spring-msa-community-service:<tag>
ghcr.io/hyunmyungchoi/spring-msa-stock-service:<tag>
```

재현 가능한 배포가 중요하면 변경 가능한 tag만 사용하지 않고 digest를 고정한다.

```yaml
containers:
  - name: community-service
    image: ghcr.io/hyunmyungchoi/spring-msa-community-service@sha256:<digest>
```

Private image라면 Kubernetes namespace에 GHCR read 권한을 가진 `imagePullSecret`이 필요하다. 소스 의존성을 배포하는 GitHub Maven Packages와 실행 이미지를 배포하는 GHCR도 구분해야 한다.

## 장점과 비용

| 장점 | 비용 |
|---|---|
| 서비스별 독립 history와 release | 서비스와 부모 저장소를 각각 commit해야 함 |
| 저장소별 권한 분리 | detached HEAD와 pointer 갱신 이해 필요 |
| 전체 버전 조합 고정 | clone 시 recursive 초기화 필요 |
| GHCR digest로 실행 바이너리 추적 | 공통 계약의 호환성 관리 필요 |

Submodule은 진짜 monorepo를 만드는 기능이 아니다. 여러 독립 저장소를 특정 버전 조합으로 묶는 도구다. 이 특성을 이해하면 상위 저장소는 전체 시스템의 배포 manifest 역할을 할 수 있다.

---

# 5. containerd와 Cilium은 무엇이 다른가

## 한 문장으로 구분하기

- containerd는 컨테이너를 실행한다.
- Cilium은 실행된 Pod가 네트워크로 통신하고 정책을 적용받게 만든다.

## Pod 생성 흐름

```text
Pod 생성 요청
    |
kube-apiserver
    |
kubelet
    |
    +-- CRI -> containerd -> image pull, container 생성과 실행
    |
    +-- CNI -> Cilium -> veth, Pod IP, route, policy, eBPF
```

| 항목 | containerd | Cilium |
|---|---|---|
| Kubernetes 인터페이스 | CRI | CNI |
| 핵심 책임 | 컨테이너 생명주기 | Pod 네트워크와 보안 정책 |
| 이미지 pull | 담당 | 담당하지 않음 |
| namespace와 cgroup | 담당 | 네트워크 namespace 연결에 관여 |
| Pod IP와 routing | 담당하지 않음 | 담당 |
| NetworkPolicy | 담당하지 않음 | 담당 |
| Service load balancing | 담당하지 않음 | eBPF 구성에 따라 담당 |
| 네트워크 관측 | 제한적 | Hubble 연동 가능 |

containerd가 없으면 kubelet이 컨테이너를 실행할 수 없다. Cilium이 없으면 컨테이너 프로세스가 만들어져도 Kubernetes가 기대하는 Pod 네트워크가 준비되지 않는다. 두 구성 요소는 서로를 대체하지 않는다.

현재 클러스터에서는 containerd와 Cilium Agent가 모든 Kubernetes 노드에 필요하다. 반면 `kubectl`은 API client이므로 관리 PC나 Control Plane 등 필요한 위치에만 있어도 된다.

---

# 6. MSA 데이터베이스는 어디까지 분리해야 하는가

## 분리 수준은 한 단계가 아니다

데이터베이스 분리는 계정 하나를 더 만드는 것부터 VM 자체를 나누는 것까지 여러 수준이 있다.

| 단계 | 구성 | 논리적 격리 | 장애 격리 | 운영 비용 |
|---:|---|---:|---:|---:|
| 1 | 한 DB, 서비스별 schema와 role | 중간 | 낮음 | 낮음 |
| 2 | 한 instance, 서비스별 database와 role | 중상 | 낮음 | 낮음 |
| 3 | 서비스별 PostgreSQL instance | 높음 | 중간 | 높음 |
| 4 | 서비스별 Storage VM | 매우 높음 | 높음 | 매우 높음 |
| 5 | Kubernetes Postgres Operator | 정책에 따라 다름 | 구성에 따라 다름 | 학습 및 운영 비용 높음 |

## 현재 프로젝트의 권장 시작점

사이드 프로젝트와 단일 Windows 호스트라는 조건에서는 Storage VM 한 대와 PostgreSQL instance 한 개를 유지하되 서비스별 schema, migrator role, runtime role을 분리하는 것이 현실적이다.

```text
PostgreSQL instance
|
+-- user_service schema
|   +-- user_migrator
|   +-- user_app
|
+-- community_service schema
|   +-- community_migrator
|   +-- community_app
|
+-- stock_service schema
|   +-- stock_migrator
|   +-- stock_app
|
+-- member_bff schema
    +-- member_bff_migrator
    +-- member_bff_app
```

Flyway를 실행하는 migrator는 자기 schema의 DDL 권한을 가진다. 런타임 애플리케이션 계정은 필요한 DML 권한만 가진다.

```sql
CREATE ROLE community_migrator LOGIN PASSWORD '<secret>';
CREATE ROLE community_app LOGIN PASSWORD '<secret>';

CREATE SCHEMA community_service AUTHORIZATION community_migrator;

GRANT CONNECT ON DATABASE springmsa
TO community_migrator, community_app;

GRANT USAGE ON SCHEMA community_service TO community_app;

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA community_service
TO community_app;
```

새 테이블에도 권한이 이어지도록 default privileges를 설정하고 `public` schema의 불필요한 생성 권한도 제한해야 한다.

## 다른 서비스 테이블 권한을 주면 안 되는 이유

Community 이벤트 때문에 Stock 데이터가 바뀌어야 한다고 해도 `community_app`에 Stock 테이블 INSERT 권한을 주면 안 된다.

```text
피해야 할 구조
Community Service -> community_app -> Stock table 직접 INSERT

권장 구조
Community Service -> Outbox -> Kafka -> Stock Consumer
                                  -> stock_app -> Stock table INSERT
```

서비스가 자기 데이터의 유일한 writer가 돼야 schema 변경, 장애 격리, 감사 추적이 가능해진다. 동기 응답이 반드시 필요하면 Stock API를 호출하고, 비동기로 처리할 수 있으면 Kafka 이벤트를 사용한다.

## 물리적 분리는 언제 하는가

다음 조건이 실제로 나타났을 때 별도 instance 또는 VM을 고려한다.

- 한 서비스의 I/O가 다른 서비스의 latency를 침범한다.
- 백업과 복구 시점이 서비스마다 달라야 한다.
- PostgreSQL 버전이나 extension 요구가 다르다.
- 보안 경계상 OS 또는 네트워크 수준 격리가 필요하다.
- 서비스별 독립 failover와 확장이 필요하다.

MSA라는 이유만으로 VM을 서비스 수만큼 만드는 것은 자원과 운영 절차만 늘릴 수 있다. 먼저 소유권과 계정을 분리하고 실제 병목을 측정한 뒤 물리적 분리로 이동하는 편이 낫다.

## Postgres Operator는 무엇을 해결하는가

Postgres Operator는 Kubernetes에서 복제, failover, backup 같은 PostgreSQL 수명주기를 자동화한다. 서비스별 데이터 소유권을 대신 설계해 주지는 않는다.

현재 학습 순서는 다음이 적절하다.

1. 단일 PostgreSQL에서 schema와 role을 분리한다.
2. 타 서비스 schema 접근이 실제로 차단되는지 권한 테스트를 작성한다.
3. 서비스 간 변경을 API 또는 Kafka로만 수행한다.
4. backup, restore, failover 요구가 생기면 별도 instance와 Operator를 비교한다.

---

# 7. Active-Active와 Active-Standby, Worker 2대와 3대의 차이

## 용어부터 구분하기

Active-Active는 둘 이상의 인스턴스가 평상시에도 요청을 처리한다. Active-Standby는 Active가 요청을 처리하고 Standby는 장애 시 승격된다.

```text
Active-Active
Load Balancer -> instance A
              -> instance B

Active-Standby
Load Balancer -> instance A
                 instance B waits
```

Kubernetes Worker 여러 대를 둔다고 자동으로 모든 서비스가 HA가 되는 것은 아니다. Deployment replica가 여러 노드에 분산돼야 하고, Service와 Ingress가 정상 Pod로만 트래픽을 보내야 하며, 데이터 계층도 별도로 장애를 견뎌야 한다.

## Worker 2대 Active-Active

두 Worker에 서비스 replica를 하나씩 배치하면 평상시 두 노드가 모두 요청을 처리한다. 한 노드가 죽으면 남은 한 노드에 부하가 집중된다.

따라서 한 대 장애 후에도 같은 부하를 처리하려면 평상시 각 Worker 사용률을 이론상 약 50% 이하로 유지해야 한다. 실제 운영에서는 순간 부하와 재스케줄링 여유 때문에 50%보다 더 낮은 목표치를 잡는 편이 안전하다.

| 장점 | 단점 |
|---|---|
| 두 노드 자원을 평상시 모두 사용 | 한 대 장애 시 자원이 절반으로 감소 |
| 구조가 단순하고 비용이 낮음 | 유지보수 중 추가 장애를 견딜 여유가 없음 |
| 소규모 환경의 HA 실습에 적합 | replica 분산 실패 시 사실상 단일 장애점 |

필수 설정은 다음과 같다.

- 서비스별 `replicas: 2` 이상
- `podAntiAffinity` 또는 `topologySpreadConstraints`
- 적절한 readiness probe
- PodDisruptionBudget
- Worker 한 대가 모든 서비스를 감당할 수 있는 requests/limits와 실제 여유 자원

## Worker 3대 Active-Active

동일 용량 Worker가 3대이고 한 대 장애를 허용하는 N+1 구성이면, 단순 계산상 평상시 각 노드를 약 66% 이하로 사용해야 남은 두 대가 전체 부하를 받을 수 있다. 실무에서는 운영 여유를 추가로 둔다.

| 장점 | 단점 |
|---|---|
| 한 대 장애 후에도 두 노드가 남음 | VM과 메모리 비용 증가 |
| rolling update와 유지보수가 쉬움 | 스케줄링 정책을 더 정교하게 관리해야 함 |
| 분산과 quorum 관련 실험 범위 확대 | 단일 Windows 호스트 장애는 여전히 막지 못함 |

현재처럼 모든 VM이 같은 Windows PC에 있으면 Worker를 3대로 늘려도 호스트 전원, 디스크, Windows 장애에는 함께 중단된다. 이는 노드 장애 실습에는 유효하지만 물리 호스트 수준의 진짜 HA는 아니다.

## Active-Standby Worker 구성은 언제 쓰는가

Standby Worker를 비워 두거나 최소 workload만 배치하면 장애 시 확실한 여유 용량을 확보할 수 있다. 하지만 평상시에 자원이 놀고, 승격과 재스케줄링 시간이 필요하다.

| 요구사항 | 추천 방향 |
|---|---|
| 비용이 중요하고 무중단 실습이 목적 | Worker 2대 Active-Active |
| 유지보수와 한 대 장애 후 여유가 중요 | Worker 3대 Active-Active |
| 평상시 부하가 낮고 확실한 예비 용량 필요 | Active-Standby 고려 |
| 엄격한 물리 장애 대응 | 서로 다른 물리 호스트와 Control Plane HA 필요 |

## 현재 프로젝트의 선택

현재는 `worker-1`, `worker-2`를 Application Node Pool의 Active-Active로 사용한다. 애플리케이션 replica를 두 노드에 분산하고 실제 부하 테스트를 통해 한 노드 장애 시 남은 노드가 감당하는 CPU, 메모리, latency를 측정한다.

Platform과 Observability 노드를 별도로 둔 이유는 애플리케이션 부하가 Argo CD, Prometheus, Loki 같은 운영 구성 요소를 밀어내지 않도록 자원과 스케줄링 경계를 만들기 위해서다.

다만 현재 Control Plane은 1대, PostgreSQL은 1대, Redis는 1대, Kafka Broker는 1대다. 따라서 애플리케이션 Worker만 Active-Active일 뿐 전체 시스템이 end-to-end HA인 것은 아니다.

---

# 8. Authorization Server를 2 replicas로 늘렸더니 OAuth가 불안정해진 이유

## 관찰된 현상

애플리케이션 서비스를 2 replicas로 운영하던 중 Authorization Server도 2개로 늘렸다. 그러자 OAuth 로그인이 간헐적으로 실패했다.

실제 테스트 결과는 다음과 같았다.

| 구성 | 로그인 결과 |
|---|---|
| Auth Server 2 replicas, 재시작 전 | 5회 중 2회 성공 |
| Auth Server 2 replicas, 재시작 후 | 8회 모두 실패 |
| Auth Server 1 replica | 5회 모두 성공 |
| Auth Server 1 replica 전체 E2E | Member/Admin 로그인, CRUD, Toss API, 로그아웃 성공 |

실패 시 callback은 다음 형태로 돌아왔다.

```text
/?error=oauth2_login_failed
```

## Redis Session이 있는데 왜 실패했을까

Redis에는 Auth Server와 BFF의 Spring Session key가 존재했다. 즉 HTTP 로그인 세션은 공유되고 있었다.

하지만 HTTP Session과 OAuth2 Authorization 정보는 같은 저장소가 아니다.

Spring Authorization Server의 `OAuth2AuthorizationService`는 authorization code, access token, refresh token 등 OAuth 인가 상태를 저장하고 조회하는 핵심 컴포넌트다. 별도로 Bean을 구성하지 않으면 in-memory 구현이 기본이며 공식 문서도 이를 개발과 테스트 용도로 설명한다.

2 replicas에서 다음 흐름이 발생할 수 있다.

```text
Browser
  -> /oauth2/authorize
  -> Auth Pod A
  -> authorization code를 Pod A 메모리에 저장

BFF
  -> /oauth2/token
  -> Kubernetes Service가 Auth Pod B로 전달
  -> Pod B 메모리에는 해당 code가 없음
  -> invalid_grant 계열 실패
  -> oauth2_login_failed
```

Redis Spring Session은 사용자의 로그인 세션을 공유했지만 Pod A의 JVM heap에 저장된 authorization code까지 공유하지는 않았다.

이 프로젝트에서 1 replica로 줄였을 때 안정화된 결과와 Spring Authorization Server의 기본 저장 방식이 이 설명과 일치한다. 다만 최종 확증을 위해서는 Token Endpoint의 실제 `invalid_grant` 로그와 Pod 이름을 함께 남기는 것이 좋다.

## Sticky Session이 완전한 해결책이 아닌 이유

브라우저의 `/oauth2/authorize` 요청과 BFF의 `/oauth2/token` 요청은 호출 주체와 연결이 다르다. Ingress cookie affinity를 브라우저에 적용해도 서버 간 token 교환이 같은 Pod로 간다는 보장이 없다.

Sticky Session은 임시 완화책이 될 수 있지만 OAuth 상태 저장소를 공유하지 않은 구조적 문제를 해결하지 못한다.

## 올바른 다중화 구성

Auth Server를 Active-Active로 운영하려면 최소한 다음 상태를 공유하거나 고정해야 한다.

| 상태 | 권장 구성 |
|---|---|
| Registered Client | JDBC 저장소 또는 동일한 외부 구성 |
| OAuth2 Authorization | `JdbcOAuth2AuthorizationService` |
| Authorization Consent | `JdbcOAuth2AuthorizationConsentService` |
| HTTP Session | 현재처럼 Spring Session Redis |
| JWK 서명 키 | Kubernetes Secret 등에서 동일한 고정 key 로드 |
| BFF Authorized Client | 세션 또는 JDBC 기반으로 replica 간 일관성 확인 |

JWK를 Pod 시작 때마다 새로 생성한다면 Pod마다 서로 다른 키로 JWT를 발급할 수 있다. 이 경우 authorization code 문제를 해결한 뒤에도 토큰 검증과 key rotation에서 장애가 생길 수 있다. 모든 replica가 같은 private key와 `kid`를 사용하도록 Secret에서 로드해야 한다.

## 적용 순서

현재 manifest는 안정성을 위해 Authorization Server를 1 replica로 유지한다. 이후 다음 순서로 다중화를 다시 시도한다.

1. OAuth2 Authorization 및 Consent JDBC schema를 Flyway로 추가한다.
2. `JdbcOAuth2AuthorizationService`와 `JdbcOAuth2AuthorizationConsentService`를 Bean으로 등록한다.
3. Registered Client 저장 방식도 외부 저장소 또는 완전히 동일한 선언으로 통일한다.
4. RSA 또는 EC JWK를 생성해 Kubernetes Secret으로 배포한다.
5. 모든 Auth Pod가 같은 key와 issuer를 사용하는지 확인한다.
6. Auth Server를 2 replicas로 변경한다.
7. 연속 로그인, refresh token, logout, revoke, Pod 강제 삭제를 테스트한다.
8. 요청을 처리한 Pod 이름과 OAuth 오류 코드를 로그에 포함한다.

## 필요한 장애 테스트

```text
1. 로그인 20회 연속 수행
2. authorization 요청 직후 Auth Pod 하나 삭제
3. token 교환 성공 여부 확인
4. access token 발급 후 다른 Pod에서 검증
5. refresh token으로 재발급
6. logout 및 token revoke
7. 두 Pod를 순차 재시작하고 기존 세션 확인
```

단순히 `replicas: 2`로 바꾸는 것은 프로세스 수를 늘리는 일일 뿐이다. 애플리케이션이 메모리에 보관하던 상태를 외부화하지 않으면 오히려 요청이 어느 Pod에 도착했는지에 따라 성공 여부가 달라진다.

## 이 편의 결론

- Redis Session 공유만으로 Authorization Server가 stateless해지는 것은 아니다.
- OAuth authorization code와 consent는 별도 저장소를 사용한다.
- 기본 in-memory authorization service는 다중 replica 운영에 적합하지 않다.
- JWK도 모든 replica에서 동일하게 관리해야 한다.
- 현재는 1 replica로 안정성을 확보했고 JDBC 및 고정 JWK 적용 후 2 replicas를 다시 검증한다.

---

# 전체 회고

이번 구축에서 반복해서 확인한 원칙은 "구성 요소의 개수보다 책임과 상태의 위치를 먼저 확인해야 한다"는 것이다.

- Hyper-V를 경유한다는 사실만으로 VirtualBox와 VMware의 성능이 같아지지는 않는다.
- iptables, nftables, Cilium은 이름이 함께 등장해도 서로 다른 계층과 책임을 가진다.
- containerd는 프로세스를 실행하고 Cilium은 그 프로세스가 속한 Pod의 네트워크를 만든다.
- Outbox는 분산 ACID가 아니라 로컬 트랜잭션과 이벤트 발행 의도를 연결한다.
- Submodule은 소스 commit 조합을, GHCR은 실행 이미지 버전을 관리한다.
- MSA의 DB 분리는 VM 개수보다 서비스별 소유권과 최소 권한에서 시작한다.
- Worker를 여러 대 만든 것만으로 전체 시스템이 HA가 되지는 않는다.
- Authorization Server replica를 늘리기 전에 메모리에 남아 있는 OAuth 상태를 외부화해야 한다.

다음 단계에서는 감상이 아니라 수치로 비교할 예정이다.

- 동일 vCPU와 RAM 조건의 VirtualBox 및 VMware Cilium 테스트 시간
- 한 Worker 장애 전후의 CPU, memory, p95 latency
- Kafka consumer lag과 Outbox 발행 지연
- PostgreSQL 계정별 접근 차단 테스트
- Auth Server 2 replicas에서 Pod별 OAuth 요청 trace

이 수치가 모이면 이번 글은 단순 설치기가 아니라 재현 가능한 장애 분석 기록으로 발전할 수 있다.

---

# 참고 자료

- [Oracle VirtualBox: Hyper-V와 함께 사용할 때의 성능 문제](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/Troubleshooting.html)
- [Microsoft Windows Hypervisor Platform API](https://learn.microsoft.com/en-us/virtualization/api/hypervisor-platform/hypervisor-platform)
- [Kubernetes: nftables mode for kube-proxy](https://kubernetes.io/blog/2025/02/28/nftables-kube-proxy/)
- [Kubernetes: Virtual IPs and Service Proxies](https://kubernetes.io/docs/reference/networking/virtual-ips/)
- [Cilium: kube-proxy replacement](https://docs.cilium.io/en/stable/network/kubernetes/kubeproxy-free/)
- [Git: Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [Spring Authorization Server Core Model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html)
- [PostgreSQL: Schemas](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [PostgreSQL: Privileges](https://www.postgresql.org/docs/current/ddl-priv.html)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Axon Framework Documentation](https://docs.axoniq.io/)
