# identity

Tài khoản và session. MySQL. Session id opaque, hết hạn sau 3 ngày, tính từ lúc cấp. Đăng nhập mới không xóa session cũ. Gateway hỏi session bằng HTTP, không đọc bảng này.

Người giữ: P1 Ngọc Anh. Service này không đọc bảng của service khác.

Cổng `8081`. Database, user và password nằm trong `resources/application.yml`.

```
services/identity/
  pom.xml
  resources/          cổng, database, và schema.sql
  src/
    app/              khởi động Spring Boot
    account/          dữ liệu tài khoản, gồm hash mật khẩu
    session/          dữ liệu session
    auth/             JSON request và response
    password/         hash và so khớp bcrypt
    storage/          interface lưu, và implementation MySQL
    event/            phát event đăng nhập
    service/          đăng ký, đăng nhập, kiểm tra session
    http/             nhận HTTP, đổi lỗi thành status
  test/               cùng các package trên, mỗi nhóm một test
```

## API

`POST /auth/register` trả 201. `POST /auth/login` trả 200. Cả hai trả `accessToken`, `tokenType`, `expiresIn`, và `user` gồm `userId`, `username`, `email`. Không trả hash mật khẩu.

`GET /me` cần `Authorization: Bearer`. Trả `userId`, `username`, `email`. `GET /internal/sessions/current` dùng cùng header và chỉ trả `userId`. Endpoint này không có trong OpenAPI public.

Lỗi: `VALIDATION_ERROR` 400, `CONFLICT` 409, `UNAUTHENTICATED` 401.

Đăng nhập thành công thì `LoggingEventPublisher` ghi event `identity.user.logged_in`, payload chỉ có `userId`. Đăng ký không phát event.

## Schema

`resources/schema.sql` tạo bảng lúc khởi động. `CREATE TABLE IF NOT EXISTS` nên lần chạy sau không xóa dữ liệu.

`accounts.user_id` và `sessions.session_id` là khóa chính. `accounts.username` và `accounts.email` là unique. `sessions.user_id` tham chiếu `accounts.user_id`. `password_hash` dài 60 ký tự. `expires_at` đến micro giây.

`AuthService` kiểm tra trùng username và email trước khi `INSERT`. Khóa unique chặn lần ghi trùng còn lọt qua. `user_id` và `session_id` là UUID do service cấp.
