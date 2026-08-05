FROM node:24.19.0-alpine AS build
WORKDIR /workspace/FrontEnd

RUN corepack enable

COPY FrontEnd/package.json FrontEnd/pnpm-lock.yaml FrontEnd/pnpm-workspace.yaml FrontEnd/.npmrc ./
COPY FrontEnd/apps/admin-logs ./apps/admin-logs
COPY FrontEnd/packages/admin-common ./packages/admin-common
COPY FrontEnd/packages/api-contract ./packages/api-contract

RUN corepack pnpm install --filter @springmsa/admin-logs... --frozen-lockfile \
    && corepack pnpm --filter @springmsa/admin-logs build

FROM nginx:1.27-alpine
COPY infra/nginx/web/admin-web.conf /etc/nginx/conf.d/default.conf
RUN mkdir -p /usr/share/nginx/html/manage/logs
COPY --from=build /workspace/FrontEnd/apps/admin-logs/dist/ /usr/share/nginx/html/manage/logs/
