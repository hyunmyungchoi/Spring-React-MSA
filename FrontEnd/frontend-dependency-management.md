# Frontend Dependency Management

## Project Stack

This frontend project uses React, TypeScript, Vite, React Router, Redux Toolkit, RTK Query, Tailwind CSS, and Axios.

## Package Manager

Use **pnpm** only.

Do not mix package managers.

```text
Allowed:
- pnpm-lock.yaml

Not allowed:
- package-lock.json
- yarn.lock
```

## Dependencies

| Purpose | Package | Version |
|---|---|---:|
| React | `react` | `19.2.6` |
| React DOM | `react-dom` | `19.2.6` |
| React Router | `react-router-dom` | `7.15.1` |
| Redux Toolkit | `@reduxjs/toolkit` | `2.11.2` |
| React Redux | `react-redux` | `9.3.0` |
| Axios | `axios` | `1.16.1` |

## Dev Dependencies

| Purpose | Package | Version |
|---|---|---:|
| TypeScript | `typescript` | `6.0.3` |
| Vite | `vite` | `8.0.13` |
| Vite React Plugin | `@vitejs/plugin-react` | `6.0.2` |
| Tailwind CSS | `tailwindcss` | `4.3.0` |
| Tailwind Vite Plugin | `@tailwindcss/vite` | `4.3.0` |
| React Types | `@types/react` | `19.2.14` |
| React DOM Types | `@types/react-dom` | `19.2.3` |

## Create Project

```bash
pnpm create vite spring-msa-front --template react-ts
cd spring-msa-front
```

## Install Runtime Dependencies

```bash
pnpm add react@19.2.6 react-dom@19.2.6 react-router-dom@7.15.1 @reduxjs/toolkit@2.11.2 react-redux@9.3.0 axios@1.16.1
```

## Install Dev Dependencies

```bash
pnpm add -D typescript@6.0.3 vite@8.0.13 @vitejs/plugin-react@6.0.2 tailwindcss@4.3.0 @tailwindcss/vite@4.3.0 @types/node@24.12.3 @types/react@19.2.14 @types/react-dom@19.2.3
```

## RTK Query

RTK Query is included in Redux Toolkit.

Do not install it separately.

```ts
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
```

## Recommended package.json Policy

Add this field to `package.json`.

```json
{
  "packageManager": "pnpm@10.0.0"
}
```

## Recommended Node Version

Create `.nvmrc`.

```text
24.18.0
```

## Development Commands

```bash
pnpm install
pnpm dev
pnpm build
pnpm preview
```

## CI/CD Install Command

Use frozen lockfile in CI/CD.

```bash
pnpm install --frozen-lockfile
```

## Dependency Management Rules

```text
1. Use pnpm only.
2. Commit pnpm-lock.yaml.
3. Do not commit package-lock.json.
4. Do not commit yarn.lock.
5. Do not use --force for normal installation.
6. Do not use --legacy-peer-deps for normal installation.
7. Fix peer dependency conflicts by aligning package versions.
8. Keep Node and pnpm versions fixed.
```

## Bad Commands

Avoid these unless it is an emergency workaround.

```bash
npm install -f
npm install --force
npm install --legacy-peer-deps
```

## Clean Reinstall

```bash
rm -rf node_modules pnpm-lock.yaml
pnpm install
```

## Version Check

```bash
node -v
pnpm -v
pnpm list
```

## Docker Entry Images

Frontend deployable entries use separate Dockerfiles. The default compose services build the full member/admin SPAs.

```bash
docker compose up --build spring-member-web
docker compose up --build spring-admin-web
```

Build a single entry image directly when you need the same target as CI.

```bash
docker build -f FrontEnd/apps/member/Dockerfile.community -t spring-react-msa/spring-community-web:local .
docker build -f FrontEnd/apps/member/Dockerfile.stock -t spring-react-msa/spring-stock-web:local .
docker build -f FrontEnd/apps/admin/Dockerfile.users -t spring-react-msa/spring-admin-users-web:local .
docker build -f FrontEnd/apps/admin/Dockerfile.logs -t spring-react-msa/spring-admin-logs-web:local .
```

Kubernetes frontend deployments use separate images for independently deployable entries.

| Deployment | Dockerfile | Mount path |
|---|---|---|
| `spring-member-web` | `FrontEnd/apps/member/Dockerfile.member` | `/` |
| `spring-community-web` | `FrontEnd/apps/member/Dockerfile.community` | `/community` |
| `spring-stock-web` | `FrontEnd/apps/member/Dockerfile.stock` | `/stock` |
| `spring-admin-web` | `FrontEnd/apps/admin/Dockerfile.admin` | `/` |
| `spring-admin-users-web` | `FrontEnd/apps/admin/Dockerfile.users` | `/manage/users` |
| `spring-admin-logs-web` | `FrontEnd/apps/admin/Dockerfile.logs` | `/manage/logs` |

Each entry Dockerfile fixes its own Vite mode and output mount path, so GitHub Actions does not pass `VITE_MODE` or `WEB_MOUNT_PATH`.
