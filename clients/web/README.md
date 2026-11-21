# Web client

Ứng dụng web (React + TypeScript + Vite) dùng chung `clients/core` cho HTTP và WebSocket.

## Chạy local

```bash
cd clients/web/ui
npm install
npm run dev
```

Mở `http://localhost:5173`. API/WS mặc định trỏ monolith `:8080` (xem `clients/core/connectionConfig.ts`).

## Build

```bash
cd clients/web/ui
npm run build
```

Image production được build trong `infra/` (nginx phục vụ static + proxy API).
