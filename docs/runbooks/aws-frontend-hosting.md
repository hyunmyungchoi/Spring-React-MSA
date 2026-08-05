# AWS frontend S3 and CloudFront deployment runbook

> Reference-only: local VM development is the current target. Revalidate Terraform state, account IDs, certificates, domains, and saved plans before any future AWS apply.

## Deployment matrix

| Target | Workspace | Build command | CloudFront behavior |
| --- | --- | --- | --- |
| `spring-member-web` | `member` | `pnpm --filter member build:prod` | default member path |
| `spring-member-community-web` | `@springmsa/member-community` | `pnpm --filter @springmsa/member-community build` | `/community/*` |
| `spring-member-stock-web` | `@springmsa/member-stock` | `pnpm --filter @springmsa/member-stock build` | `/stock/*` |
| `spring-admin-web` | `admin` | `pnpm --filter admin build:prod` | default admin path |
| `spring-admin-users-web` | `@springmsa/admin-users` | `pnpm --filter @springmsa/admin-users build` | `/manage/users/*` |
| `spring-admin-logs-web` | `@springmsa/admin-logs` | `pnpm --filter @springmsa/admin-logs build` | `/manage/logs/*` |

Each target must publish only its own `dist` directory and invalidate only its matching CloudFront paths.

## Local contract validation

```powershell
Set-Location D:\Project\SpringMSA\FrontEnd
corepack pnpm install --frozen-lockfile
corepack pnpm -r test
corepack pnpm -r build
```

The shared API contract package is `@springmsa/api-contract`. Member and admin common UI/runtime code is provided by `@springmsa/member-common` and `@springmsa/admin-common`.

## AWS preflight

Before planning or applying:

1. Confirm the active AWS account and region.
2. Confirm Terraform state ownership and locking.
3. Confirm the S3 buckets are private and use CloudFront Origin Access Control.
4. Confirm ACM certificates and Route 53 records belong to the intended account.
5. Confirm GitHub OIDC roles are restricted to the intended repository and branch.
6. Produce and review a new Terraform plan. Do not reuse historical plan files or checksums.

## Selective deployment

Example for the stock frontend:

```powershell
gh workflow run aws-frontend-deploy.yml `
  --repo hyunmyungchoi/Spring-React-MSA `
  --ref master `
  -f deploy_target=spring-member-stock-web
```

The workflow must build only `@springmsa/member-stock`, sync only the stock bucket, and invalidate only `/stock` and `/stock/*`.

## Smoke checks

After deployment, verify all six entry points return the expected HTML and assets:

- Member root
- Community
- Stock
- Admin root
- Admin users
- Admin logs

Also verify OAuth redirects, API routes, logout, session cookies, and WebSocket routes against the configured public domains.

## Rollback

Redeploy the previous known-good Git revision for only the affected target. Keep S3 versioning enabled, but treat a complete application build as the rollback unit so that HTML and hashed assets stay consistent.