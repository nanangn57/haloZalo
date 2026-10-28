FROM node:22-alpine AS frontend-build

WORKDIR /app

# COPY web/ui/package.json web/ui/package-lock.json ./
# RUN npm ci

COPY web/ ./web/
COPY core/ ./core/
WORKDIR /app/web/ui

RUN npm ci
RUN npm run lint
RUN npm run build