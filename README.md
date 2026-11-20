# haloZalo

Ứng dụng chat 1-1 và chat nhóm, có web và mobile. Cùng một tài khoản trên hai thiết bị thấy cùng hội thoại, cùng thứ tự tin và cùng trạng thái đã đọc.

## Giới thiệu

Người dùng đăng ký, tạo nhóm, nhắn tin và xem thống kê hoạt động. Tin gồm chữ, emotion, ảnh, tài liệu và video. Tin mới tới qua WebSocket, không cần tải lại trang.

Một process monolith (`services/app`), một PostgreSQL, mỗi module một schema. Muốn dữ liệu của module khác thì gọi interface trong process hoặc nhận event. Client chỉ gọi app qua HTTP/WebSocket.

## Tính năng

- Đăng ký, đăng nhập, hồ sơ cơ bản, friend request
- Nhóm: tạo, danh sách, thêm/xoá thành viên, role OWNER / ADMIN / MEMBER
- Chat 1-1 và chat nhóm (typing, presence, đã đọc)
- Năm loại tin: text, emotion, image, document, video
- Đồng bộ web với mobile qua WebSocket + catch-up `seq`
- Push device token (Expo / demo local); FCM thật cần thiết bị + project Expo
- Ghi hoạt động và trang thống kê
- Media local qua `/media/{id}` (S3/cloud để khi deploy)

Phạm vi đầy đủ nằm ở [doc/requirement.md](doc/requirement.md).

## Kiến trúc

| Thành phần | Vai trò | Lưu trữ |
|---|---|---|
| app | Cửa HTTP public, filter session, WebSocket (`:8080`) | — |
| identity | Tài khoản, session, nhóm, thành viên | Schema `identity` |
| analytics | Ghi event, đọc bảng tổng hợp | Schema `analytics` |
| messaging | Hội thoại, tin, `seq`, file, fan-out, push | Schema `messaging` |
| client | Web và mobile dùng chung `clients/core` | Cache trên thiết bị |

Ba schema nằm trên cùng database PostgreSQL `halozalo`. Identity cấp session id và giữ bảng session. Filter trong app gọi Identity trong process. Tin được ghi xong rồi mới phát ra WebSocket.

Quyết định đã chốt: [doc/contract/decisions.md](doc/contract/decisions.md).  
Hợp đồng HTTP: [doc/contract/openapi.yaml](doc/contract/openapi.yaml).  
Sự kiện: [doc/contract/events.md](doc/contract/events.md).  
Lịch 8 tuần: [doc/timeline.md](doc/timeline.md).

## Cấu trúc thư mục

| Thư mục | Việc | Người |
|---|---|---|
| `doc/contract/` | OpenAPI, envelope event, quyết định kiến trúc | P1 Ngọc Anh |
| `services/app/` | Monolith Spring Boot (cửa public) | P1 Ngọc Anh |
| `services/identity/` | Tài khoản, token, nhóm, thành viên | P1 Ngọc Anh |
| `services/analytics/` | Ghi event, đọc bảng tổng hợp | P1 Ngọc Anh |
| `services/messaging/` | Hội thoại, tin, `seq`, file, fan-out, media | P2 Huy |
| `clients/core/` | SDK, WebSocket, reconnect, catch-up, outbox | P3 Khanh |
| `clients/web/` | Màn hình web, gồm dashboard | P3 Khanh |
| `clients/mobile/` | App Expo dùng lại `clients/core`, push | P4 Thành |
| `infra/` | Compose local, E2E, CI, deploy cloud | P4 Thành |

## Chạy local

Cần Docker (Postgres), JDK 21+, Node 20+.

### 1. Database + monolith

```bash
cd infra
./scripts/local-up.sh          # Postgres :5432 + build/run app :8080
# hoặc chỉ Postgres rồi chạy app tay:
# docker compose -f docker-compose.yml -f docker-compose.local.yml up -d postgres
```

Nếu chạy app bằng Maven (không qua Compose app container):

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

### 4. E2E API (tuỳ chọn)

```bash
cd infra/e2e
npm install
npm test
```

Chi tiết UI: [infra/e2e/UI_TEST.md](infra/e2e/UI_TEST.md).

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

Deploy tuần, S3 signed URL, URL public — nằm ở `infra/` khi sẵn sàng. Phần này **không bắt buộc** để phát triển và demo local.

## Đóng góp

Sửa trong thư mục mình giữ. Một pull request không trộn thư mục của hai người.

Đổi API hoặc event thì P1 sửa `doc/contract/` trước. Người còn lại sinh type và SDK từ file đó, không chép schema bằng tay.

Mẫu mô tả task: [doc/taskTemplate.md](doc/taskTemplate.md).

## Nhóm

| Người | Phần |
|---|---|
| P1 Ngọc Anh | Hợp đồng, app, identity, analytics |
| P2 Huy | Messaging và media |
| P3 Khanh | client-core và web |
| P4 Thành | Mobile và phát hành |
