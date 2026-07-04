FROM node:22-alpine AS build

WORKDIR /app

RUN corepack enable

COPY FrontEnd/apps/admin/package.json FrontEnd/apps/admin/pnpm-lock.yaml ./
RUN if [ -f pnpm-lock.yaml ]; then pnpm install --frozen-lockfile; else pnpm install; fi

COPY FrontEnd/apps/admin .

ARG VITE_MODE=prod
RUN pnpm exec tsc -b && pnpm exec vite build --mode ${VITE_MODE}

FROM nginx:1.27-alpine

COPY infra/nginx/admin-web.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
