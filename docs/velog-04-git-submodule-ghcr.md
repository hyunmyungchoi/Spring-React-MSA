# Git Submodule로 MSA 저장소를 묶고 GHCR로 배포하기

> Git Submodule, Multi-repository, GitHub Actions, GHCR, Kubernetes

## 시작하며

Spring MSA 프로젝트는 처음에 하나의 Git 저장소 안에 Backend, Frontend, Infra, Docs가 모두 들어 있었다. 서비스 수가 늘어나면서 각 서비스의 release와 history를 독립적으로 관리하고 싶어졌다.

완전한 monorepo를 유지할 수도 있고 각 서비스를 완전히 분리할 수도 있다. 이번 프로젝트에서는 서비스별 독립 저장소를 만들고 상위 저장소가 Git Submodule로 정확한 commit 조합을 가리키는 방식을 선택했다.

여기에 GitHub Container Registry, GHCR을 연결해 서비스별 실행 이미지를 Kubernetes에 배포했다.

## Organization과 Submodule은 다르다

GitHub Organization은 저장소와 사용자 권한을 묶는 관리 단위다. Git Submodule은 한 Git 저장소가 다른 저장소의 특정 commit을 참조하는 Git 기능이다.

```text
GitHub Organization
  +-- repository A
  +-- repository B
  +-- repository C

Parent repository
  +-- submodule A -> repository A의 특정 commit
  +-- submodule B -> repository B의 특정 commit
  +-- submodule C -> repository C의 특정 commit
```

Organization만 사용하면 저장소를 한 화면에서 관리할 수 있지만 특정 배포 시점에 어떤 조합을 사용했는지는 자동으로 고정되지 않는다. Submodule은 그 commit 조합을 상위 저장소에 기록한다.

두 기능은 함께 사용할 수 있지만 반드시 함께 써야 하는 것은 아니다.

## Submodule이 실제로 저장하는 것

상위 저장소에는 두 종류의 정보가 들어간다.

- `.gitmodules`에 하위 저장소 경로와 URL
- gitlink에 하위 저장소의 특정 commit SHA

```ini
[submodule "BackEnd/services/user-service"]
    path = BackEnd/services/user-service
    url = git@github.com:hyunmyungchoi/spring-msa-user-service.git
```

상위 저장소가 하위 저장소의 전체 Git history를 복제해 보관하는 것은 아니다.

## 하위 서비스가 바뀌면 부모도 수정해야 하는 이유

User Service에서 코드를 수정하고 commit과 push를 완료했다고 가정해 보자. 상위 저장소는 여전히 이전 User Service commit을 가리킨다.

```text
User Service remote
old commit -> new commit

Parent repository gitlink
old commit를 계속 가리킴
```

상위 저장소에서 pointer를 갱신해야 새 조합이 기록된다.

```bash
cd BackEnd/services/user-service
git pull
cd ../../..

git add BackEnd/services/user-service
git commit -m "chore: update user-service submodule"
git push
```

이 과정은 중복 commit처럼 보이지만 의미가 다르다.

- 하위 저장소 commit은 User Service 코드 변경을 기록한다.
- 상위 저장소 commit은 전체 시스템이 사용할 User Service 버전을 기록한다.

상위 저장소는 전체 배포 버전의 Bill of Materials 역할을 한다.

## Clone할 때 주의할 점

일반 clone만 실행하면 submodule directory가 비어 있거나 특정 commit이 checkout되지 않을 수 있다.

```bash
git clone --recurse-submodules <parent-repository-url>
```

이미 clone했다면 다음 명령을 사용한다.

```bash
git submodule update --init --recursive
```

상위 저장소가 기록한 commit으로 되돌리려면 하위 저장소에서 임의로 `git pull`하는 대신 상위 root에서 update해야 한다.

```bash
git submodule update --recursive
```

## Detached HEAD가 오류는 아니다

Submodule은 기본적으로 상위 저장소가 가리키는 정확한 commit을 checkout한다. 이때 하위 저장소가 detached HEAD 상태로 보일 수 있다.

그 상태에서 코드를 수정해 commit하려면 먼저 작업할 branch로 이동해야 한다.

```bash
cd BackEnd/services/user-service
git switch master
```

branch를 확인하지 않고 commit하면 나중에 commit을 찾기 어려워질 수 있다.

## GHCR은 무엇을 관리하는가

Submodule이 소스 버전을 관리한다면 GHCR은 빌드된 컨테이너 이미지 버전을 관리한다.

```text
Git commit
  -> GitHub Actions
  -> Gradle/Vite build
  -> Container image build
  -> GHCR push
  -> Kubernetes pull
```

이미지 이름은 서비스별로 분리한다.

```text
ghcr.io/hyunmyungchoi/spring-msa-user-service:<tag>
ghcr.io/hyunmyungchoi/spring-msa-community-service:<tag>
ghcr.io/hyunmyungchoi/spring-msa-stock-service:<tag>
ghcr.io/hyunmyungchoi/spring-msa-member-web:<tag>
```

GitHub Actions에는 package write 권한이 필요하다.

```yaml
permissions:
  contents: read
  packages: write

steps:
  - uses: actions/checkout@v4
    with:
      submodules: recursive

  - uses: docker/login-action@v3
    with:
      registry: ghcr.io
      username: ${{ github.actor }}
      password: ${{ secrets.GITHUB_TOKEN }}
```

Private submodule을 checkout할 때는 상위 workflow의 token이 해당 저장소들을 읽을 수 있어야 한다. `GITHUB_TOKEN`의 범위만으로 부족하면 read-only deploy key나 최소 권한 PAT를 검토해야 한다.

## Tag보다 Digest가 안전한 이유

`latest` 같은 tag는 같은 이름이 다른 이미지로 이동할 수 있다. 배포 시점에 어떤 바이너리가 실행됐는지 확실히 남기려면 digest를 사용할 수 있다.

```yaml
containers:
  - name: community-service
    image: ghcr.io/hyunmyungchoi/spring-msa-community-service@sha256:<digest>
```

| 방식 | 장점 | 주의점 |
|---|---|---|
| `latest` | 간단함 | 실행 이미지 추적이 어려움 |
| commit SHA tag | 소스 commit 추적 가능 | tag 변경을 막는 정책 필요 |
| digest | 바이너리를 정확히 고정 | 자동 갱신 절차가 필요 |

학습 환경에서는 commit SHA tag를 사용하고, 재현성이 중요한 배포에서는 digest로 고정하는 방식이 이해하기 쉽다.

## GHCR과 GitHub Packages Maven은 다르다

공통 Java library를 다른 서비스가 Gradle dependency로 사용하는 경우 GitHub Packages의 Maven Registry를 사용할 수 있다. 실행 가능한 컨테이너 이미지는 GHCR에 저장한다.

```text
공통 Java package
  -> GitHub Packages Maven Registry
  -> implementation("group:artifact:version")

서비스 실행 image
  -> GHCR
  -> Kubernetes Deployment image
```

둘 다 GitHub Packages 화면에 나타날 수 있지만 package format과 소비 방식은 다르다.

## 장점과 비용

| 장점 | 비용 |
|---|---|
| 서비스별 독립 history | 저장소 수 증가 |
| 서비스별 release와 권한 분리 | 여러 저장소의 변경 순서 관리 필요 |
| 상위 저장소에서 전체 조합 고정 | submodule pointer commit 필요 |
| GHCR에서 배포 이미지 추적 | image retention과 권한 관리 필요 |

서비스 간 계약도 별도로 관리해야 한다. API schema나 event schema가 바뀌면 여러 저장소가 동시에 깨질 수 있다. OpenAPI, AsyncAPI, 공통 contract package와 호환성 테스트가 필요하다.

## 결론

Git Submodule은 여러 저장소를 monorepo처럼 바꿔 주는 기능이 아니다. 독립 저장소의 정확한 commit 조합을 상위 저장소에 기록하는 기능이다.

GHCR은 그 소스로부터 빌드된 실행 이미지를 관리한다. Submodule SHA, Git commit tag, image digest를 연결하면 어떤 소스 조합이 어떤 컨테이너로 배포됐는지 추적할 수 있다.

## 참고 자료

- [Git Tools: Submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [GitHub Actions: Checking out submodules](https://github.com/actions/checkout)

