# infra

Monolith `app` + PostgreSQL + nginx edge. Một lệnh deploy local hoặc cloud.

Người giữ: P4 Thành.

## Local stack

Identity/messaging/analytics chạy trong một process `app` trên `:8080`, cùng database `halozalo` trên `:5432`. Không chạy `services/*/docker-compose` song song.

```bash
cd infra
cp .env.local.example .env   # lần đầu (nếu có); hoặc dùng .env.example
chmod +x scripts/local-up.sh
./scripts/local-up.sh        # hoặc: ./scripts/local-up.sh reset
```

| Service  | Host port | Notes |
|----------|-----------|-------|
| Postgres | 5432      | DB `halozalo` |
| app      | 8080      | Monolith: identity + messaging + analytics |
| Edge     | 8088      | Tuỳ local compose overlay: `/api/identity/*`, `/api/messaging/*` |

Tắt: `./scripts/local-up.sh down`.

**Client (local):**

```env
VITE_API_IDENTITY_BASE_URL=http://localhost:8080
VITE_API_MESSAGE_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8080
```

Mobile (`EXPO_PUBLIC_*`) tương tự. Media local: `MESSAGING_MEDIA_BACKEND=local` (mặc định trong profile local).

## Cloud (EC2 + S3 + HTTPS)

Stack mặc định trong `docker-compose.yml`: `postgres` + `app` + `nginx` (+ profile `tools` cho certbot). Profile `split` giữ identity/messaging tách chỉ khi cần debug cũ.

### Yêu cầu trên máy chủ

- Docker Compose v2
- `infra/.env` từ `.env.example` (password mạnh, `ACME_EMAIL`, S3 bucket/region)
- Instance role có quyền SSM + S3 media (Put/Get trên bucket media)
- SG mở 80/443; domain DNS trỏ về IP public

### Deploy

```bash
cd infra
cp .env.example .env   # lần đầu — sửa DOMAIN, ACME_EMAIL, DB_PASSWORD, S3
chmod +x scripts/deploy-ec2.sh scripts/renew-cert.sh
ALLOW_DIRTY=1 ./scripts/deploy-ec2.sh   # ALLOW_DIRTY=1 nếu tree không clean
```

Script sẽ:

1. Bake nginx config HTTP hoặc HTTPS theo cert hiện có
2. `compose up -d --build postgres app nginx`
3. Xin/renew Let's Encrypt nếu cần, rồi reload nginx

Public URL (ví dụ):

| Endpoint | Ví dụ |
|----------|--------|
| Health | `https://$DOMAIN/actuator/health` |
| Web | `https://$DOMAIN/` |
| Identity API | `https://$DOMAIN/api/identity/...` |
| Messaging API / media | `https://$DOMAIN/api/messaging/...` |
| WebSocket | `wss://$DOMAIN/api/messaging/ws` |

Vite bake-time keys (bắt buộc khớp `clients/core/connectionConfig.ts`):

- `VITE_API_IDENTITY_BASE_URL`
- `VITE_API_MESSAGE_BASE_URL`
- `VITE_WS_BASE_URL`

Media cloud: `MESSAGING_MEDIA_BACKEND=s3` — upload vẫn `POST /media/uploads`; object nằm S3; `GET /media/{id}` stream từ S3. Bucket giữ Block Public Access; CORS origin = domain.

Renew cert định kỳ: `./scripts/renew-cert.sh` (cron).

### E2E trỏ cloud / app

E2E gọi path monolith (`/auth/login`, `/media/...`, `/ws`). Qua nginx public cần hai prefix khác nhau — chạy trực tiếp vào container `app`:

```bash
APP_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' halozalo-app-1)
cd infra/e2e
python3 -m venv /tmp/hz-e2e-venv && /tmp/hz-e2e-venv/bin/pip install websockets
HALOZALO_BASE_URL=http://$APP_IP:8080 HALOZALO_WS_URL=ws://$APP_IP:8080 \
  /tmp/hz-e2e-venv/bin/python run_all.py
```

Hoặc smoke HTTP public: health, login `POST /api/identity/auth/login`, upload media qua `/api/messaging/media/uploads`.

`GET /stats` cần user `platformRole=ADMIN` — seed `ngoc_anh`/`huy` là USER nên E2E dashboard stats có thể FAIL dù stack ổn.

### Tài khoản mẫu (volume Postgres mới)

| username | email | password |
|---|---|---|
| ngoc_anh | anh@example.com | password1 |
| huy | huy@example.com | password1 |

## Ghi chú

- Client chỉ đi qua nginx/gateway; không expose cổng app ra ngoài.
- Đổi hợp đồng API/event: sửa `doc/contract/` trước.
- Chi tiết E2E: [e2e/README.md](e2e/README.md).
