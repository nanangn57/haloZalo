# Hướng dẫn test — local & cloud

**Ngày:** 2026-11-24  
**Clients:** Web Vite (`clients/web/ui`), Mobile Expo (`clients/mobile/app`) — cùng `clients/core`.  
**Backend:** monolith `services/app` (identity + messaging + analytics).

| | Local | Cloud |
|---|---|---|
| API / WS | `http://localhost:8080`, `ws://localhost:8080` | nginx: `https://halozalo.nltuanthanh.io.vn` (+ `/api/...`) |
| Web UI | Vite `:5173` | Cùng domain (SPA trên nginx) |
| Mobile | Expo Go / Expo web trên máy bạn | Expo trỏ API cloud (Metro vẫn local; tunnel nếu khác Wi‑Fi) |
| DB / media / mail | Postgres Compose, disk, mail `log` | RDS, S3, SMTP Gmail |

Health:

- Local: `http://localhost:8080/actuator/health`
- Cloud: `https://halozalo.nltuanthanh.io.vn/actuator/health` → `{"status":"UP"}`

Tài khoản mẫu (nếu đã seed): `anh@example.com` / `password1`, `huy@example.com` / `password1`. Cloud admin: xem `IDENTITY_PLATFORM_ADMINS` / seed trên RDS.

---

## 0. Chuẩn bị chung

Cần: Docker, JDK 21+, Node 20+, (mobile) Expo Go trên điện thoại.

Biến client (cùng ý nghĩa Vite / Expo):

| Biến | Việc |
|---|---|
| `VITE_API_IDENTITY_BASE_URL` / `EXPO_PUBLIC_API_IDENTITY_BASE_URL` | Base identity (+ auth qua base này) |
| `VITE_API_MESSAGE_BASE_URL` / `EXPO_PUBLIC_API_MESSAGE_BASE_URL` | Base messaging / media |
| `VITE_WS_BASE_URL` / `EXPO_PUBLIC_WS_BASE_URL` | Base WebSocket (client nối thêm `/ws`) |

Đổi `.env` mobile rồi **`npx expo start --clear`**. File `.env` không commit.

---

## 1. Test local

### 1.1 Backend

```bash
cd infra
cp -n .env.local.example .env
./scripts/local-up.sh
```

Mở health `http://localhost:8080/actuator/health`.

Dừng: `./scripts/local-up.sh down`.

### 1.2 Web (Vite)

```bash
cd clients/web/ui
npm install
npm run dev
```

Mở `http://localhost:5173` (mặc định gọi `:8080`).

Checklist nhanh:

- [ ] Đăng ký / đăng nhập / logout  
- [ ] Chat 1-1 và nhóm, tin realtime (không F5)  
- [ ] Upload ảnh / document / video trong giới hạn  
- [ ] Hai tab cùng account: gửi / đọc đồng bộ  
- [ ] Admin stats (user `platformRole=ADMIN`) nếu có  

### 1.3 Mobile — Expo web (trên máy)

```bash
cd clients/mobile/app
cp -n .env.example .env
```

`.env` local monolith (máy / Expo web):

```env
EXPO_PUBLIC_API_IDENTITY_BASE_URL=http://localhost:8080
EXPO_PUBLIC_API_MESSAGE_BASE_URL=http://localhost:8080
EXPO_PUBLIC_WS_BASE_URL=ws://localhost:8080
EXPO_PUBLIC_MESSAGE_HTTP_AUTH_MODE=TOKEN
EXPO_PUBLIC_WS_AUTH_MODE=TOKEN_QUERY
```

```bash
npm install
npx expo start --web --port 8081 --clear
```

Mở `http://localhost:8081`. Checklist giống web (auth, chat, media, multi-device với Vite nếu cần).

### 1.4 Mobile — Expo Go (điện thoại, cùng Wi‑Fi)

1. Cài Expo Go.  
2. Máy host + điện thoại cùng Wi‑Fi.  
3. Lấy IP LAN: `ipconfig getifaddr en0` (ví dụ `192.168.1.9`).  
4. `.env` — **không** dùng `localhost`:

```env
EXPO_PUBLIC_API_IDENTITY_BASE_URL=http://192.168.1.9:8080
EXPO_PUBLIC_API_MESSAGE_BASE_URL=http://192.168.1.9:8080
EXPO_PUBLIC_WS_BASE_URL=ws://192.168.1.9:8080
EXPO_PUBLIC_MESSAGE_HTTP_AUTH_MODE=TOKEN
EXPO_PUBLIC_WS_AUTH_MODE=TOKEN_QUERY
```

5. Start và quét QR:

```bash
cd clients/mobile/app
npx expo start --clear
```

Trong terminal Expo: nhấn **`c`** để hiện QR. Checklist: login, chat realtime, media từ gallery, đồng bộ với web local.

---

## 2. Test cloud

Backend đã deploy trên EC2 (merge/`push` `dev` → workflow Deploy cloud, hoặc `infra/scripts/deploy-ec2.sh`). Chi tiết: [infra/README.md](../infra/README.md).

Public (ví dụ hiện tại):

| Endpoint | URL |
|---|---|
| Health | `https://halozalo.nltuanthanh.io.vn/actuator/health` |
| Web SPA | `https://halozalo.nltuanthanh.io.vn/` |
| Identity API | `https://…/api/identity/...` |
| Messaging API | `https://…/api/messaging/...` |
| WebSocket | `wss://…/api/messaging/ws` |

Trước khi test client: health phải `UP`.

### 2.1 Web trên cloud

Mở trình duyệt: `https://halozalo.nltuanthanh.io.vn/`  
Không cần Vite local — nginx phục vụ SPA đã build lúc deploy.

Checklist: auth (mail verify qua Gmail), chat, media (S3), multi-device (web + mobile cloud).

### 2.2 Mobile — Expo trỏ API cloud

Metro vẫn chạy **trên máy bạn**; chỉ API/WS trỏ cloud.

`clients/mobile/app/.env`:

```env
EXPO_PUBLIC_API_IDENTITY_BASE_URL=https://halozalo.nltuanthanh.io.vn/api/identity
EXPO_PUBLIC_API_MESSAGE_BASE_URL=https://halozalo.nltuanthanh.io.vn/api/messaging
EXPO_PUBLIC_WS_BASE_URL=wss://halozalo.nltuanthanh.io.vn/api/messaging
EXPO_PUBLIC_MESSAGE_HTTP_AUTH_MODE=TOKEN
EXPO_PUBLIC_WS_AUTH_MODE=TOKEN_QUERY
```

**Expo web (máy bạn):**

```bash
cd clients/mobile/app
npx expo start --web --port 8081 --clear
```

**Expo Go cùng Wi‑Fi:** `npx expo start --clear` → quét QR (`c`).

**Expo Go khác mạng (tunnel):**

```bash
cd clients/mobile/app
# Cần @expo/ngrok (devDependency) nếu chưa có; hoặc npx expo login rồi --tunnel
npx expo start --tunnel ngrok --clear --port 8081
```

- Tunnel sẵn: terminal có `Tunnel ready`, URL dạng `https://….exp.direct`.  
- Mở bằng Expo Go: `exp://….exp.direct:80`.  
- Hiện QR: nhấn **`c`**, hoặc tạo QR từ URL `exp://…` (ví dụ dịch vụ QR online).  
- Laptop phải giữ Metro/tunnel chạy; tắt là hết kết nối bundle.  
- Chat/API vẫn đi cloud — tunnel chỉ đưa **JS bundle** tới máy khác mạng.

Checklist mobile cloud: đăng ký (SMTP), login, WS realtime, upload media S3, đồng bộ với web cloud.

---

## 3. Ma trận nhanh

| Mục tiêu | Backend | Client lệnh / URL |
|---|---|---|
| Web local | `local-up.sh` | `clients/web/ui` → `npm run dev` → `:5173` |
| Mobile web local | `local-up.sh` | Expo `--web` → `:8081`, `.env` → `:8080` |
| Mobile phone local | `local-up.sh` | Expo Go + LAN IP trong `.env` |
| Web cloud | EC2 đã deploy | Trình duyệt → domain |
| Mobile → cloud API | EC2 đã deploy | Expo + `.env` HTTPS/`wss` `/api/...` |
| Mobile khác Wi‑Fi | EC2 + Metro local | `expo start --tunnel ngrok` |

---

## 4. E2E (tuỳ chọn)

```bash
cd infra/e2e
python3 run_all.py
```

Trỏ cloud: xem [infra/e2e/README.md](../infra/e2e/README.md) và [infra/README.md](../infra/README.md#e2e-trỏ-cloud--app).

---

## 5. Lỗi thường gặp

| Triệu chứng | Hướng xử lý |
|---|---|
| Expo Go không gọi được API local | Đổi `localhost` → IP LAN; cùng Wi‑Fi; SG/firewall |
| Đổi `.env` mà app vẫn gọi URL cũ | `npx expo start --clear` |
| `--tunnel` đòi login | `npx expo login`, hoặc `--tunnel ngrok` + `@expo/ngrok` |
| Cloud WS offline / 401 | Base `wss://…/api/messaging`; token query; nginx giữ `$args` |
| Deploy SSM fail vì dirty file tracked trên EC2 (nginx templates, compose, …) | CD đã `git reset --hard origin/<ref>` — không sửa tay file tracked trên host; chỉ giữ `infra/.env`. Redeploy (push `dev` hoặc Actions → Deploy cloud) |
| Verify email local | Xem log container (`IDENTITY_MAIL_MODE=log`); cloud: hộp thư Gmail |

---

## Tham chiếu

- [README.md](../README.md) — chạy local / cloud overview  
- [infra/README.md](../infra/README.md) — Compose, deploy EC2, CD  
- [clients/mobile/README.md](../clients/mobile/README.md) — Expo  
- [doc/contract/decisions.md](contract/decisions.md) — hợp đồng đã chốt  
