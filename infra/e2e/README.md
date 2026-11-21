# haloZalo E2E

Bộ kiểm thử end-to-end (HTTP + WebSocket) chạy thật trên monolith local (`:8080`), không mock.

| Suite | File | Phạm vi |
|---|---|---|
| `auth_profile` | `agents/auth_profile.py` | Login, 401, PATCH `/me`, lookup |
| `friends` | `agents/friends.py` | Request / accept / list |
| `chat_sync` | `agents/chat_sync.py` | 1 user × 2 socket (web + mobile), mark-read, typing/presence |
| `media` | `agents/media.py` | Upload IMAGE/DOCUMENT, fan-out, GET `/media`, history |
| `groups` | `agents/groups.py` | Tạo nhóm, chat nhóm, leave |
| `dashboard_devices` | `agents/dashboard_devices.py` | Stats/dashboard/activity + device token |

## Chạy

Stack phải đang lên:

```bash
cd infra && docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
```

Toàn bộ:

```bash
cd infra/e2e
python3 run_all.py
```

Một suite:

```bash
python3 agents/chat_sync.py
python3 agents/media.py
```

Biến môi trường (tuỳ chọn):

| Biến | Mặc định |
|---|---|
| `HALOZALO_BASE_URL` | `http://127.0.0.1:8080` |
| `HALOZALO_WS_URL` | suy ra từ BASE |
| `HALOZALO_USER_A` | `ngoc_anh` |
| `HALOZALO_USER_B` | `huy` |
| `HALOZALO_PASSWORD` | `password1` |

Cần `websockets` cho suite realtime/media:

```bash
pip3 install websockets
```

Cloud smoke (auth, chat, media, latency): `python3 cloud_quality_suite.py` với `HALOZALO_BASE_URL` trỏ domain public.
