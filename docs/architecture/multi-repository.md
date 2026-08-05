# Multi-repository operation

## Decision

`Spring-React-MSA` is the meta repository. Backend services, frontend apps, and shared packages are independent repositories linked through Git submodules.

This gives each deployable unit an independent history and release lifecycle while the parent commit records one reproducible system composition.

## Repository ownership

The parent repository owns:

- `.gitmodules`
- `FrontEnd/package.json`
- `FrontEnd/pnpm-lock.yaml`
- `infra/`
- `docs/`
- root development metadata

Each submodule owns every file below its registered path. Never commit a child source change only in the parent. The parent stores only the child commit SHA.

## Initial clone

```powershell
git clone --recurse-submodules https://github.com/hyunmyungchoi/Spring-React-MSA.git
cd Spring-React-MSA
```

For an existing clone:

```powershell
git submodule sync --recursive
git submodule update --init --recursive
```

## Change a service

```powershell
cd BackEnd\spring-user-service
git switch main
git pull --ff-only
git add .
git commit -m "feat: describe the service change"
git push origin main
cd ..\..
git add BackEnd\spring-user-service
git commit -m "chore: update user service pointer"
git push origin main
```

The same sequence applies to frontend submodules.

## Update all child pointers

```powershell
git submodule update --remote --merge
git submodule foreach --recursive "git status --short"
git add BackEnd FrontEnd .gitmodules
git commit -m "chore: update submodule pointers"
git push origin main
```

Review child changes before committing the parent pointer update. A pointer change can include behavior from multiple child commits.

## Shared packages

Backend services consume the Maven packages published by:

- `spring-msa-common-web`
- `spring-msa-common-kafka`

Frontend apps consume the npm packages published by `spring-msa-frontend-common`:

- `@hyunmyungchoi/api-contract`
- `@hyunmyungchoi/member-common`
- `@hyunmyungchoi/admin-common`

Publishing requires a GitHub token with `read:packages` and `write:packages`. Package workflows use `GITHUB_TOKEN` in each common repository.

## Versioning rule

1. Change and test the common package.
2. Increment its package version.
3. Publish the package.
4. Update consuming repositories and their lock files.
5. Test and push each consumer.
6. Update the meta-repository submodule pointers last.

Do not overwrite an existing immutable package version.

## Recovery

If a submodule directory is empty:

```powershell
git submodule update --init --recursive
```

If a submodule URL changed:

```powershell
git submodule sync --recursive
git submodule update --init --recursive
```

If the parent reports a dirty submodule, enter that directory and inspect `git status`. Do not reset it from the parent because it may contain uncommitted service work.
