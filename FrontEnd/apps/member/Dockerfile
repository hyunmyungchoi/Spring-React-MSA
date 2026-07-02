FROM node:22-alpine AS build

WORKDIR /app

RUN corepack enable

COPY FrontEnd/apps/member/package.json FrontEnd/apps/member/pnpm-lock.yaml ./
RUN if [ -f pnpm-lock.yaml ]; then pnpm install --frozen-lockfile; else pnpm install; fi

COPY FrontEnd/apps/member .

ARG VITE_MODE=prod
ARG VITE_MEMBER_GATEWAY_BASE_URL=
RUN if [ -n "$VITE_MEMBER_GATEWAY_BASE_URL" ]; then export VITE_MEMBER_GATEWAY_BASE_URL="$VITE_MEMBER_GATEWAY_BASE_URL"; fi; \
    pnpm exec tsc -b && pnpm exec vite build --mode ${VITE_MODE}

FROM nginx:1.27-alpine

COPY infra/nginx/frontend-static.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
