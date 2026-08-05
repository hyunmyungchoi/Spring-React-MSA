# Frontend workspace boundaries

The frontend remains in one Git repository but is split into independently buildable and deployable workspace applications.

| Workspace | Route | Shared package |
| --- | --- | --- |
| `member` | `/`, `/auth`, `/chat` | `@springmsa/member-common` |
| `@springmsa/member-community` | `/community` | `@springmsa/member-common` |
| `@springmsa/member-stock` | `/stock` | `@springmsa/member-common` |
| `admin` | `/`, `/auth` | `@springmsa/admin-common` |
| `@springmsa/admin-users` | `/manage/users` | `@springmsa/admin-common` |
| `@springmsa/admin-logs` | `/manage/logs` | `@springmsa/admin-common` |

`@springmsa/api-contract` owns the common `MsaResponse` parser and generates TypeScript declarations from the Member and Admin BFF OpenAPI endpoints.

```powershell
cd D:\Project\SpringMSA\FrontEnd
corepack pnpm install
corepack pnpm contracts:generate
corepack pnpm -r build
```

The four feature applications have independent package manifests, Vite configurations, tests, Dockerfiles, CI image selection, and deployment outputs. This is the safe boundary for a future polyrepo split. Creating separate Git repositories is intentionally deferred until repository names and remote URLs are selected; nesting unrelated `.git` directories in this repository would break the current CI and shared-package workflow.
