# infra

Monolith `app` + PostgreSQL + nginx edge. Local: Postgres Compose + mail log. Cloud: Amazon RDS + EC2 + S3 + SMTP Gmail. Một lệnh deploy cho mỗi môi trường.

## Local stack

Identity / messaging / analytics chạy trong một process `app` trên `:8080`, database `halozalo`. Local dùng Postgres trong Compose (`--profile local-db`). Không chạy `services/*/docker-compose` song song.

```bash
cd infra
cp .env.local.example .env   # lần đầu
chmod +x scripts/local-up.sh
./scripts/local-up.sh        # hoặc: ./scripts/local-up.sh reset
```

| Service  | Host port | Notes |
|----------|-----------|-------|
| Postgres | 5432      | Compose profile `local-db`, DB `halozalo` |
| app      | 8080      | Monolith: identity + messaging + analytics |
| Edge     | 8088      | Tuỳ chọn (`--profile edge`): `/api/identity/*`, `/api/messaging/*` |

Tắt: `./scripts/local-up.sh down`.

**Client (local):**

```env
VITE_API_IDENTITY_BASE_URL=http://localhost:8080
VITE_API_MESSAGE_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8080
```

Mobile (`EXPO_PUBLIC_*`) tương tự. Media local: `MESSAGING_MEDIA_BACKEND=local`.

## Cloud (EC2 + RDS + S3 + HTTPS)

Trên AWS: EC2 chạy `app` + `nginx`; database **Amazon RDS for PostgreSQL**; media S3; mail **SMTP Gmail**; HTTPS Let's Encrypt.

### Yêu cầu trên máy chủ

- Docker Compose v2
- `infra/.env` từ `.env.example` (password mạnh, `ACME_EMAIL`, S3, **`DB_HOST` = endpoint RDS**)
- RDS cùng VPC với EC2; security group RDS cho phép EC2 vào cổng `5432`
- Instance role có quyền SSM + S3 media (Put/Get). Mail qua SMTP Gmail (app password trong `infra/.env`).
- SG EC2 mở 80/443; DNS domain trỏ về IP public

### Biến database (cloud)

| Biến | Cloud | Local |
|---|---|---|
| `DB_HOST` | Endpoint RDS (vd. `xxx.ap-southeast-1.rds.amazonaws.com`) | `postgres` |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Khớp RDS | Khớp Compose Postgres |

Khi `DB_HOST` không phải `postgres` / `localhost`, `deploy-ec2.sh` chỉ `up app nginx` và không chạy container Postgres.

### Mail (SMTP / Gmail)

| Biến | Cloud | Local |
|---|---|---|
| `IDENTITY_MAIL_MODE` | `smtp` | `log` (link verify in ra log container/app) |
| `IDENTITY_MAIL_FROM` | Địa chỉ Gmail gửi đi | `noreply@halozalo.local` |
| `SPRING_MAIL_HOST` | `smtp.gmail.com` | (không dùng khi `log`) |
| `SPRING_MAIL_PORT` | `587` | |
| `SPRING_MAIL_USERNAME` | Cùng Gmail | |
| `SPRING_MAIL_PASSWORD` | Gmail **app password** (không commit) | |

Cloud gửi qua SMTP Gmail (`SmtpMailSender`).

### Deploy

```bash
cd infra
cp .env.example .env   # lần đầu — sửa DOMAIN, ACME_EMAIL, DB_HOST, DB_*, S3
chmod +x scripts/deploy-ec2.sh scripts/renew-cert.sh
ALLOW_DIRTY=1 ./scripts/deploy-ec2.sh   # ALLOW_DIRTY=1 nếu tree không clean
```

Script sẽ:

1. Bake nginx config HTTP hoặc HTTPS theo cert hiện có
2. `compose up -d --build app nginx` (RDS) — hoặc thêm `postgres` nếu `DB_HOST=postgres`
3. Xin/renew Let's Encrypt nếu cần, rồi reload nginx

**Chuyển dữ liệu từ Postgres Docker cũ sang RDS** (một lần, nếu còn volume local trên EC2):

```bash
DB_HOST_RDS=<rds-endpoint> ./scripts/migrate-to-rds.sh
ALLOW_DIRTY=1 ./scripts/deploy-ec2.sh
docker compose --profile local-db stop postgres
```

Public URL (ví dụ):

| Endpoint | Ví dụ |
|----------|--------|
| Health | `https://$DOMAIN/actuator/health` |
| Web | `https://$DOMAIN/` |
| Identity API | `https://$DOMAIN/api/identity/...` |
| Messaging API / media | `https://$DOMAIN/api/messaging/...` |
| WebSocket | `wss://$DOMAIN/api/messaging/ws` |

Vite bake-time keys (khớp `clients/core/connectionConfig.ts`):

- `VITE_API_IDENTITY_BASE_URL`
- `VITE_API_MESSAGE_BASE_URL`
- `VITE_WS_BASE_URL`

Media cloud: `MESSAGING_MEDIA_BACKEND=s3` — upload `POST /media/uploads`; object trên S3; `GET /media/{id}` stream từ S3.

Renew cert định kỳ: `./scripts/renew-cert.sh` (cron).

### CD (GitHub Actions)

- **Auto:** mỗi push/merge vào `dev` chạy workflow **Deploy cloud**.
- **Manual:** Actions → Deploy cloud → Run workflow (chọn `ref` tùy ý).

Workflow [`.github/workflows/deploy-cloud.yml`](../.github/workflows/deploy-cloud.yml) gọi AWS SSM `SendCommand` trên EC2, `git pull` rồi chạy `infra/scripts/deploy-ec2.sh`. Secrets app (`infra/.env`) **không** đưa lên GitHub — vẫn chỉ nằm trên host.

**Prerequisite trên EC2**

- SSM Agent + instance profile có `AmazonSSMManagedInstanceCore` (và S3 media như hiện tại)
- Repo đã clone (path khớp biến `HALOZALO_REPO_PATH`)
- `infra/.env` đã điền (RDS, S3, SMTP, `DOMAIN`, …)
- Docker Compose v2

**GitHub secrets / variables** (Settings → Secrets and variables → Actions)

| Name | Loại | Ví dụ / ghi chú |
|---|---|---|
| `AWS_ACCESS_KEY_ID` | secret | IAM user chỉ cần `ssm:SendCommand`, `ssm:GetCommandInvocation` |
| `AWS_SECRET_ACCESS_KEY` | secret | |
| `AWS_REGION` | variable | `ap-southeast-1` |
| `EC2_INSTANCE_ID` | variable | `i-…` |
| `HALOZALO_REPO_PATH` | variable | `/home/ssm-user/haloZalo` |
| `DEPLOY_HEALTH_URL` | variable | `https://halozalo.nltuanthanh.io.vn/actuator/health` |

Inputs khi bấm Run workflow: `ref` (mặc định `dev`), `allow_dirty` (mặc định `true` — cần khi `.env`/nginx bake làm tree dirty trên EC2).

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

### Tài khoản mẫu (DB mới / seed)

| username | email | password |
|---|---|---|
| ngoc_anh | anh@example.com | password1 |
| huy | huy@example.com | password1 |

## Ghi chú

- Client chỉ đi qua nginx; không expose cổng app ra ngoài.
- Đổi hợp đồng API/event: sửa `doc/contract/` trước.
- Chi tiết E2E: [e2e/README.md](e2e/README.md).
