# infra

Một process app và một PostgreSQL. Một lệnh deploy, CI, URL cloud mỗi tuần.

Người giữ: P4 Thành.

## PostgreSQL thử trên cloud

Đứng trong thư mục này:

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

Session hết hạn 3 ngày sau lúc tạo container. `messaging` và `analytics` chưa có bảng.
