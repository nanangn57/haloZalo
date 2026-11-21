# haloZalo

Ứng dụng chat 1-1 và chat nhóm, có web và mobile. Cùng một tài khoản trên hai thiết bị thấy cùng hội thoại, cùng thứ tự tin và cùng trạng thái đã đọc.

## Giới thiệu

Người dùng đăng ký, tạo nhóm, nhắn tin và xem thống kê hoạt động. Tin gồm chữ, emotion, ảnh, tài liệu và video. Tin mới tới qua WebSocket, không cần tải lại trang.

Một process monolith (`services/app`), một database PostgreSQL `halozalo` (ba schema), mỗi module một schema. Muốn dữ liệu của module khác thì gọi interface trong process hoặc nhận event. Client chỉ gọi app qua HTTP/WebSocket.

| Môi trường | App | Database | Media | Mail xác minh |
|---|---|---|---|---|
| Local | Compose / Maven `:8080` | Postgres trong Compose (`--profile local-db`) | Disk (`local`) | Log (`IDENTITY_MAIL_MODE=log`) |
| Cloud | EC2 (`app` + `nginx`) | **Amazon RDS** (`DB_HOST`) | **Amazon S3** | **SMTP Gmail** (`IDENTITY_MAIL_MODE=smtp`) |

## Tính năng

- Đăng ký, đăng nhập, hồ sơ cơ bản, friend request
- Nhóm: tạo, danh sách, thêm/xoá thành viên, role OWNER / ADMIN / MEMBER
- Chat 1-1 và chat nhóm (typing, presence, đã đọc)
- Năm loại tin: text, emotion, image, document, video
- Đồng bộ web với mobile qua WebSocket + catch-up `seq`
- Push khi offline qua Expo Push (đăng ký device token)
- Ghi hoạt động và trang thống kê
- Media qua `/media/{id}` (local disk hoặc S3)

Phạm vi đầy đủ nằm ở [doc/requirement.md](doc/requirement.md).

## Kiến trúc

| Thành phần | Vai trò | Lưu trữ |
|---|---|---|
| app | Cửa HTTP public, filter session, WebSocket (`:8080`) | — |
| identity | Tài khoản, session, nhóm, thành viên | Schema `identity` |
| analytics | Ghi event, đọc bảng tổng hợp | Schema `analytics` |
| messaging | Hội thoại, tin, `seq`, file, fan-out, push | Schema `messaging` |
| client | Web và mobile dùng chung `clients/core` | Cache trên thiết bị |

Ba schema nằm trên cùng database `halozalo`. Identity cấp session id và giữ bảng session. Filter trong app gọi Identity trong process. Tin được ghi xong rồi mới phát ra WebSocket.

Quyết định đã chốt: [doc/contract/decisions.md](doc/contract/decisions.md).  
Hợp đồng HTTP: [doc/contract/openapi.yaml](doc/contract/openapi.yaml).  
Sự kiện: [doc/contract/events.md](doc/contract/events.md).  
Lịch 8 tuần: [doc/timeline.md](doc/timeline.md).

## Cấu trúc thư mục

| Thư mục | Việc | Người giữ |
|---|---|---|
| `doc/contract/` | OpenAPI, envelope event, quyết định kiến trúc | Ngọc Anh |
| `services/app/` | Monolith Spring Boot (cửa public) | Ngọc Anh |
| `services/identity/` | Tài khoản, token, nhóm, thành viên | Ngọc Anh |
| `services/analytics/` | Ghi event, đọc bảng tổng hợp | Ngọc Anh |
| `services/messaging/` | Hội thoại, tin, `seq`, file, fan-out, media | Huy |
| `clients/core/` | SDK, WebSocket, reconnect, catch-up, outbox | Khanh |
| `clients/web/` | Màn hình web, gồm dashboard | Khanh |
| `clients/mobile/` | App Expo dùng lại `clients/core`, push | Thành |
| `infra/` | Compose local, E2E, CI, deploy EC2 + RDS + S3 | Thành |

## Chạy local

Cần Docker, JDK 21+, Node 20+.

### 1. Database + monolith

```bash
cd infra
cp -n .env.local.example .env
./scripts/local-up.sh          # Postgres :5432 (profile local-db) + app :8080
```

Nếu chạy app bằng Maven (Postgres vẫn từ Compose ở trên):

```bash
cd services/app
./mvnw spring-boot:run
```

Health: `http://localhost:8080/actuator/health`  
API base: `http://localhost:8080`  
WebSocket: `ws://localhost:8080/ws`

### 2. Web (Vite)

```bash
cd clients/web/ui
npm install
npm run dev
```

Mở `http://localhost:5173`. Mặc định gọi API/WS ở `:8080` (xem `clients/core/connectionConfig.ts`).

### 3. Mobile (Expo web / device)

```bash
cd clients/mobile/app
npm install
npx expo start --clear
```

Mở Expo web `http://localhost:8081`, hoặc quét QR trên thiết bị. Trên thiết bị thật, đặt `EXPO_PUBLIC_API_*` / `EXPO_PUBLIC_WS_BASE_URL` trỏ IP máy host (không dùng `localhost`).

### 4. E2E (tuỳ chọn)

```bash
cd infra/e2e
python3 run_all.py
```

Chi tiết: [infra/e2e/README.md](infra/e2e/README.md).

### Biến môi trường client (tuỳ chọn)

| Biến | Mặc định |
|---|---|
| `VITE_API_IDENTITY_BASE_URL` / `EXPO_PUBLIC_API_IDENTITY_BASE_URL` | `http://localhost:8080` |
| `VITE_API_MESSAGE_BASE_URL` / `EXPO_PUBLIC_API_MESSAGE_BASE_URL` | `http://localhost:8080` |
| `VITE_WS_BASE_URL` / `EXPO_PUBLIC_WS_BASE_URL` | `ws://localhost:8080` |

### Dừng local

```bash
cd infra
./scripts/local-up.sh down
```

## Cloud / AWS

| Thành phần | Dịch vụ |
|---|---|
| App + nginx | EC2 (Docker Compose) |
| Database | Amazon RDS for PostgreSQL (`DB_HOST` = endpoint RDS) |
| Media | Amazon S3 (`MESSAGING_MEDIA_BACKEND=s3`) |
| Mail | SMTP Gmail (`IDENTITY_MAIL_MODE=smtp`, `SPRING_MAIL_*` app password trong `.env`) |
| TLS | Let's Encrypt (certbot) |

- Domain: `https://halozalo.nltuanthanh.io.vn`
- Health: `https://halozalo.nltuanthanh.io.vn/actuator/health`
- Deploy: `infra/scripts/deploy-ec2.sh` — khi `DB_HOST` trỏ RDS thì chỉ `up app nginx`, không chạy container Postgres
- Env mẫu: [infra/.env.example](infra/.env.example) — `DOMAIN`, `DB_HOST`, `DB_*`, SMTP (`IDENTITY_MAIL_*`, `SPRING_MAIL_*`), S3, ba key `VITE_*`
- Chi tiết RDS / SMTP / SG: [infra/README.md](infra/README.md)
- Mobile cloud: `EXPO_PUBLIC_API_*` / `EXPO_PUBLIC_WS_BASE_URL` trỏ `https://$DOMAIN/api/...` và `wss://$DOMAIN/api/messaging`

## Hợp đồng

Đổi API hoặc event thì sửa `doc/contract/` trước, rồi mới đổi code và client.

## Nhóm

| Thành viên | Phần |
|---|---|
| Nguyễn Ngọc Anh | Hợp đồng, app, identity, analytics |
| La Quang Huy | Messaging và media |
| Nguyễn Đan Thục Khanh | client-core và web |
| Nguyễn Lê Tuấn Thành | Mobile và phát hành |
