# syntax=docker/dockerfile:1
# codepill-web — static SPA image. Environment is injected at container start
# (docker/40-codepill-runtime-env.sh -> /config.js), never baked at build time,
# so the same image is promotable across environments (12-factor).

FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm config set store-dir /root/.local/share/pnpm/store && \
    pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# Unprivileged: master runs as uid 101, listens on 8080 — satisfies a
# runAsNonRoot SecurityContext without extra ceremony.
FROM nginxinc/nginx-unprivileged:1.29-alpine

# CSP source lists expanded by the entrypoint's envsubst; deploys append the
# IdP origin (connect-src + frame-src) and OTLP collector origin (connect-src).
ENV CODEPILL_CSP_CONNECT_SRC="'self'" \
    CODEPILL_CSP_FRAME_SRC="'self'"

COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=0755 docker/40-codepill-runtime-env.sh /docker-entrypoint.d/40-codepill-runtime-env.sh
# nginx-owned so the entrypoint can write /config.js at startup
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html

EXPOSE 8080
