# Hyper-V가 켜진 Windows에서 VirtualBox를 포기하고 VMware로 옮긴 이유

> Windows 11, VT-x, Hyper-V, VBS, VirtualBox NEM, VMware Host VBS Mode, Kubernetes, Cilium

## 시작하며

Windows 11 호스트에 Ubuntu VM 세 대를 만들고 `kubeadm` 기반 Kubernetes와 Cilium을 설치했다. 구성 자체는 성공했고 Pod도 실행됐다. 문제는 `cilium connectivity test`를 실행하면서 시작됐다.

테스트가 일정 구간에서 급격히 느려지고 VM이 사실상 멈췄다. 운이 좋으면 조금씩 진행됐지만, 어떤 부팅에서는 `Begin: Loading essential drivers...`에서 오랫동안 정지했다. VM을 일시정지했다가 재개하면 다시 진행되는 이상한 현상도 있었다.

결론부터 말하면 같은 Windows 호스트에서 VMware Workstation으로 교체한 뒤 Cilium 전체 테스트가 완료됐다. 다만 이 글의 결론은 "VirtualBox는 느리고 VMware는 빠르다"가 아니다. 실제로 확인한 사실과 아직 증명하지 못한 추론을 분리해 정리하려 한다.

## 실제로 확인한 로그

VirtualBox 환경에서 멈춤이 발생할 때 Ubuntu 커널에는 다음 로그가 반복됐다.

```text
watchdog: BUG: soft lockup - CPU#0 stuck for ...
rcu_preempt self-detected stall on CPU
rcu_preempt kthread starved for ... jiffies
systemd-logind.service: Watchdog timeout
containerd-shim ... stuck
cilium-agent ... stuck
e1000 ... NETDEV WATCHDOG
```

처음에는 이 현상을 "CPU 누수"라고 불렀다. 하지만 정확한 표현은 아니다. 위 로그는 CPU 자원이 어딘가로 새어 나갔다는 뜻이 아니라 특정 vCPU가 오랫동안 정상적으로 스케줄링되지 못했다는 뜻에 가깝다.

게스트 커널 입장에서는 CPU를 받아 실행돼야 할 thread가 수십 초 이상 실행되지 않았다. 그 결과 RCU, systemd, containerd, Cilium, 가상 NIC watchdog이 연쇄적으로 timeout을 기록했다.

## SQLite가 원인이었을까

과거 단일 노드 k3s를 사용했을 때도 비슷한 정지 현상이 있었다. k3s 단일 서버는 기본 datastore로 SQLite를 사용할 수 있으므로 처음에는 "가상 디스크가 느려서 SQLite가 막힌 것"이라고 생각했다.

하지만 현재 상황과는 분리해서 봐야 한다.

- 과거 환경은 단일 노드 k3s였다.
- 현재 환경은 `kubeadm` Kubernetes이고 Control Plane 저장소는 etcd다.
- `cilium connectivity test` 자체가 SQLite를 사용하는 것은 아니다.
- 현재 로그가 직접 증명하는 것은 CPU stall이지 SQLite lock이 아니다.

스토리지 지연이 전체 현상을 악화했을 가능성은 있다. 그러나 `iostat`, disk latency, SQLite lock time을 측정하지 않은 상태에서 SQLite를 직접 원인이라고 결론 내릴 수는 없다.

## VT-x와 Hyper-V의 관계

VT-x는 Intel CPU가 제공하는 하드웨어 가상화 기능이다. 게스트 운영체제가 privileged instruction, 메모리 주소 변환, CPU 실행 상태 전환을 효율적으로 수행하도록 돕는다. EPT는 게스트 가상 주소에서 호스트 물리 주소로 이어지는 메모리 변환을 지원한다.

Hyper-V는 VT-x 그 자체가 아니다. Microsoft가 VT-x와 EPT를 이용해 VM과 VBS 격리 영역을 실행하는 하이퍼바이저다.

```text
Intel CPU
  +-- VT-x
  +-- EPT
       |
       v
Microsoft Hyper-V Hypervisor
       |
       v
Windows Hypervisor Platform
       |
       +-- VirtualBox NEM
       +-- VMware Host VBS Mode
```

VBS는 Hyper-V가 제공하는 격리 영역을 이용해 Windows의 보안 기능을 실행한다. Memory Integrity, Credential Guard, 일부 Code Integrity 정책 등이 이 계층과 연관된다.

따라서 Windows 기능 화면에서 Hyper-V 항목을 껐다고 항상 하이퍼바이저가 내려가는 것은 아니다. VBS나 부팅 단계의 보안 정책이 하이퍼바이저를 계속 요구할 수 있다.

## VirtualBox와 VMware가 VT-x를 직접 사용하지 못한다는 의미

Hyper-V가 활성화된 호스트에서는 Microsoft 하이퍼바이저가 VT-x를 먼저 사용한다. 다른 데스크톱 가상화 제품이 VT-x를 독점적으로 제어하는 대신 Windows가 제공하는 하이퍼바이저 인터페이스를 경유할 수 있다.

VirtualBox는 이 호환 경로를 NEM이라고 표현하고, VMware Workstation은 Host VBS Mode라는 표현을 사용한다.

두 제품 모두 최하단에서 Hyper-V 계층을 경유할 수 있지만 같은 구현은 아니다.

| 항목 | VirtualBox NEM | VMware Host VBS Mode |
|---|---|---|
| 하단 하이퍼바이저 | Hyper-V/WHP | Hyper-V 계열 인터페이스 |
| vCPU 실행 관리 | VirtualBox 구현 | VMware 구현 |
| 가상 장치 | VirtualBox 장치 모델 | VMware 장치 모델 |
| 인터럽트와 타이머 | VirtualBox 구현 | VMware 구현 |
| 디스크 및 네트워크 I/O | VirtualBox 구현 | VMware 구현 |
| 핵심 구현 공개 범위 | 상대적으로 넓음 | 대부분 비공개 |

같은 하이퍼바이저 API를 호출하더라도 VM exit, vCPU 재진입, timer, interrupt, virtual NIC, disk queue를 처리하는 상위 구현은 다를 수 있다. 따라서 두 제품의 성능과 안정성이 반드시 같을 이유는 없다.

## 왜 일시정지 후 재개하면 살아났을까

부팅이나 테스트가 멈췄을 때 VM을 일시정지하고 재개하면 진행되는 경우가 있었다. 이것은 손상된 드라이버가 복구된 것이라기보다 pause/resume 과정에서 vCPU 실행, timer 전달, 가상 장치 상태가 다시 동기화되면서 일시적으로 진행된 것으로 추론할 수 있다.

다만 제품 내부 trace 없이 "NEM의 특정 코드가 고쳐졌다"거나 "정확히 timer bug다"라고 확정할 수는 없다.

## VMware로 교체한 뒤 달라진 점

VMware Workstation에서 Control Plane과 Worker VM을 다시 만들고 Kubernetes와 Cilium을 구성했다. VirtualBox에서 반복적으로 멈추던 구간을 통과했고 `cilium connectivity test` 132개 항목이 끝까지 실행됐다.

테스트 이후 표시된 실패는 현재 통신 장애가 아니라 VM 재부팅 과정에서 누적된 Cilium 컨테이너 restart count를 검사한 결과였다. 최근 로그에서는 새로운 soft lockup과 fatal 오류가 확인되지 않았다.

체감 속도도 크게 향상됐다. 그러나 동시에 변경된 조건이 있다.

| 조건 | VirtualBox | VMware |
|---|---:|---:|
| vCPU | 2 | 4 |
| 메모리 | 약 4GB | 약 4GB |
| VM 저장 위치 | C 드라이브 계열 | `D:\VMware VMs` |
| 실행 경로 | NEM | Host VBS Mode |
| Cilium 테스트 | 정지 및 soft lockup | 전체 실행 완료 |

vCPU가 두 배로 증가했고 VM 저장 위치도 바뀌었다. 그러므로 성능 개선 전체를 VMware 실행 엔진 하나의 효과로 정량화할 수는 없다.

확실하게 말할 수 있는 범위는 다음과 같다.

> 이 Windows 호스트와 Kubernetes 부하 조합에서 VirtualBox NEM 환경은 반복적인 vCPU stall을 보였고, VMware Host VBS Mode 환경으로 옮긴 뒤 같은 문제가 재현되지 않았다.

## 정확한 비교를 위한 다음 실험

제품 차이를 더 정확하게 비교하려면 조건을 통제해야 한다.

```text
동일한 Ubuntu 이미지
동일한 kernel/containerd/Kubernetes/Cilium 버전
동일한 vCPU와 RAM
동일한 가상 디스크 위치와 크기
동일한 NIC 구성
동일한 Cilium test 옵션
```

수집할 지표도 미리 정해야 한다.

```bash
vmstat 1
iostat -xz 1
pidstat -u -d 1
dmesg --follow
cilium connectivity test
```

호스트에서는 CPU 사용률뿐 아니라 VM 프로세스의 ready time과 디스크 latency도 함께 비교해야 한다.

## 결론

- VT-x는 CPU 기능이고 Hyper-V는 이를 사용하는 하이퍼바이저다.
- VBS가 활성화된 Windows에서는 Hyper-V가 계속 실행될 수 있다.
- VirtualBox NEM과 VMware Host VBS Mode는 같은 실행 코드가 아니다.
- 이번 장애는 CPU 누수보다 vCPU starvation 또는 scheduling stall로 표현하는 것이 정확하다.
- SQLite는 측정 없이 직접 원인으로 단정할 수 없다.
- 내 환경에서는 VMware로 교체한 뒤 Cilium 테스트가 정상 완료됐다.

## 참고 자료

- [Oracle VirtualBox Troubleshooting](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/Troubleshooting.html)
- [Microsoft Windows Hypervisor Platform API](https://learn.microsoft.com/en-us/virtualization/api/hypervisor-platform/hypervisor-platform)
- [Microsoft WHvRunVirtualProcessor](https://learn.microsoft.com/en-us/virtualization/api/hypervisor-platform/funcs/whvrunvirtualprocessor)

