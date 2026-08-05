FROM node:24.19.0-alpine AS build
WORKDIR /workspace/FrontEnd

RUN corepack enable

COPY FrontEnd/package.json FrontEnd/pnpm-lock.yaml FrontEnd/pnpm-workspace.yaml FrontEnd/.npmrc ./
COPY FrontEnd/apps/member-stock ./apps/member-stock
COPY FrontEnd/packages/member-common ./packages/member-common
COPY FrontEnd/packages/api-contract ./packages/api-contract

RUN corepack pnpm install --filter @springmsa/member-stock... --frozen-lockfile \
    && corepack pnpm --filter @springmsa/member-stock build

FROM nginx:1.27-alpine
COPY infra/nginx/web/member-web.conf /etc/nginx/conf.d/default.conf
RUN mkdir -p /usr/share/nginx/html/stock
COPY --from=build /workspace/FrontEnd/apps/member-stock/dist/ /usr/share/nginx/html/stock/
