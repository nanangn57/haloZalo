# Mobile

App Expo (React Native) dùng lại `clients/core` cho HTTP, WebSocket và sync.

## Backend

Chạy monolith local trước (Compose Postgres + app `:8080`):

```bash
cd infra
./scripts/local-up.sh
```

Chi tiết: [infra/README.md](../../infra/README.md).

## Chạy trên máy (Expo web)

```bash
cd clients/mobile/app
cp -n .env.example .env
npm install
npx expo start --web --port 8081
```

Mở `http://localhost:8081`.

## Chạy trên điện thoại (Expo Go)

1. Cài Expo Go trên iOS/Android.
2. Máy và điện thoại cùng Wi‑Fi.
3. Lấy IP LAN của máy host, ví dụ `192.168.1.9`:

```bash
ipconfig getifaddr en0
```

4. Sửa `clients/mobile/app/.env` (không dùng `localhost` trên điện thoại):

```env
EXPO_PUBLIC_API_IDENTITY_BASE_URL=http://192.168.1.9:8080
EXPO_PUBLIC_API_MESSAGE_BASE_URL=http://192.168.1.9:8080
EXPO_PUBLIC_WS_BASE_URL=ws://192.168.1.9:8080
```

5. Khởi động và quét QR:

```bash
cd clients/mobile/app
npx expo start
```

Cloud: đặt `EXPO_PUBLIC_*` trỏ `https://$DOMAIN/api/...` và `wss://$DOMAIN/api/messaging`.

## Tài khoản mẫu

- `anh@example.com` / `password1`
- `huy@example.com` / `password1`
