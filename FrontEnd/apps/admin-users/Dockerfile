FROM node:24.19.0-alpine AS build
WORKDIR /workspace/FrontEnd

RUN corepack enable

COPY FrontEnd/package.json FrontEnd/pnpm-lock.yaml FrontEnd/pnpm-workspace.yaml FrontEnd/.npmrc ./
COPY FrontEnd/apps/admin-users ./apps/admin-users
COPY FrontEnd/packages/admin-common ./packages/admin-common
COPY FrontEnd/packages/api-contract ./packages/api-contract

RUN corepack pnpm install --filter @springmsa/admin-users... --frozen-lockfile \
    && corepack pnpm --filter @springmsa/admin-users build

FROM nginx:1.27-alpine
COPY infra/nginx/web/admin-web.conf /etc/nginx/conf.d/default.conf
RUN mkdir -p /usr/share/nginx/html/manage/users
COPY --from=build /workspace/FrontEnd/apps/admin-users/dist/ /usr/share/nginx/html/manage/users/
