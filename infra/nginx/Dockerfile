# Build React/Vite into static files, then serve those files and proxy APIs with ONE Nginx container.
FROM node:22-alpine AS frontend-build
WORKDIR /app

COPY clients/web/ ./web/
COPY clients/core/ ./core/

WORKDIR /app/web/ui

ARG VITE_API_IDENTITY_BASE_URL
ARG VITE_API_MESSAGE_BASE_URL
ARG VITE_WS_BASE_URL
ENV VITE_API_IDENTITY_BASE_URL=$VITE_API_IDENTITY_BASE_URL
ENV VITE_API_MESSAGE_BASE_URL=$VITE_API_MESSAGE_BASE_URL
ENV VITE_WS_BASE_URL=$VITE_WS_BASE_URL

RUN npm ci
# Lint runs in CI / local; image build only needs a production bundle.
RUN npm run build

FROM nginx:stable-alpine
COPY --from=frontend-build /app/web/ui/dist/ /usr/share/nginx/html/
# The active server configuration is mounted from infra/nginx/current/default.conf.
EXPOSE 80 443
