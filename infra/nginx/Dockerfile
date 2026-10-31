# Build React/Vite into static files, then serve those files and proxy APIs with ONE Nginx container.
FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY web/ui/package.json web/ui/package-lock.json ./
RUN npm ci
COPY web/ui/ ./
COPY core/ ./core/
RUN npm run build

FROM nginx:stable-alpine
COPY --from=frontend-build /app/dist/ /usr/share/nginx/html/
# The active server configuration is mounted from infra/nginx/current/default.conf.
EXPOSE 80 443
