# mobile

Expo app (React Native) dùng lại `clients/core`.

## Local stack (backend chung)

Một Postgres + identity + messaging (không tách `:5432` / `:5433`):

```bash
cd infra
cp -n .env.local.example .env
./scripts/local-up.sh
```

Chi tiết: `infra/README.md` mục **Local stack**.

## Chạy trên máy (để agent / browser test)

Backend phải đang chạy (sau `infra/scripts/local-up.sh`):

- Identity `http://localhost:8080`
- Messaging `http://localhost:8082`
- Postgres chung `localhost:5432` / DB `halozalo`

```bash
cd clients/mobile/app
cp -n .env.example .env
npm install
npx expo start --web --port 8081
```

Mở `http://localhost:8081`.

## Chạy trên điện thoại (Expo Go)

1. Cài **Expo Go** trên iOS/Android.
2. Máy Mac và điện thoại cùng Wi‑Fi.
3. Lấy IP LAN của Mac, ví dụ `192.168.1.9`:

```bash
ipconfig getifaddr en0
```

4. Sửa `clients/mobile/app/.env` (không dùng `localhost` trên điện thoại):

```env
EXPO_PUBLIC_API_IDENTITY_BASE_URL=http://192.168.1.9:8080
EXPO_PUBLIC_API_MESSAGE_BASE_URL=http://192.168.1.9:8082
EXPO_PUBLIC_WS_BASE_URL=ws://192.168.1.9:8082
EXPO_PUBLIC_MESSAGE_HTTP_AUTH_MODE=USER_ID
EXPO_PUBLIC_WS_AUTH_MODE=USER_ID_QUERY
```

5. Identity CORS đã cho phép `http://localhost:*`. Với Expo Go native thì không bị CORS; nếu dùng tunnel web thì thêm origin nếu cần.
6. Khởi động:

```bash
cd clients/mobile/app
npx expo start
```

7. Quét QR bằng Expo Go (Android) hoặc Camera (iOS).

## Tài khoản test sẵn

Sau khi verify email (link trong `docker logs` của identity):

- `flow_a@example.com` / `password1`
- `flow_b@example.com` / `password1`

## Ghi chú

- Login/Register/Chat/Dashboard/Groups/Contacts gọi API thật qua `@core`.
- Với local stack chung DB, dashboard `messagesCreated` / groups tăng trên cùng analytics.
