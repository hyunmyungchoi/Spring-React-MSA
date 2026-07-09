FROM node:22-alpine AS build

WORKDIR /app

RUN corepack enable

COPY FrontEnd/apps/member/package.json FrontEnd/apps/member/pnpm-lock.yaml ./
RUN if [ -f pnpm-lock.yaml ]; then pnpm install --frozen-lockfile; else pnpm install; fi

COPY FrontEnd/apps/member .

ARG VITE_MODE=prod
ARG WEB_MOUNT_PATH=/
RUN pnpm exec tsc -b \
    && pnpm exec vite build --mode ${VITE_MODE} \
    && case "${VITE_MODE}" in \
        community|stock) \
          test -f "dist/${VITE_MODE}/${VITE_MODE}.html"; \
          rm -rf dist-root; \
          if [ "${WEB_MOUNT_PATH}" = "/" ]; then \
            mkdir dist-root; \
            cp -R "dist/${VITE_MODE}/." dist-root/; \
            mv "dist-root/${VITE_MODE}.html" dist-root/index.html; \
          else \
            mount_path="${WEB_MOUNT_PATH#/}"; \
            mount_path="${mount_path%/}"; \
            test -n "${mount_path}"; \
            mkdir -p "dist-root/${mount_path}"; \
            cp -R "dist/${VITE_MODE}/." "dist-root/${mount_path}/"; \
            mv "dist-root/${mount_path}/${VITE_MODE}.html" "dist-root/${mount_path}/index.html"; \
          fi; \
          rm -rf dist; \
          mv dist-root dist; \
          ;; \
        *) \
          ;; \
      esac

FROM nginx:1.27-alpine

COPY infra/nginx/web/member-web.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
