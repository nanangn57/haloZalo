FROM node:22-alpine AS frontend-build

WORKDIR /app

COPY web/ui/package.json web/ui/package-lock.json ./
RUN npm ci

COPY web/ui/ ./
COPY core/ ./core/

RUN npm run build