# infra

Một process app và một PostgreSQL. Một lệnh deploy, CI, URL cloud mỗi tuần.

Người giữ: P4 Thành.

## Local stack (một Postgres chung)

Identity và messaging phải cùng database `halozalo` trên `:5432`. Không chạy `services/*/docker-compose` hay `messaging/local-deps.sh` song song (tránh Postgres tách `:5432` / `:5433`).

```bash
cd infra
cp .env.local.example .env   # lần đầu
chmod +x scripts/local-up.sh
./scripts/local-up.sh        # hoặc: ./scripts/local-up.sh reset
```

| Service   | Host port | JDBC / notes                                      |
|-----------|-----------|---------------------------------------------------|
| Postgres  | 5432      | DB `halozalo`, user/pass `halozalo`               |
| Identity  | 8080      | schema `identity` (+ analytics tables)            |
| Messaging | 8082      | schema `messaging` (Flyway); session auth (Bearer)|
| Edge      | 8088      | một URL: `/api/identity/*`, `/api/messaging/*`    |

Tắt stack: `./scripts/local-up.sh down`.

**Client một URL (Part 5):** dùng `clients/web/.env.unified.example` → `.env.dev`, và mobile:

```env
EXPO_PUBLIC_API_IDENTITY_BASE_URL=http://localhost:8088/api/identity
EXPO_PUBLIC_API_MESSAGE_BASE_URL=http://localhost:8088/api/messaging
EXPO_PUBLIC_WS_BASE_URL=ws://localhost:8088/api/messaging
EXPO_PUBLIC_MESSAGE_HTTP_AUTH_MODE=TOKEN
EXPO_PUBLIC_WS_AUTH_MODE=TOKEN_QUERY
```

Trong lúc debug tách cổng, vẫn gọi thẳng `:8080` / `:8082` với Bearer (messaging `MESSAGING_SESSION_AUTH_ENABLED=true`).

## PostgreSQL / deploy cloud

Đứng trong thư mục này (cần `infra/.env` với `DOMAIN`, `DB_*`, `VITE_API_BASE_URL`):

```
docker compose up -d
```

Lần đầu container tạo database `halozalo`, schema `identity`, `messaging`, `analytics`, bảng tài khoản và session, rồi hai tài khoản mẫu. Volume còn dữ liệu thì script không chạy lại. Tạo lại từ đầu:

```
docker compose down -v
docker compose up -d
```

Nối vào cổng `5432`, database `halozalo`, user `halozalo`, password `halozalo`, schema `identity`. Khớp `services/identity/resources/application.yml`.

| username | email | password | session id (access token) |
|---|---|---|---|
| ngoc_anh | anh@example.com | password1 | `22222222-2222-4222-8222-222222222222` |
| huy | huy@example.com | password1 | `44444444-4444-4444-8444-444444444444` |

Session hết hạn 3 ngày sau lúc tạo container. Messaging tạo bảng qua Flyway khi service start.
